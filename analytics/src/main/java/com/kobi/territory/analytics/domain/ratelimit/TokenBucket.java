package com.kobi.territory.analytics.domain.ratelimit;

import java.time.Duration;
import java.time.Instant;

/**
 * 토큰 버킷 하나 — 처음엔 가득(capacity), 요청마다 하나 쓰고, 1분에 perMinute 개씩(연속으로) 다시 찬다. 동시 요청에 안전하다.
 */
public final class TokenBucket {

    private final int capacity;
    private final double refillPerNano;
    private double tokens;
    private Instant refilledAt;

    public TokenBucket(int capacity, int perMinute, Instant now) {
        this.capacity = capacity;
        this.refillPerNano = perMinute / (double) Duration.ofMinutes(1).toNanos();
        this.tokens = capacity;
        this.refilledAt = now;
    }

    /** 하나 쓸 수 있으면 쓰고 true. */
    public synchronized boolean tryTake(Instant now) {
        refill(now);
        if (tokens < 1) return false;
        tokens -= 1;
        return true;
    }

    /** 지금 가득 찼는지(오래 안 쓴 버킷 — 잊어도 같은 결과). */
    public synchronized boolean fullAt(Instant now) {
        refill(now);
        return tokens >= capacity;
    }

    private void refill(Instant now) {
        if (!now.isAfter(refilledAt)) return;
        long elapsed = Duration.between(refilledAt, now).toNanos();
        tokens = Math.min(capacity, tokens + elapsed * refillPerNano);
        refilledAt = now;
    }
}
