package com.kobi.territory.analytics.domain.tracking;

import java.util.List;

/**
 * 화면 이벤트 묶음을 받은 결과.
 *
 * @param accepted   적을 이벤트
 * @param rejected   받지 않은 이벤트와 이유
 * @param ignoredBot 봇 요청이라 통째로 버렸는지(오류는 아니다)
 */
public record BatchOutcome(List<TrackedEvent> accepted, List<Rejection> rejected, boolean ignoredBot) {

    public BatchOutcome {
        accepted = List.copyOf(accepted);
        rejected = List.copyOf(rejected);
    }
}
