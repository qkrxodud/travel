package com.kobi.territory.catalog.application;

import com.kobi.territory.catalog.domain.lineup.CollectionSchedule;
import com.kobi.territory.catalog.domain.lineup.LineupPolicy;
import java.util.Objects;

/**
 * 계절 회차 지역 목록(13s단계) 설정값 묶음(territory.tourapi.lineup.* · territory.tourapi.collect.*).
 *
 * @param boundaryToleranceKilometers 축제 좌표가 경계 밖일 때 가장 가까운 지역으로 볼 거리(km) — 경계 자료가 단순화돼 있어서
 * @param collectOnStartup            기동 직후 자동 수집을 한 번 돈다(키를 넣고 재기동하면 바로 후보가 보이게)
 */
public record SeasonLineupSettings(LineupPolicy policy, CollectionSchedule schedule, double boundaryToleranceKilometers,
                                   boolean collectOnStartup) {

    public SeasonLineupSettings {
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(schedule, "schedule");
        if (boundaryToleranceKilometers < 0) throw new IllegalArgumentException("경계 허용 거리는 0 이상");
    }
}
