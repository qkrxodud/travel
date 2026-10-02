package com.kobi.territory.progression.domain.quest;

import java.time.Instant;
import java.util.Objects;

/** 퀘스트 하나의 진행(quest_progress 행): 집계 + 보상 받은 시각. */
public record QuestProgress(String questId, QuestTally tally, Instant claimedAt) {

    public QuestProgress {
        Objects.requireNonNull(questId, "questId");
        Objects.requireNonNull(tally, "tally");
    }

    static QuestProgress empty(String questId) {
        return new QuestProgress(questId, QuestTally.EMPTY, null);
    }

    public boolean claimed() {
        return claimedAt != null;
    }

    public int current(QuestRule rule) {
        return tally.current(rule);
    }

    public boolean achieved(QuestRule rule) {
        return current(rule) >= rule.target();
    }

    /** 지금 보상 받기를 할 수 있는지(달성했고 아직 안 받음). 보드가 닫혔는지는 QuestBoard 가 본다. */
    public boolean claimable(QuestRule rule) {
        return achieved(rule) && !claimed();
    }
}
