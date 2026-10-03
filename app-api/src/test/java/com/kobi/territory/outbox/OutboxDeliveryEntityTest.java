package com.kobi.territory.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 회귀 출처 3단계 결정 5(R2-3): 낙관적 락 충돌 재시도는 횟수 상한에 세지 않지만 첫 충돌부터 시간 상한을 넘기면 FAILED. */
@DisplayName("소식 전달 기록")
class OutboxDeliveryEntityTest {

    static final Instant T0 = Instant.parse("2026-10-03T00:00:00Z");
    static final Duration LIMIT = Duration.ofMinutes(10);

    private static OutboxDeliveryEntity delivery() {
        return new OutboxDeliveryEntity(1L, "progression.progress", T0);
    }

    @Nested
    @DisplayName("받는 쪽이 같은 기록을 동시에 고쳐 처리가 겹칠 때")
    class Conflicts {

        @Test
        @DisplayName("첫 겹침부터 10분 동안은 실패로 세지 않고 계속 다시 보낸다")
        void retriedWithinTimeLimit() {
            OutboxDeliveryEntity delivery = delivery();
            for (int minute = 0; minute < 10; minute++) {
                assertThat(delivery.markConflict("충돌", Duration.ofSeconds(1), LIMIT, T0.plus(Duration.ofMinutes(minute)))).isFalse();
            }
            assertThat(delivery.failed()).isFalse();
            assertThat(delivery.attempts()).isZero();
        }

        @Test
        @DisplayName("10분을 넘기면 멈추고 다시 보내 달라는 요청을 기다린다")
        void stopsAfterTimeLimit() {
            OutboxDeliveryEntity delivery = delivery();
            for (int minute = 0; minute < 10; minute++) {
                delivery.markConflict("충돌", Duration.ofSeconds(1), LIMIT, T0.plus(Duration.ofMinutes(minute)));
            }
            assertThat(delivery.markConflict("충돌", Duration.ofSeconds(1), LIMIT, T0.plus(LIMIT))).isTrue();
            assertThat(delivery.failed()).isTrue();
            assertThat(delivery.conflicts()).isEqualTo(11);
        }
    }

    @Nested
    @DisplayName("멈춘 전달을 다시 보내면")
    class Redelivery {

        @Test
        @DisplayName("겹침 시간을 처음부터 다시 잰다")
        void conflictClockRestarts() {
            OutboxDeliveryEntity delivery = delivery();
            delivery.markConflict("충돌", Duration.ofSeconds(1), LIMIT, T0);
            delivery.markConflict("충돌", Duration.ofSeconds(1), LIMIT, T0.plus(LIMIT));
            delivery.redeliver(T0.plus(LIMIT));
            assertThat(delivery.failed()).isFalse();
            assertThat(delivery.markConflict("충돌", Duration.ofSeconds(1), LIMIT, T0.plus(LIMIT).plusSeconds(1))).isFalse();
        }

        @Test
        @DisplayName("성공하면 전달 완료로 남는다")
        void deliveredAfterRedelivery() {
            OutboxDeliveryEntity delivery = delivery();
            delivery.markConflict("충돌", Duration.ofSeconds(1), LIMIT, T0);
            delivery.markConflict("충돌", Duration.ofSeconds(1), LIMIT, T0.plus(LIMIT));
            delivery.redeliver(T0.plus(LIMIT));
            delivery.markDelivered(T0.plus(LIMIT).plusSeconds(2));
            assertThat(delivery.delivered()).isTrue();
        }
    }
}
