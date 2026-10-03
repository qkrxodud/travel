package com.kobi.territory.social.domain.stats;

import com.kobi.territory.common.model.ExplorerId;
import java.util.List;
import java.util.Optional;

/** 집계 스냅숏 저장소(rank_percentile · region_stats · province_stats). 배치가 통째로 바꾸고 조회가 읽는다. */
public interface RankSnapshotRepository {

    /** 세 테이블을 지우고 스냅숏으로 다시 채운다(호출자 트랜잭션 — 읽는 쪽은 바뀌기 전 또는 후만 본다). */
    void replace(RankSnapshot snapshot);

    Optional<RankPercentile> findPercentile(ExplorerId explorerId);

    List<RegionStat> regionStats();

    List<ProvinceStat> provinceStats();
}
