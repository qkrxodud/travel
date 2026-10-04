package com.kobi.territory.notification.domain.campaign;

/**
 * 스트릭 지키기 알림에 넣을 사실(진행 컨텍스트의 공개 조회 StreakQuery 에서).
 *
 * @param months               지금 이어지고 있는 연속 탐험 개월 수
 * @param freezesHeld          가진 보호권 수
 * @param freezesNeededIfMissed 이번 달을 놓치면 다음 달에 이어 가는 데 필요한 보호권 수
 */
public record StreakFacts(int months, int freezesHeld, int freezesNeededIfMissed) {

    public StreakFacts {
        if (months < 1 || freezesHeld < 0 || freezesNeededIfMissed < 1) throw new IllegalArgumentException("스트릭 사실이 올바르지 않다");
    }

    /** 이번 달을 놓쳐도 가진 보호권으로 이어 갈 수 있는지. */
    public boolean coveredIfMissed() {
        return freezesHeld >= freezesNeededIfMissed;
    }
}
