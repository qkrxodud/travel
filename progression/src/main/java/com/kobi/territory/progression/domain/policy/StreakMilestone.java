package com.kobi.territory.progression.domain.policy;

/**
 * 연속 탐험 마일스톤(정책 VO — 카탈로그 streak-rules.json). 처음 도달하면 XP·칭호·보호권(보유 상한 안)·한정 아이템, 한 번만.
 *
 * @param xp      보상 XP(1 이상 — 장부 항목이 "받았음" 기록이다)
 * @param freezes 함께 받는 보호권 수
 */
public record StreakMilestone(int months, int xp, int freezes) {
    public StreakMilestone {
        if (months < 1) throw new IllegalArgumentException("months >= 1");
        if (xp < 1) throw new IllegalArgumentException("마일스톤 XP 는 1 이상(장부에 받았음이 남아야 한다): " + months);
        if (freezes < 0) throw new IllegalArgumentException("freezes >= 0");
    }
}
