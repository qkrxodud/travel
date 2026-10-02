package com.kobi.territory.progression.infra;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.progression.domain.ExploredRegion;
import com.kobi.territory.progression.domain.ExploredRegions;
import com.kobi.territory.progression.domain.ExplorerProgress;
import com.kobi.territory.progression.domain.ExplorerProgressRepository;
import com.kobi.territory.progression.domain.XpLedgerEntry;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/**
 * ExplorerProgress 저장소 어댑터 — explorer_progress(루트)·xp_ledger·explorer_region·badge_earned·title_earned.
 * 행 ↔ 도메인 변환은 각 엔티티(from·apply·toDomain)가 하고, 여기는 조회·저장 호출과 엔티티 조합만 한다.
 * <ul>
 *   <li>평소(이벤트 처리·칭호 선택): 새로 쌓인 장부·뱃지·칭호를 추가하고 바뀐 지역만 쓴다 — 재조회 없이 짧은 트랜잭션.</li>
 *   <li>재계산 사본({@link ExplorerProgress#rebuilt()}): 애그리거트 상태와 통째로 동기화(지운 장부·지역 행 삭제).</li>
 * </ul>
 * 같은 트랜잭션에서 find 로 읽은 루트 행이 영속 컨텍스트에 있으므로 version 낙관적 락이 동시 갱신을 막는다.
 */
@Repository
class JpaExplorerProgressRepository implements ExplorerProgressRepository {

    private final ExplorerProgressJpaRepository progressRows;
    private final XpLedgerJpaRepository ledgerRows;
    private final BadgeEarnedJpaRepository badgeRows;
    private final TitleEarnedJpaRepository titleRows;
    private final ExplorerRegionJpaRepository regionRows;
    private final Clock clock;

    JpaExplorerProgressRepository(ExplorerProgressJpaRepository progressRows, XpLedgerJpaRepository ledgerRows,
                                  BadgeEarnedJpaRepository badgeRows, TitleEarnedJpaRepository titleRows,
                                  ExplorerRegionJpaRepository regionRows, Clock clock) {
        this.progressRows = progressRows;
        this.ledgerRows = ledgerRows;
        this.badgeRows = badgeRows;
        this.titleRows = titleRows;
        this.regionRows = regionRows;
        this.clock = clock;
    }

    @Override
    public Optional<ExplorerProgress> find(ExplorerId explorerId) {
        String id = explorerId.value();
        return progressRows.findById(id).map(root -> root.toDomain(ledgerRows.findByExplorerIdOrderByIdAsc(id),
            regionRows.findByExplorerId(id), badgeRows.findByExplorerId(id), titleRows.findByExplorerId(id)));
    }

    @Override
    public ExploredRegions exploredRegions(ExplorerId explorerId) {
        return ExplorerRegionJpaEntity.toDomain(regionRows.findByExplorerId(explorerId.value()));
    }

    @Override
    public void save(ExplorerProgress progress) {
        ExplorerProgressJpaEntity root = progressRows.findById(progress.explorerId().value())
            .orElseGet(() -> ExplorerProgressJpaEntity.from(progress, clock.instant()));
        root.apply(progress, clock.instant());
        progressRows.save(root);
        if (progress.rebuilt()) {
            syncAll(progress);
        } else {
            appendChanges(progress);
        }
    }

    /** 평소 경로: 추가·변경분만. */
    private void appendChanges(ExplorerProgress progress) {
        ExplorerId explorer = progress.explorerId();
        progress.ledger().unsaved().forEach(entry -> ledgerRows.save(XpLedgerJpaEntity.from(explorer, entry)));
        progress.unsavedBadges().forEach(badge ->
            badgeRows.save(BadgeEarnedJpaEntity.from(explorer, badge, progress.badges().get(badge))));
        progress.unsavedTitles().forEach(title ->
            titleRows.save(TitleEarnedJpaEntity.from(explorer, title, progress.titles().get(title))));
        progress.regions().changed().forEach(region -> saveRegion(explorer, region));
    }

    private void saveRegion(ExplorerId explorer, ExploredRegion region) {
        regionRows.findById(ExplorerRegionJpaEntity.keyOf(explorer, region)).ifPresentOrElse(
            regionRow -> regionRow.apply(region),
            () -> regionRows.save(ExplorerRegionJpaEntity.from(explorer, region)));
    }

    /** 재계산 경로: 애그리거트와 통째로 같게. */
    private void syncAll(ExplorerProgress progress) {
        ExplorerId explorer = progress.explorerId();
        String id = explorer.value();
        Map<String, XpLedgerJpaEntity> staleLedger = ledgerRows.findByExplorerIdOrderByIdAsc(id).stream()
            .collect(Collectors.toMap(XpLedgerJpaEntity::refId, Function.identity()));
        for (XpLedgerEntry entry : progress.ledger().entries()) {
            if (staleLedger.remove(entry.refId()) == null) ledgerRows.save(XpLedgerJpaEntity.from(explorer, entry));
        }
        ledgerRows.deleteAll(staleLedger.values());

        Set<String> savedBadges = badgeRows.findByExplorerId(id).stream().map(BadgeEarnedJpaEntity::badgeId)
            .collect(Collectors.toSet());
        progress.badges().entrySet().stream().filter(badge -> !savedBadges.contains(badge.getKey()))
            .forEach(badge -> badgeRows.save(BadgeEarnedJpaEntity.from(explorer, badge.getKey(), badge.getValue())));
        Set<String> savedTitles = titleRows.findByExplorerId(id).stream().map(TitleEarnedJpaEntity::titleId)
            .collect(Collectors.toSet());
        progress.titles().entrySet().stream().filter(title -> !savedTitles.contains(title.getKey()))
            .forEach(title -> titleRows.save(TitleEarnedJpaEntity.from(explorer, title.getKey(), title.getValue())));

        Map<String, ExplorerRegionJpaEntity> staleRegions = regionRows.findByExplorerId(id).stream()
            .collect(Collectors.toMap(ExplorerRegionJpaEntity::regionCode, Function.identity()));
        progress.regions().all().forEach(region -> {
            staleRegions.remove(region.code().value());
            saveRegion(explorer, region);
        });
        regionRows.deleteAll(staleRegions.values());
    }
}
