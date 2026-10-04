package com.kobi.territory.analytics.domain.ratelimit;

import static com.kobi.territory.analytics.domain.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.common.error.TerritoryException;
import java.time.Duration;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("이벤트 수집 레이트 리밋")
class IngestThrottleTest {

    private static final RateLimitPolicy POLICY = new RateLimitPolicy(3, 6, 5, 60, 100);

    private void 보낸다(IngestThrottle throttle, String visitor, String address, int times) {
        IntStream.range(0, times).forEach(i -> throttle.admit(visitor, address, NOW));
    }

    @Nested
    @DisplayName("같은 방문이 몰아서 보낼 때")
    class SameVisitor {

        @Test
        @DisplayName("한꺼번에 정해진 수까지만 받고 그다음은 거절한다")
        void burst() {
            IngestThrottle throttle = new IngestThrottle(POLICY);
            보낸다(throttle, "visitor-a", "addr-1", 3);

            assertThatThrownBy(() -> throttle.admit("visitor-a", "addr-1", NOW)).isInstanceOfSatisfying(TerritoryException.class,
                rejected -> assertThat(rejected.code()).isEqualTo("EVENTS_RATE_LIMITED"));
        }

        @Test
        @DisplayName("시간이 지나면 분당 정해진 만큼 다시 보낼 수 있다")
        void refill() {
            IngestThrottle throttle = new IngestThrottle(POLICY);
            보낸다(throttle, "visitor-a", "addr-1", 3);

            throttle.admit("visitor-a", "addr-1", NOW.plus(Duration.ofSeconds(10)));
        }

        @Test
        @DisplayName("다른 방문은 따로 센다")
        void otherVisitor() {
            IngestThrottle throttle = new IngestThrottle(POLICY);
            보낸다(throttle, "visitor-a", "addr-1", 3);

            throttle.admit("visitor-b", "addr-1", NOW);
        }
    }

    @Nested
    @DisplayName("한 주소가 방문 ID 를 바꿔 가며 보낼 때")
    class SameAddress {

        @Test
        @DisplayName("주소 상한에서 막힌다")
        void addressLimit() {
            IngestThrottle throttle = new IngestThrottle(POLICY);
            IntStream.range(0, 5).forEach(i -> throttle.admit("visitor-" + i, "addr-1", NOW));

            assertThatThrownBy(() -> throttle.admit("visitor-new", "addr-1", NOW)).isInstanceOf(TerritoryException.class);
        }
    }

    @Nested
    @DisplayName("기억하는 열쇠가 상한에 닿았을 때")
    class Memory {

        @Test
        @DisplayName("한동안 안 쓴(가득 찬) 버킷부터 잊고 새 열쇠를 받는다")
        void forgetIdle() {
            TokenBuckets buckets = new TokenBuckets(2, 60, 2);
            buckets.tryTake("a", NOW);
            buckets.tryTake("b", NOW);

            assertThat(buckets.tryTake("c", NOW.plus(Duration.ofMinutes(1)))).isTrue();
            assertThat(buckets.trackedKeys()).isEqualTo(1);
        }

        @Test
        @DisplayName("모두 쓰는 중이면 새 열쇠는 거절한다 — 메모리를 지킨다")
        void refuseWhenBusy() {
            TokenBuckets buckets = new TokenBuckets(2, 60, 2);
            buckets.tryTake("a", NOW);
            buckets.tryTake("b", NOW);

            assertThat(buckets.tryTake("c", NOW)).isFalse();
        }
    }
}
