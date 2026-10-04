package com.kobi.territory.analytics.domain.tracking;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/**
 * 화면 이벤트 수집 규칙(설정 territory.analytics.ingest.*).
 *
 * @param maxBatchEvents 한 번에 받는 이벤트 수 상한
 * @param maxClockSkew   화면 시각을 믿는 범위 — 받은 시각보다 이만큼 이전까지(오프라인으로 모았다 보낸 경우). 미래 쪽은 1분까지
 * @param zone           "하루"의 기준 시간대(territory.time-zone)
 */
public record IngestPolicy(int maxBatchEvents, Duration maxClockSkew, ZoneId zone) {

    private static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(1);

    public IngestPolicy {
        if (maxBatchEvents < 1) throw new IllegalArgumentException("maxBatchEvents ≥ 1");
        Objects.requireNonNull(maxClockSkew, "maxClockSkew");
        Objects.requireNonNull(zone, "zone");
    }

    /** 화면 시각이 믿을 만한 범위면 그 시각, 아니면(없음·너무 옛날·미래) 받은 시각. */
    public Instant occurredAt(Instant clientAt, Instant receivedAt) {
        if (clientAt == null) return receivedAt;
        boolean tooOld = clientAt.isBefore(receivedAt.minus(maxClockSkew));
        boolean future = clientAt.isAfter(receivedAt.plus(FUTURE_TOLERANCE));
        return tooOld || future ? receivedAt : clientAt;
    }

    public LocalDate dayOf(Instant at) {
        return LocalDate.ofInstant(at, zone);
    }
}
