package com.kobi.territory.catalog.application;

import com.kobi.territory.catalog.domain.definition.SeasonDefinition;
import com.kobi.territory.catalog.domain.lineup.CollectionPlan;
import com.kobi.territory.catalog.domain.lineup.LineupRegions;
import com.kobi.territory.catalog.domain.lineup.SeasonLineup;
import java.util.Objects;

/**
 * 관리자 화면의 회차 하나(13s단계).
 *
 * @param inEffect 이 회차에 쓰는(쓸) 지역 목록 — 확정본, 없으면 기본 목록(AI 추정)
 * @param locked   회차가 열려(또는 지나) 목록이 고정됐는지
 * @param nextPlan 지금 자동 수집이 돈다면 할 일
 */
public record SeasonLineupStatus(SeasonDefinition season, SeasonLineup lineup, LineupRegions inEffect, boolean locked,
                                 CollectionPlan nextPlan) {

    public SeasonLineupStatus {
        Objects.requireNonNull(season, "season");
        Objects.requireNonNull(lineup, "lineup");
        Objects.requireNonNull(inEffect, "inEffect");
        Objects.requireNonNull(nextPlan, "nextPlan");
    }
}
