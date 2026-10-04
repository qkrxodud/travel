package com.kobi.territory.progression.api.query;

import java.util.Collection;
import java.util.List;

/** 연속 탐험 공개 Query(12단계) — 알림이 월말 "스트릭 지키기" 대상과 보호권 수를 고를 때 쓴다. */
public interface StreakQuery {

    /** 이 탐험가들의 이번 달 연속 탐험 상태. 진행 기록이 없는 탐험가는 결과에 없다(탐험가 확인은 하지 않는다). */
    List<StreakStandingView> standingsOf(Collection<String> explorerIds);
}
