package com.kobi.territory.catalog.application;

import com.kobi.territory.catalog.domain.lineup.CollectionSchedule;
import com.kobi.territory.catalog.domain.lineup.FetchUsage;
import java.util.List;

/** 관리자 화면 전체: TourAPI 연결 현황·자동 수집 정책·지금 열린 회차와 다음 회차들. */
public record SeasonLineupOverview(FetchUsage usage, CollectionSchedule schedule, List<SeasonLineupStatus> rounds) {

    public SeasonLineupOverview {
        rounds = List.copyOf(rounds);
    }
}
