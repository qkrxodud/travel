package com.kobi.territory.progression.domain.progress;

import java.time.Instant;

/**
 * 연속 탐험 마일스톤 한 단계의 내 현황(8단계).
 *
 * @param titleId         이 마일스톤의 칭호 id(칭호 규칙 STREAK 중 이 개월 수 — 없으면 null)
 * @param reachedAt       받은 시각(아직이면 null)
 * @param remainingMonths 지금 보이는 연속에서 더 필요한 개월 수(받았으면 0, 아니면 1 이상)
 */
public record MilestoneStatus(int months, int xp, int freezes, String titleId, Instant reachedAt, int remainingMonths) {

    public boolean reached() {
        return reachedAt != null;
    }
}
