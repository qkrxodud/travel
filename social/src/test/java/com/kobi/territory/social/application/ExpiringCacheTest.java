package com.kobi.territory.social.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** QA P3-5: 읽는 동안 무효화되면 옛 값을 넣지 않고, 키가 상한을 넘으면 정리한다. */
class ExpiringCacheTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-03T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void 읽는_동안_비워지면_그_결과는_캐시하지_않는다() {
        ExpiringCache<String, Integer> cache = new ExpiringCache<>(Duration.ofMinutes(5), CLOCK, 10);
        AtomicInteger loads = new AtomicInteger();
        assertThat(cache.get("k", () -> { cache.clear(); return loads.incrementAndGet(); })).isEqualTo(1); // 옛 스냅숏
        assertThat(cache.get("k", loads::incrementAndGet)).isEqualTo(2); // 다시 읽는다
        assertThat(cache.get("k", loads::incrementAndGet)).isEqualTo(2); // 이번엔 캐시
    }

    @Test
    void 상한을_넘으면_정리한다() {
        ExpiringCache<Integer, Integer> cache = new ExpiringCache<>(Duration.ofMinutes(5), CLOCK, 3);
        for (int i = 0; i < 10; i++) {
            int key = i;
            cache.get(key, () -> key);
        }
        assertThat(cache.size()).isLessThanOrEqualTo(3);
    }
}
