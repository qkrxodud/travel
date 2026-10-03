package com.kobi.territory.progression.domain.policy;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 연속 탐험 규칙(정책 VO, 8단계): 보호권 최대 보유 수, 한 달 월간 퀘스트를 모두 보상 받으면 주는 보호권 수, 마일스톤(개월 수 오름차순).
 * 값은 카탈로그 streak-rules.json 에서 application 이 옮겨 온다(도메인에 숫자를 박지 않는다).
 */
public final class StreakRules {

    private final int freezeMaxHeld;
    private final int monthlyQuestsFreezes;
    private final List<StreakMilestone> milestones;

    public StreakRules(int freezeMaxHeld, int monthlyQuestsFreezes, List<StreakMilestone> milestones) {
        if (freezeMaxHeld < 0 || monthlyQuestsFreezes < 0) throw new IllegalArgumentException("보호권 값은 0 이상");
        this.freezeMaxHeld = freezeMaxHeld;
        this.monthlyQuestsFreezes = monthlyQuestsFreezes;
        this.milestones = milestones.stream().sorted(Comparator.comparingInt(StreakMilestone::months)).toList();
    }

    /** 보호권·마일스톤이 없는 규칙. */
    public static StreakRules none() {
        return new StreakRules(0, 0, List.of());
    }

    public int freezeMaxHeld() {
        return freezeMaxHeld;
    }

    public int monthlyQuestsFreezes() {
        return monthlyQuestsFreezes;
    }

    /** 연속 months 개월로 닿은 마일스톤(오름차순). */
    public List<StreakMilestone> reachedBy(int months) {
        return milestones.stream().filter(milestone -> milestone.months() <= months).toList();
    }

    public Optional<StreakMilestone> find(int months) {
        return milestones.stream().filter(milestone -> milestone.months() == months).findFirst();
    }

    /** 아직 받지 않은 가장 가까운 마일스톤(받은 개월 수 목록 기준). */
    public Optional<StreakMilestone> nextAfter(List<Integer> reachedMonths) {
        return milestones.stream().filter(milestone -> !reachedMonths.contains(milestone.months())).findFirst();
    }

    /** 마일스톤 전체(오름차순, 불변 뷰). */
    public List<StreakMilestone> milestones() {
        return milestones;
    }
}
