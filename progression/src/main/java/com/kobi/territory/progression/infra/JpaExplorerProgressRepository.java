package com.kobi.territory.progression.infra;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.progression.domain.ExploredRegion;
import com.kobi.territory.progression.domain.ExploredRegions;
import com.kobi.territory.progression.domain.ExplorerProgress;
import com.kobi.territory.progression.domain.ExplorerProgressRepository;
import com.kobi.territory.progression.domain.Streak;
import com.kobi.territory.progression.domain.XpLedger;
import com.kobi.territory.progression.domain.XpLedgerEntry;
import com.kobi.territory.progression.domain.XpSource;
import com.kobi.territory.progression.infra.ProgressJpaEntities.BadgeRow;
import com.kobi.territory.progression.infra.ProgressJpaEntities.LedgerRow;
import com.kobi.territory.progression.infra.ProgressJpaEntities.ProgressRow;
import com.kobi.territory.progression.infra.ProgressJpaEntities.RegionRow;
import com.kobi.territory.progression.infra.ProgressJpaEntities.TitleRow;
import java.time.Clock;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/**
 * ExplorerProgress ↔ explorer_progress·xp_ledger·badge_earned·title_earned·explorer_region.
 * <ul>
 *   <li>평소(이벤트 처리·칭호 선택): 새로 쌓인 장부·뱃지·칭호를 추가하고 바뀐 지역만 쓴다 — 다시 조회하지 않아 트랜잭션이 짧다
 *       (QA P1-2: 짧을수록 사용자 커맨드와의 낙관적 락 경합이 준다).</li>
 *   <li>재계산 사본: 애그리거트 상태와 통째로 동기화(재계산이 지운 장부·지역 행 삭제).</li>
 * </ul>
 * 같은 트랜잭션에서 find 로 읽은 행이 영속 컨텍스트에 있으므로 version 낙관적 락이 동시 갱신을 막는다.
 */
@Repository
class JpaExplorerProgressRepository implements ExplorerProgressRepository {

    private final ProgressRowRepository progressRows;
    private final LedgerRowRepository ledgerRows;
    private final BadgeRowRepository badgeRows;
    private final TitleRowRepository titleRows;
    private final RegionRowRepository regionRows;
    private final Clock clock;

    JpaExplorerProgressRepository(ProgressRowRepository progressRows, LedgerRowRepository ledgerRows,
                                  BadgeRowRepository badgeRows, TitleRowRepository titleRows, RegionRowRepository regionRows,
                                  Clock clock) {
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
        return progressRows.findById(id).map(row -> ExplorerProgress.restore(explorerId,
            XpLedger.of(ledgerRows.findByExplorerIdOrderByIdAsc(id).stream()
                .map(ledgerRow -> new XpLedgerEntry(XpSource.valueOf(ledgerRow.getSource()), ledgerRow.getAmount(),
                    ledgerRow.getRefId(), ledgerRow.getCreatedAt()))
                .toList()),
            exploredRegions(explorerId),
            row.getStreakMonths() == 0 ? Streak.NONE
                : new Streak(row.getStreakMonths(), YearMonth.parse(row.getStreakLastMonth())),
            badgeRows.findByExplorerId(id).stream().collect(Collectors.toMap(BadgeRow::getBadgeId, BadgeRow::getEarnedAt,
                (first, second) -> first, LinkedHashMap::new)),
            titleRows.findByExplorerId(id).stream().collect(Collectors.toMap(TitleRow::getTitleId, TitleRow::getEarnedAt,
                (first, second) -> first, LinkedHashMap::new)),
            row.getTitleId(), row.getLevel()));
    }

    @Override
    public ExploredRegions exploredRegions(ExplorerId explorerId) {
        return ExploredRegions.of(regionRows.findByExplorerId(explorerId.value()).stream()
            .map(regionRow -> new ExploredRegion(RegionCode.of(regionRow.getRegionCode()), regionRow.getProvinceCode(),
                Rarity.valueOf(regionRow.getRarity()), regionRow.getFirstVisitedAt(), splitSet(regionRow.getActiveMapIds())))
            .toList());
    }

