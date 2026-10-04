package com.kobi.territory.analytics.domain.metrics;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 원본 이벤트·방문·여정에서 수를 세는 포트(집계 질의). "사람"은 행위자 열쇠 기준이고 봇·누구인지 모르는 열람은 세지 않는다.
 */
public interface MetricsReader {

    int newVisitors(LocalDate day);

    int newExplorers(LocalDate day);

    /** 구간 안에 활동한 사람 수. */
    int activeActors(DayRange range);

    /** 구간 안에 활동한 탐험가 수(탐험가로 이어진 사람만). */
    int activeExplorers(DayRange range);

    PageViews pageViews(LocalDate day);

    /** 기능 이벤트별로 구간 안에 그 이벤트가 있는 사람 수(없는 기능은 빠질 수 있다). */
    Map<String, Integer> featureUsers(DayRange range, List<String> features);

    /** 구간 안 화면 오류 토스트의 오류 코드별 횟수. */
    Map<String, Integer> errorCounts(DayRange range);

    /** 구간 안에 가입한 탐험가 중 초대 유입 수. */
    int invitedNewExplorers(DayRange createdIn);

    /** 구간 안에 가입한 탐험가 중 카드 유입 수. */
    int cardNewExplorers(DayRange createdIn);

    /** 구간 안에 가입한 탐험가 중 초대 유입 또는 카드 유입인 수(한 사람은 한 번). */
    int viralNewExplorers(DayRange createdIn);

    int funnelFirstScreen(LocalDate cohortDay);

    int funnelFirstCheckIn(LocalDate cohortDay, DayRange checkInWindow);

    int funnelRevisited(LocalDate cohortDay, DayRange checkInWindow);

    /** 그날 가입한 탐험가 수(리텐션 코호트 크기). */
    int cohortSize(LocalDate cohortDay);

    /** 그날 가입한 탐험가 중 activeDay 에 활동한 수. */
    int retained(LocalDate cohortDay, LocalDate activeDay);
}
