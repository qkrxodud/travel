package com.kobi.territory.progression.domain.progress;

import com.kobi.territory.common.model.ExplorerId;
import java.util.Collection;
import java.util.List;

/** 연속 탐험 상태의 읽기 전용 포트(12단계 — 알림의 스트릭 지키기 대상 고르기). 진행 기록이 없는 탐험가는 결과에 없다. */
public interface StreakStandings {

    List<StreakStanding> of(Collection<ExplorerId> explorerIds);
}
