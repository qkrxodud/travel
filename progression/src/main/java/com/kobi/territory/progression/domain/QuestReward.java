package com.kobi.territory.progression.domain;

import com.kobi.territory.common.model.ExplorerId;
import java.time.Instant;

/** 퀘스트 보상 받기 결과 → application 이 QuestCompleted(공개 이벤트)로 적재한다. */
public record QuestReward(ExplorerId explorerId, QuestPeriod period, String questId, int xp, Instant claimedAt) {}
