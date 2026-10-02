package com.kobi.territory.progression.domain.progress;

import com.kobi.territory.progression.domain.quest.QuestPeriod;
import java.util.Objects;

/** 진행이 받는 퀘스트 보상 XP 한 건(보드 기간 id + 퀘스트 id + XP). 재계산 복구 규칙에서 쓴다. */
public record QuestXp(QuestPeriod period, String questId, int xp) {
    public QuestXp {
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(questId, "questId");
    }
}
