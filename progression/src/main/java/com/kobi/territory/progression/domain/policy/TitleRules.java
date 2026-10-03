package com.kobi.territory.progression.domain.policy;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/** 일급 컬렉션: 칭호 규칙(정책 VO). 정의 순서(레벨 → 테마 → 상시 도전 → 시·도)를 유지한다. */
public final class TitleRules {

    private final List<TitleRule> items;

    public TitleRules(List<TitleRule> titleRules) {
        this.items = List.copyOf(titleRules);
    }

    public boolean contains(String titleId) {
        return items.stream().anyMatch(title -> title.id().equals(titleId));
    }

    /** 아직 얻지 않았고 earned 판정을 통과하는 칭호들(정의 순서). */
    public List<TitleRule> newlyEarned(Set<String> earned, Predicate<TitleRule> earnedNow) {
        return items.stream().filter(title -> !earned.contains(title.id()) && earnedNow.test(title)).toList();
    }

    /** 이 개월 수 연속 탐험 마일스톤의 칭호(STREAK, 8단계). */
    public Optional<TitleRule> forStreak(int months) {
        return items.stream()
            .filter(title -> title.source() == TitleRule.Source.STREAK && title.ref().equals(String.valueOf(months))).findFirst();
    }

    /** level 이하에서 가장 높은 레벨 칭호. */
    public Optional<TitleRule> levelTitleFor(int level) {
        return items.stream()
            .filter(title -> title.source() == TitleRule.Source.LEVEL && level >= Integer.parseInt(title.ref()))
            .reduce((lower, higher) -> higher);
    }
}
