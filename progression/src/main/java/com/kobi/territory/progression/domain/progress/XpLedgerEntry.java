package com.kobi.territory.progression.domain.progress;

import com.kobi.territory.progression.domain.policy.XpSource;
import java.time.Instant;
import java.util.Objects;

/** XP 장부 한 줄. refId 는 전역 유일(DB UNIQUE)이며 멱등 키다. 감소는 음수 항목으로만. */
public record XpLedgerEntry(XpSource source, int amount, String refId, Instant at) {
    public XpLedgerEntry {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(refId, "refId");
        Objects.requireNonNull(at, "at");
    }
}
