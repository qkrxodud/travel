package com.kobi.territory.progression.domain;

import java.util.List;

/** 일급 컬렉션: 퀘스트 규칙(정책 VO). */
public record QuestRules(List<QuestRule> rules) {

    public QuestRules {
        rules = List.copyOf(rules);
    }

    public QuestRule require(String questId) {
        return rules.stream().filter(rule -> rule.id().equals(questId)).findFirst()
            .orElseThrow(() -> ProgressionError.QUEST_NOT_FOUND.exception(questId));
    }

    /** 이 보드(월간/상시)에 속하는 규칙들. */
    public List<QuestRule> of(QuestPeriod period) {
        return rules.stream().filter(rule -> rule.belongsTo(period)).toList();
    }
}
