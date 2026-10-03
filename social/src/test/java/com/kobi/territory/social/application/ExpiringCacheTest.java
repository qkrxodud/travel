package com.kobi.territory.social.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 회귀 출처: 5단계 QA P3-5(통계 캐시 — 읽는 동안 새 통계로 바뀐 경우·쌓이는 키). */
@DisplayName("순위 통계 보관")
class ExpiringCacheTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-03T00:00:00Z"), ZoneOffset.UTC);

    @Nested
    @DisplayName("읽는 도중에 새 통계가 나오면")
    class ClearedWhileLoading {

        @Test
        @DisplayName("그때 읽은 옛 통계는 보관하지 않고 다음에 다시 읽는다")
        void doesNotKeepStale() {
            ExpiringCache<String, Integer> cache = new ExpiringCache<>(Duration.ofMinutes(5), CLOCK, 10);
            AtomicInteger loads = new AtomicInteger();

            assertThat(cache.get("k", () -> { cache.clear(); return loads.incrementAndGet(); })).isEqualTo(1);
            assertThat(cache.get("k", loads::incrementAndGet)).isEqualTo(2);
        }

        @Test
        @DisplayName("다시 읽은 새 통계는 보관해 두고 쓴다")
        void keepsFresh() {
            ExpiringCache<String, Integer> cache = new ExpiringCache<>(Duration.ofMinutes(5), CLOCK, 10);
            AtomicInteger loads = new AtomicInteger();
            cache.get("k", () -> { cache.clear(); return loads.incrementAndGet(); });
            cache.get("k", loads::incrementAndGet);

            assertThat(cache.get("k", loads::incrementAndGet)).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("보관 기간이 지나면 다시 읽는다")
    void expires() {
        MovableClock clock = new MovableClock();
        ExpiringCache<String, Integer> cache = new ExpiringCache<>(Duration.ofMinutes(5), clock, 10);
        AtomicInteger loads = new AtomicInteger();
        cache.get("k", loads::incrementAndGet);

        clock.now = clock.now.plus(Duration.ofMinutes(5));

        assertThat(cache.get("k", loads::incrementAndGet)).isEqualTo(2);
    }

    @Test
    @DisplayName("보관한 사람 수가 상한을 넘으면 정리한다")
    void evictsOverLimit() {
        ExpiringCache<Integer, Integer> cache = new ExpiringCache<>(Duration.ofMinutes(5), CLOCK, 3);
        for (int i = 0; i < 10; i++) {
            int key = i;
            cache.get(key, () -> key);
        }

        assertThat(cache.size()).isLessThanOrEqualTo(3);
    }

    static final class MovableClock extends Clock {
        Instant now = Instant.parse("2026-10-03T00:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
