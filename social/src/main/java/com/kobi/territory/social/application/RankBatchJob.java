package com.kobi.territory.social.application;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.api.query.TerritoryQuery;
import com.kobi.territory.progression.api.query.ExplorerRegionQuery;
import com.kobi.territory.progression.api.query.RegionVisitorsView;
import com.kobi.territory.social.domain.stats.ProvinceTallies;
import com.kobi.territory.social.domain.stats.ProvinceTally;
import com.kobi.territory.social.domain.stats.RankSnapshot;
import com.kobi.territory.social.domain.stats.RankSnapshotRepository;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 일 1회 집계 배치(§5): explorer_region(진행)으로 상위 %(rank_percentile)·지역별 방문자 비율(region_stats)·시·도 평균 유저
 * (province_stats)를 통째로 다시 만든다. 주기는 territory.social.rank-batch-cron(기본 매일 04:30, territory.time-zone 기준,
 * "-" 면 끔), local 은 {@code POST /dev/batch/rank} 로 수동 실행. 설계의 Redis 대신 DB + 애플리케이션 캐시 — 트래픽이 생기면 Redis.
 * 읽기 모델이라 언제 다시 돌려도 같은 입력이면 같은 결과다.
 */
@Component
public class RankBatchJob {

    private static final Logger log = LoggerFactory.getLogger(RankBatchJob.class);

    private final TerritoryQuery territories;
    private final ExplorerRegionQuery regions;
    private final RankSnapshotRepository snapshots;
    private final RankingService rankings;
    private final Clock clock;
    private final TransactionTemplate writeTx;
    private final TransactionTemplate readTx;
    /** 시작 이후 넘쳐 맞춘 지역 누계(원인 추적용 카운터). */
    private final AtomicLong overflowedRegions = new AtomicLong();

    public RankBatchJob(TerritoryQuery territories, ExplorerRegionQuery regions, RankSnapshotRepository snapshots,
                        RankingService rankings, Clock clock, PlatformTransactionManager transactionManager) {
        this.territories = territories;
        this.regions = regions;
        this.snapshots = snapshots;
        this.rankings = rankings;
        this.clock = clock;
        this.writeTx = new TransactionTemplate(transactionManager);
        this.readTx = new TransactionTemplate(transactionManager);
        this.readTx.setReadOnly(true);
    }

    public long overflowedRegions() {
        return overflowedRegions.get();
    }

    @Scheduled(cron = "${territory.social.rank-batch-cron:0 30 4 * * *}", zone = "${territory.time-zone:Asia/Seoul}")
    public RankSnapshot run() {
        // 세 집계를 한 읽기 트랜잭션에서 읽는다(MySQL REPEATABLE READ — 같은 시점의 explorer_region)
        RankSnapshot snapshot = readTx.execute(status -> {
            List<String> active = territories.explorerIds();
            return RankSnapshot.compute(active.stream().map(ExplorerId::of).toList(),
                ProvinceTallies.of(regions.provinceTallies().stream()
                    .map(tally -> new ProvinceTally(ExplorerId.of(tally.explorerId()), tally.provinceCode(), tally.regionCount()))
                    .toList()),
                regions.regionVisitorsAmong(active).stream()
                    .collect(Collectors.toMap(RegionVisitorsView::regionCode, RegionVisitorsView::visitorCount)),
                clock.instant());
        });
        snapshot.overflows().forEach(overflow -> log.error(
            "상위 % 배치: 지역 {} 방문자 {}명이 모집단 {}명을 넘어 모집단으로 맞췄다 — explorer_region·탐험가 상태 원천을 확인할 것",
            overflow.regionCode(), overflow.counted(), overflow.population()));
        overflowedRegions.addAndGet(snapshot.overflows().size());
        writeTx.executeWithoutResult(status -> snapshots.replace(snapshot));
        rankings.invalidateStats();
        log.info("상위 % 배치: 모집단(지역 1곳 이상 활성 탐험가) {}명, 순위 {}명, 지역 통계 {}곳", snapshot.population(), snapshot.percentiles().size(),
            snapshot.regionStats().size());
        return snapshot;
    }
}
