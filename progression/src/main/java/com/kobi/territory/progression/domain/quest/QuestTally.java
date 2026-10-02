package com.kobi.territory.progression.domain.quest;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 일급 컬렉션: 퀘스트 진행 집계(quest_progress.tally). 센 지역을 "시·도|지역" 키로 기억해 같은 지역을 두 번 세지 않는다
 * (멱등 — 같은 이벤트 재전달·취소 후 재체크인 모두). 목표를 넘는 키는 버린다(시·도별 지표는 시·도당 param 개까지만).
 */
public final class QuestTally {

    public static final QuestTally EMPTY = new QuestTally(Set.of());

    private final Set<String> keys;

    private QuestTally(Collection<String> keys) {
        this.keys = Set.copyOf(new LinkedHashSet<>(Objects.requireNonNull(keys, "keys")));
    }

    /** 저장된 키로 복원. */
    public static QuestTally of(Collection<String> keys) {
        return new QuestTally(keys);
    }

    static String key(QuestFact fact) {
        return fact.provinceCode() + "|" + fact.region().value();
    }

    /** 현재 진행도(목표로 자른 값). */
    public int current(QuestRule rule) {
        int raw = rule.metric() == QuestRule.Metric.PROVINCES_WITH_MIN_REGIONS
            ? (int) keys.stream().map(QuestTally::province).distinct()
                .filter(province -> countIn(province) >= rule.param()).count()
            : keys.size();
        return Math.min(raw, rule.target());
    }

    QuestTally count(QuestFact fact, QuestRule rule) {
        if (!rule.counts(fact) || keys.contains(key(fact)) || current(rule) >= rule.target()) return this;
        if (rule.metric() == QuestRule.Metric.PROVINCES_WITH_MIN_REGIONS && countIn(fact.provinceCode()) >= rule.param()) {
            return this;
        }
        Set<String> next = new LinkedHashSet<>(keys);
        next.add(key(fact));
        return new QuestTally(next);
    }

    /** 센 지역 키 수(저장용 current_count). */
    public int size() {
        return keys.size();
    }

    public boolean isEmpty() {
        return keys.isEmpty();
    }

    /** 저장·비교용 정렬된 불변 키 목록. */
    public List<String> sorted() {
        return keys.stream().sorted().toList();
    }

    private long countIn(String province) {
        return keys.stream().filter(key -> province(key).equals(province)).count();
    }

    private static String province(String key) {
        return key.substring(0, key.indexOf('|'));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof QuestTally tally && keys.equals(tally.keys);
    }

    @Override
    public int hashCode() {
        return keys.hashCode();
    }

    @Override
    public String toString() {
        return "QuestTally" + sorted();
    }
}
