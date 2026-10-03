package com.kobi.territory.progression.domain.progress;

import java.time.Instant;
import java.util.Objects;

/**
 * 보호권을 받을 일 한 건(8단계) — 장부에 남은 보상(마일스톤 XP·한 달 월간 퀘스트 전부)에서 결정적으로 다시 만든다. 재계산이 보호권 장부를
 * 비우고 처리 시각 순으로 다시 쌓을 때 쓴다(소모와 상한이 순서에 달려 있어서).
 */
public record FreezeGrant(String refId, FreezeReason reason, int amount, Instant at) {
    public FreezeGrant {
        Objects.requireNonNull(refId, "refId");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(at, "at");
        if (reason == FreezeReason.USED || amount < 0) throw new IllegalArgumentException("받는 일만: " + refId);
    }
}
