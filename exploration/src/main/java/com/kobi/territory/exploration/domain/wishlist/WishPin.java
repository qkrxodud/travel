package com.kobi.territory.exploration.domain.wishlist;

import com.kobi.territory.common.model.RegionCode;
import java.time.Instant;
import java.util.Objects;

/**
 * 가고 싶은 곳 핀 하나(wish_pin 행). fulfilledAt 이 있으면 "다녀옴"(핀을 꽂은 뒤 그 지역을 칠함). 다음 상태(다녀옴)를 계산하므로 class.
 */
public final class WishPin {

    private final RegionCode region;
    private final Instant pinnedAt;
    private final Instant fulfilledAt;

    private WishPin(RegionCode region, Instant pinnedAt, Instant fulfilledAt) {
        this.region = Objects.requireNonNull(region, "region");
        this.pinnedAt = Objects.requireNonNull(pinnedAt, "pinnedAt");
        this.fulfilledAt = fulfilledAt;
    }

    static WishPin pinned(RegionCode region, Instant at) {
        return new WishPin(region, at, null);
    }

    public static WishPin restore(RegionCode region, Instant pinnedAt, Instant fulfilledAt) {
        return new WishPin(region, pinnedAt, fulfilledAt);
    }

    public boolean fulfilled() {
        return fulfilledAt != null;
    }

    /**
     * 이 처리 시각의 체크인이 이 핀을 이루는지 — 아직 다녀오지 않았고, 핀을 꽂은 뒤에 칠했거나 지금 그 지역이 칠해져 있다(핀 꽂기와 체크인이 거의
     * 동시라 체크인 처리 시각이 핀보다 앞서도 지금 칠해져 있으면 다녀옴 — 칠한 곳에 대기 핀이 남지 않게, QA P3-2).
     */
    boolean fulfilledBy(Instant visitedAt, boolean paintedNow) {
        return !fulfilled() && (paintedNow || !visitedAt.isBefore(pinnedAt));
    }

    /** 다녀온 시각 — 핀을 꽂은 시각보다 앞서지 않는다. */
    WishPin fulfilledAt(Instant at) {
        return new WishPin(region, pinnedAt, at.isBefore(pinnedAt) ? pinnedAt : at);
    }

    /** 병합: 두 핀을 하나로 — 먼저 꽂은 시각, 다녀옴은 어느 쪽이든 다녀왔으면 가장 이른 시각. */
    WishPin mergedWith(WishPin other) {
        Instant pinned = pinnedAt.isBefore(other.pinnedAt) ? pinnedAt : other.pinnedAt;
        Instant done = fulfilledAt == null ? other.fulfilledAt
            : other.fulfilledAt == null || fulfilledAt.isBefore(other.fulfilledAt) ? fulfilledAt : other.fulfilledAt;
        return new WishPin(region, pinned, done);
    }

    public RegionCode region() { return region; }
    public Instant pinnedAt() { return pinnedAt; }
    public Instant fulfilledAt() { return fulfilledAt; }

    @Override
    public boolean equals(Object other) {
        return other instanceof WishPin pin && region.equals(pin.region) && pinnedAt.equals(pin.pinnedAt)
            && Objects.equals(fulfilledAt, pin.fulfilledAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(region, pinnedAt, fulfilledAt);
    }
}
