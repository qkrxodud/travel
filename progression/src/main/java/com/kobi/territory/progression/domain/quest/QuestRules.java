package com.kobi.territory.progression.domain.quest;

import com.kobi.territory.progression.domain.ProgressionError;
import java.util.List;

/** 일급 컬렉션: 퀘스트 규칙(정책 VO — application 이 카탈로그에서 조립). 정의 순서를 유지한다. */
public final class QuestRules {

    private final List<QuestRule> items;

    public QuestRules(List<QuestRule> rules) {
        this.items = List.copyOf(rules);
    }

    public QuestRule require(String questId) {
        return items.stream().filter(rule -> rule.id().equals(questId)).findFirst()
            .orElseThrow(() -> ProgressionError.QUEST_NOT_FOUND.exception(questId));
    }

    /** 이 보드(월간/상시)에 속하는 규칙들. */
    public List<QuestRule> byPeriod(QuestPeriod period) {
        return items.stream().filter(rule -> rule.belongsTo(period)).toList();
    }

    /** 범위(월간/상시)별 규칙들 — 화면 목록 순서. */
    public List<QuestRule> byScope(QuestRule.Scope scope) {
        return items.stream().filter(rule -> rule.scope() == scope).toList();
    }
}
