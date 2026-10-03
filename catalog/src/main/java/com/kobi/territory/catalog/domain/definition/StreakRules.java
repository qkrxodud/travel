package com.kobi.territory.catalog.domain.definition;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 연속 탐험 규칙(streak-rules.json, 8단계): 보호권(빈 달을 메워 스트릭을 지켜 주는 것)의 보유 상한과 월간 퀘스트 완주 보상 수, 마일스톤 목록.
 * 판정·소모·지급은 진행 도메인이 한다 — 카탈로그는 값만 보관한다.
 *
 * @param freezeMaxHeld          보호권 최대 보유 수
 * @param monthlyQuestsFreezes   한 달의 월간 퀘스트를 모두 보상 받으면 주는 보호권 수
 * @param milestones             개월 수 오름차순, 중복 없음
 */
public record StreakRules(int freezeMaxHeld, int monthlyQuestsFreezes, List<StreakMilestone> milestones) {

    public StreakRules {
        if (freezeMaxHeld < 0 || monthlyQuestsFreezes < 0) throw new IllegalStateException("보호권 값은 0 이상");
        milestones = milestones.stream().sorted(Comparator.comparingInt(StreakMilestone::months)).toList();
        Set<Integer> seen = new HashSet<>();
        milestones.forEach(milestone -> {
            if (!seen.add(milestone.months())) throw new IllegalStateException("마일스톤 개월 수 중복: " + milestone.months());
        });
    }

    /** 보호권·마일스톤이 없는 규칙(진행 정의가 필요 없는 곳). */
    public static StreakRules none() {
        return new StreakRules(0, 0, List.of());
    }

    /** 이 개월 수의 마일스톤이 정의돼 있는지(아이템 지급 규칙 참조 검증). */
    public boolean defines(int months) {
        return milestones.stream().anyMatch(milestone -> milestone.months() == months);
    }
}
