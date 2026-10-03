package com.kobi.territory.catalog.domain.definition;

import java.util.Objects;

/**
 * 연속 탐험(스트릭) 마일스톤 한 단계(streak-rules.json). 처음 도달했을 때 한 번만 — XP·칭호(streak-{months})·보호권·한정 아이템
 * (STREAK_MILESTONE 지급 규칙, 아이템 정의는 DB). 끊겼다 다시 쌓아도 다시 주지 않는 것은 진행이 장부로 지킨다.
 *
 * @param freezes 함께 받는 보호권 수(보유 상한 안에서만)
 * @param title   칭호 이름
 */
public record StreakMilestone(int months, int xp, int freezes, String title) {
    public StreakMilestone {
        if (months < 1) throw new IllegalStateException("마일스톤 개월 수는 1 이상: " + months);
        if (xp < 0 || freezes < 0) throw new IllegalStateException("마일스톤 보상은 0 이상: " + months);
        Objects.requireNonNull(title, "title");
    }

    /** 칭호 id(streak-{months}). */
    public String titleId() {
        return "streak-" + months;
    }
}