    @Override
    public void save(ExplorerProgress progress) {
        String id = progress.explorerId().value();
        ProgressRow row = progressRows.findById(id).orElseGet(() -> new ProgressRow(id));
        row.setXp(progress.xp());
        row.setLevel(progress.level());
        row.setTitleId(progress.selectedTitle().orElse(null));
        row.setStreakMonths(progress.streak().months());
        row.setStreakLastMonth(progress.streak().lastMonth() == null ? null : progress.streak().lastMonth().toString());
        row.setUpdatedAt(clock.instant());
        progressRows.save(row);
        if (progress.rebuilt()) {
            syncAll(id, progress);
        } else {
            appendChanges(id, progress);
        }
    }

    /** 평소 경로: 추가·변경분만. */
    private void appendChanges(String id, ExplorerProgress progress) {
        progress.ledger().unsaved().forEach(entry -> ledgerRows.save(toRow(id, entry)));
        progress.unsavedBadges().forEach(badge -> badgeRows.save(new BadgeRow(id, badge, progress.badges().get(badge))));
        progress.unsavedTitles().forEach(title -> titleRows.save(new TitleRow(id, title, progress.titles().get(title))));
        progress.regions().changed().forEach(region -> regionRows.save(write(
            regionRows.findById(new RegionRow.Key(id, region.code().value()))
                .orElseGet(() -> new RegionRow(id, region.code().value())), region)));
    }

    /** 재계산 경로: 애그리거트와 통째로 같게. */
    private void syncAll(String id, ExplorerProgress progress) {
        Map<String, LedgerRow> existingLedger = ledgerRows.findByExplorerIdOrderByIdAsc(id).stream()
            .collect(Collectors.toMap(LedgerRow::getRefId, Function.identity()));
        for (XpLedgerEntry entry : progress.ledger().entries()) {
            if (existingLedger.remove(entry.refId()) == null) ledgerRows.save(toRow(id, entry));
        }
        ledgerRows.deleteAll(existingLedger.values());

        Set<String> haveBadges = badgeRows.findByExplorerId(id).stream().map(BadgeRow::getBadgeId).collect(Collectors.toSet());
        progress.badges().forEach((badge, at) -> {
            if (!haveBadges.contains(badge)) badgeRows.save(new BadgeRow(id, badge, at));
        });
        Set<String> haveTitles = titleRows.findByExplorerId(id).stream().map(TitleRow::getTitleId).collect(Collectors.toSet());
        progress.titles().forEach((title, at) -> {
            if (!haveTitles.contains(title)) titleRows.save(new TitleRow(id, title, at));
        });

        Map<String, RegionRow> existingRegions = regionRows.findByExplorerId(id).stream()
            .collect(Collectors.toMap(RegionRow::getRegionCode, Function.identity()));
        for (ExploredRegion region : progress.regions().all()) {
            RegionRow regionRow = Optional.ofNullable(existingRegions.remove(region.code().value()))
                .orElseGet(() -> new RegionRow(id, region.code().value()));
            regionRows.save(write(regionRow, region));
        }
        regionRows.deleteAll(existingRegions.values());
    }

    private static LedgerRow toRow(String id, XpLedgerEntry entry) {
        return new LedgerRow(id, entry.source().name(), entry.amount(), entry.refId(), entry.at());
    }

    private static RegionRow write(RegionRow regionRow, ExploredRegion region) {
        regionRow.setProvinceCode(region.provinceCode());
        regionRow.setRarity(region.rarity().name());
        regionRow.setFirstVisitedAt(region.firstVisitedAt());
        regionRow.setActiveMapCount(region.activeMapCount());
        regionRow.setActiveMapIds(String.join(",", region.activeMaps()));
        return regionRow;
    }

    static Set<String> splitSet(String csv) {
        return csv == null || csv.isBlank() ? Set.of()
            : Arrays.stream(csv.split(",")).filter(token -> !token.isBlank()).collect(Collectors.toSet());
    }

}
