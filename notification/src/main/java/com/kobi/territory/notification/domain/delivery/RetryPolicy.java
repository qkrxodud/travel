package com.kobi.territory.notification.domain.delivery;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 발송 재시도(설정 territory.push.dispatch.*): 푸시 서비스가 잠시 받지 못하면(429·5xx·연결 실패) 지수 백오프(initial × 2^(n−1), 최대 max)로
 * 다시, 푸시 서비스가 알려 준 Retry-After 가 더 길면 그만큼. maxAttempts 번 보내고도 안 되면 FAILED.
 */
public record RetryPolicy(int maxAttempts, Duration initialBackoff, Duration maxBackoff) {

    public RetryPolicy {
        if (maxAttempts < 1) throw new IllegalArgumentException("max-attempts 는 1 이상");
        Objects.requireNonNull(initialBackoff, "initialBackoff");
        Objects.requireNonNull(maxBackoff, "maxBackoff");
        if (initialBackoff.isNegative() || initialBackoff.isZero() || maxBackoff.compareTo(initialBackoff) < 0) {
            throw new IllegalArgumentException("재시도 간격 설정이 올바르지 않다");
        }
    }

    /** attempts 번 보낸 뒤 다음에 보낼 시각. */
    public Instant nextAttemptAt(int attempts, Instant now, Duration retryAfter) {
        Duration backoff = initialBackoff.multipliedBy(1L << Math.min(Math.max(attempts - 1, 0), 20));
        Duration wait = backoff.compareTo(maxBackoff) > 0 ? maxBackoff : backoff;
        return now.plus(retryAfter.compareTo(wait) > 0 ? retryAfter : wait);
    }

    public boolean exhausted(int attempts) {
        return attempts >= maxAttempts;
    }
}
