package com.kobi.territory.progression.domain.policy;

import java.util.List;
import java.util.Set;

/** 일급 컬렉션: 뱃지 정의(정책 VO). */
public final class Badges {

    private final List<Badge> items;

    public Badges(List<Badge> badges) {
        this.items = List.copyOf(badges);
    }

    /** 아직 얻지 않았고 지금 조건을 만족하는 뱃지 id(정의 순서). */
    public List<String> newlyEarned(Set<String> earned, BadgeFacts facts) {
        return items.stream().filter(badge -> !earned.contains(badge.id()) && badge.rule().satisfiedBy(facts))
            .map(Badge::id).toList();
    }
}
