package com.kobi.territory.progression.domain.progress;

import java.time.Instant;
import java.time.YearMonth;
import java.util.Objects;

/**
 * 보호권 장부 한 줄(8단계, streak_freeze). refId 가 멱등 키. 받은 줄은 양수(보유 상한에 막혀 못 받았으면 0 — "받을 일이 있었음"을 남겨
 * 재전달·재계산이 다시 주지 않게), 쓴 줄은 음수.
 *
 * @param month 쓴 줄이면 연속을 이은 달(그 달 첫 체크인), 받은 줄이면 null
 */
public record StreakFreezeEntry(String refId, FreezeReason reason, int amount, YearMonth month, Instant at) {
    public StreakFreezeEntry {
        Objects.requireNonNull(refId, "refId");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(at, "at");
        if ((reason == FreezeReason.USED) != (amount < 0)) throw new IllegalArgumentException("쓴 줄만 음수: " + refId);
    }
}
