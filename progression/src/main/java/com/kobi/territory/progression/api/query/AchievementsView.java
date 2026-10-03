package com.kobi.territory.progression.api.query;

import java.time.Instant;
import java.util.List;

/**
 * 탐험가가 받은 8단계 업적(공개 Query DTO) — 연속 탐험 마일스톤과 시·도 정복. 꾸미기 재계산이 회수 없는 한정 아이템(STREAK_MILESTONE·
 * PROVINCE_COMPLETE)을 빠짐없이 복구할 때 쓴다(기간 판정은 받은 시각).
 */
public record AchievementsView(List<Milestone> milestones, List<Conquest> conquests) {

    public AchievementsView {
        milestones = List.copyOf(milestones);
        conquests = List.copyOf(conquests);
    }

    /** @param months 마일스톤 개월 수 */
    public record Milestone(int months, Instant reachedAt) {}

    public record Conquest(String provinceCode, Instant conqueredAt) {}
}
