package com.kobi.territory.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 서버 시계(8단계) — 시스템 시계에 앞으로만 미는 간격을 더한다. 간격을 바꾸는 길은 local 전용 {@code /dev/clock} 뿐이고(운영에는
 * 그 경로가 없어 간격이 늘 0), 주차·월 넘김(이번 주 미스터리 지역·스트릭·보호권)을 E2E 에서 기다리지 않고 확인하는 데 쓴다.
 * 뒤로 돌리면 이미 쌓인 처리 시각보다 앞선 체크인이 생겨 규칙이 뒤틀리므로 앞으로만 민다(되돌리기는 초기화와 함께 0으로).
 */
public class AdjustableClock extends Clock {

    private final Clock base;
    private final AtomicReference<Duration> offset;

    public AdjustableClock(Clock base) {
        this(base, new AtomicReference<>(Duration.ZERO));
    }

    private AdjustableClock(Clock base, AtomicReference<Duration> offset) {
        this.base = Objects.requireNonNull(base, "base");
        this.offset = offset;
    }

    @Override
    public ZoneId getZone() {
        return base.getZone();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new AdjustableClock(base.withZone(zone), offset);
    }

    @Override
    public Instant instant() {
        return base.instant().plus(offset.get());
    }

    /** 앞으로 민다. 음수·0 은 거절(IllegalArgumentException). */
    public Instant advance(Duration amount) {
        if (amount == null || amount.isNegative() || amount.isZero()) throw new IllegalArgumentException("시계는 앞으로만 밀 수 있어요");
        offset.updateAndGet(current -> current.plus(amount));
        return instant();
    }

    /** 지금 간격(0 = 시스템 시계 그대로). */
    public Duration offset() {
        return offset.get();
    }

    /** 시스템 시계로 되돌린다(dev 초기화와 함께 쓴다 — 쌓인 데이터의 처리 시각보다 앞으로 돌아가므로). */
    public void reset() {
        offset.set(Duration.ZERO);
    }
}
