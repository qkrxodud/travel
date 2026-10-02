package com.kobi.territory.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 결정 5(R2-3): 낙관적 락 충돌 재시도는 횟수 상한에 세지 않지만 첫 충돌부터 시간 상한을 넘기면 FAILED. */
class OutboxDeliveryEntityTest {

    static final Instant T0 = Instant.parse("2026-10-03T00:00:00Z");
    static final Duration LIMIT = Duration.ofMinutes(10);

    @Test
    void 충돌이_시간_상한_안이면_계속_재시도하고_넘기면_FAILED() {
        OutboxDeliveryEntity delivery = new OutboxDeliveryEntity(1L, "progression.progress", T0);
        for (int minute = 0; minute < 10; minute++) {
            assertThat(delivery.markConflict("충돌", Duration.ofSeconds(1), LIMIT, T0.plus(Duration.ofMinutes(minute)))).isFalse();
        }
        assertThat(delivery.failed()).isFalse();
        assertThat(delivery.attempts()).isZero(); // 충돌은 시도 횟수에 세지 않는다
        assertThat(delivery.markConflict("충돌", Duration.ofSeconds(1), LIMIT, T0.plus(LIMIT))).isTrue();
        assertThat(delivery.failed()).isTrue();
        assertThat(delivery.conflicts()).isEqualTo(11);
    }

    @Test
    void 성공하거나_재전달하면_충돌_시계가_처음부터() {
        OutboxDeliveryEntity delivery = new OutboxDeliveryEntity(1L, "progression.progress", T0);
        delivery.markConflict("충돌", Duration.ofSeconds(1), LIMIT, T0);
        delivery.markConflict("충돌", Duration.ofSeconds(1), LIMIT, T0.plus(LIMIT)); // FAILED
        delivery.redeliver(T0.plus(LIMIT));
        assertThat(delivery.failed()).isFalse();
        assertThat(delivery.markConflict("충돌", Duration.ofSeconds(1), LIMIT, T0.plus(LIMIT).plusSeconds(1))).isFalse();
        delivery.markDelivered(T0.plus(LIMIT).plusSeconds(2));
        assertThat(delivery.delivered()).isTrue();
    }
}
