package com.kobi.territory.outbox;

import java.time.Duration;
import java.util.random.RandomGenerator;

/**
 * 재시도 간격: 지수 백오프(initial × 2^(n−1), max 로 자름) + 지터(그 값의 50~100% 중 무작위).
 * 값은 territory.outbox.relay.backoff.* 설정에서 온다.
 */
record RetryBackoff(Duration initial, Duration max, RandomGenerator random) {

    Duration delay(int retryNumber) {
        long base = initial.toMillis() << Math.min(Math.max(retryNumber - 1, 0), 20);
        long capped = Math.min(base, max.toMillis());
        long jittered = capped / 2 + (long) (random.nextDouble() * (capped - capped / 2 + 1));
        return Duration.ofMillis(Math.max(1, jittered));
    }
}
