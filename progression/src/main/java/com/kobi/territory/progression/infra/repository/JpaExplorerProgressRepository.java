package com.kobi.territory.progression.infra.repository;

import com.kobi.territory.progression.infra.entity.BadgeEarnedJpaEntity;
import com.kobi.territory.progression.infra.entity.ExplorerProgressJpaEntity;
import com.kobi.territory.progression.infra.entity.ExplorerRegionJpaEntity;
import com.kobi.territory.progression.infra.entity.TitleEarnedJpaEntity;
import com.kobi.territory.progression.infra.entity.XpLedgerJpaEntity;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.progression.domain.progress.ExploredRegion;
import com.kobi.territory.progression.domain.progress.ExploredRegions;
import com.kobi.territory.progression.domain.progress.ExplorerProgress;
import com.kobi.territory.progression.domain.progress.ExplorerProgressRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * ExplorerProgress 저장소 어댑터 — explorer_progress(루트)·xp_ledger·explorer_region·badge_earned·title_earned.
 * "어떻게 저장할지"만 한다(무엇을 할지는 호출자가 save/replace 로 고른다). 행 ↔ 도메인 변환은 각 엔티티가 한다.
 * <ul>
 *   <li>{@link #save}: 새로 쌓인 장부·뱃지·칭호 추가, 바뀐 지역만 쓰기(재조회 없음 — 짧은 트랜잭션).</li>
 *   <li>{@link #replace}: 그 탐험가의 자식 행을 지우고 애그리거트 상태로 다시 넣는다(재계산 경로).</li>
 * </ul>
 * 루트 행은 저장마다 OPTIMISTIC_FORCE_INCREMENT 로 version 을 올린다 — 자식 행만 바뀌어도 동시 갱신이 충돌로 드러나게(QA I-2).
 */
@Repository
class JpaExplorerProgressRepository implements ExplorerProgressRepository {

    private final ExplorerProgressJpaRepository progressRows;
    private final XpLedgerJpaRepository ledgerRows;
    private final BadgeEarnedJpaRepository badgeRows;
    private final TitleEarnedJpaRepository titleRows;
    private final ExplorerRegionJpaRepository regionRows;
    private final EntityManager entityManager;

    JpaExplorerProgressRepository(ExplorerProgressJpaRepository progressRows, XpLedgerJpaRepository ledgerRows,
                                  BadgeEarnedJpaRepository badgeRows, TitleEarnedJpaRepository titleRows,
                                  ExplorerRegionJpaRepository regionRows, EntityManager entityManager) {
        this.progressRows = progressRows;
        this.ledgerRows = ledgerRows;
        this.badgeRows = badgeRows;
        this.titleRows = titleRows;
        this.regionRows = regionRows;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<ExplorerProgress> find(ExplorerId explorerId) {
        return progressRows.findById(explorerId.value()).map(this::withChildren);
    }

    @Override
    public Optional<ExplorerProgress> findLocked(ExplorerId explorerId) {
        return progressRows.lockById(explorerId.value()).map(this::withChildren);
    }

    private ExplorerProgress withChildren(ExplorerProgressJpaEntity root) {
        String id = root.explorerId();
        return root.toDomain(ledgerRows.findByExplorerIdOrderByIdAsc(id), regionRows.findByExplorerId(id),
            badgeRows.findByExplorerId(id), titleRows.findByExplorerId(id));
    }

    @Override
    public ExploredRegions exploredRegions(ExplorerId explorerId) {
        return ExplorerRegionJpaEntity.toDomain(regionRows.findByExplorerId(explorerId.value()));
    }

    @Override
    public void save(ExplorerProgress progress) {
        saveRoot(progress);
        ExplorerId explorer = progress.explorerId();
        progress.ledger().unsaved().forEach(entry -> ledgerRows.save(XpLedgerJpaEntity.from(explorer, entry)));
        progress.unsavedBadges().forEach(badge ->
            badgeRows.save(BadgeEarnedJpaEntity.from(explorer, badge, progress.badges().get(badge))));
        progress.unsavedTitles().forEach(title ->
            titleRows.save(TitleEarnedJpaEntity.from(explorer, title, progress.titles().get(title))));
        progress.regions().changed().forEach(region -> saveRegion(explorer, region));
    }

    /**
     * 통째로 바꾸기(재계산). 호출자는 {@link #findLocked}로 루트 행을 이미 잠갔다(PESSIMISTIC_WRITE — FOR UPDATE, 저장 때 version 증가) — 진행 이벤트 처리도 같은 잠금으로 시작하므로 그동안 자식 행을 바꾸는 쪽은 없다. 자식 행을 엔티티 단위로
     * 지운 뒤 flush(영속성 컨텍스트는 비우지 않는다 — QA S-1) → 장부는 처리 시각 순으로 다시 넣는다(QA S-2·S-3) → 루트 갱신.
     * 루트 행이 없던 신규 탐험가는 잠글 행이 없어 동시 생성이 PK 충돌로 끝날 수 있다 — 재계산 재시도가 흡수한다.
     */
    @Override
    public void replace(ExplorerProgress progress) {
        ExplorerId explorer = progress.explorerId();
        String id = explorer.value();
        ledgerRows.deleteAll(ledgerRows.findByExplorerIdOrderByIdAsc(id));
        regionRows.deleteAll(regionRows.findByExplorerId(id));
        badgeRows.deleteAll(badgeRows.findByExplorerId(id));
        titleRows.deleteAll(titleRows.findByExplorerId(id));
        entityManager.flush();
        progress.ledger().chronological().forEach(entry -> ledgerRows.save(XpLedgerJpaEntity.from(explorer, entry)));
        progress.regions().all().forEach(region -> regionRows.save(ExplorerRegionJpaEntity.from(explorer, region)));
        progress.badges().forEach((badge, earnedAt) -> badgeRows.save(BadgeEarnedJpaEntity.from(explorer, badge, earnedAt)));
        progress.titles().forEach((title, earnedAt) -> titleRows.save(TitleEarnedJpaEntity.from(explorer, title, earnedAt)));
        saveRoot(progress);
    }

    /** 루트 행: 있으면 갱신 + version 강제 증가, 없으면 추가. */
    private void saveRoot(ExplorerProgress progress) {
        progressRows.findById(progress.explorerId().value()).ifPresentOrElse(root -> {
            root.apply(progress);
            entityManager.lock(root, forceIncrementFor(root));
        }, () -> progressRows.save(ExplorerProgressJpaEntity.from(progress)));
    }

    /**
     * version 강제 증가 방식(저장 기술): 이미 FOR UPDATE 로 잡은 루트(findLocked)는 PESSIMISTIC_FORCE_INCREMENT — 그보다 약한
     * OPTIMISTIC_FORCE_INCREMENT 는 무시되기 때문(QA S-1). 자기 잠금이라 NOWAIT 여도 바로 얻는다. 잠그지 않고 읽은 루트(칭호 선택)는
     * OPTIMISTIC_FORCE_INCREMENT — 커밋 때 version 검사·증가.
     */
    private LockModeType forceIncrementFor(ExplorerProgressJpaEntity root) {
        return entityManager.getLockMode(root) == LockModeType.PESSIMISTIC_WRITE
            ? LockModeType.PESSIMISTIC_FORCE_INCREMENT : LockModeType.OPTIMISTIC_FORCE_INCREMENT;
    }

    private void saveRegion(ExplorerId explorer, ExploredRegion region) {
        regionRows.findById(ExplorerRegionJpaEntity.keyOf(explorer, region)).ifPresentOrElse(
            regionRow -> regionRow.apply(region),
            () -> regionRows.save(ExplorerRegionJpaEntity.from(explorer, region)));
    }
}
