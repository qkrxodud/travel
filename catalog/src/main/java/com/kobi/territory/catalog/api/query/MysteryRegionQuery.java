package com.kobi.territory.catalog.api.query;

import java.time.Instant;
import java.util.Optional;

/**
 * 이번 주 미스터리 지역 공개 Query(8단계). 전체 사용자 공통으로 한 주에 한 곳 — 희귀·전설 지역 중 방문자 비율 하위 구간에서 주차로
 * 결정적으로 고르고 mystery_week 에 기록한다(같은 주는 누구에게나, 서버를 다시 띄워도 같은 지역).
 */
public interface MysteryRegionQuery {

    /**
     * 처리 시각 at 이 속한 주의 미스터리 지역. 기록이 있으면 그것, 없으면 그 주가 <b>지금 주</b>일 때만 골라서 기록한다 —
     * 지난 주를 나중에 고르지 않는다(소급 없음: 기록이 없는 지난 주의 체크인은 보너스가 없다).
     */
    Optional<MysteryWeekView> weekOf(Instant at);

    /** 지금 주의 미스터리 지역(없으면 골라서 기록). */
    MysteryWeekView thisWeek();
}
