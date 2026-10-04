package com.kobi.territory.exploration.domain.wishlist;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.domain.ExplorationError;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 가고 싶은 곳 애그리거트(explorerId, 9단계) — 탐험가 단위 계획, 비공개.
 *
 * 불변식
 * - 아직 칠하지 않은 지역(탐험가 단위로 보이는 방문이 없음)에만 핀을 꽂는다. 같은 지역은 핀 하나(다시 꽂아도 그대로).
 * - 아직 다녀오지 않은 핀은 정책 상한(maxPins)까지.
 * - 핀을 꽂은 뒤 그 지역을 어느 지도에서든 칠하면 "다녀옴"으로 바뀐다(한 번). 보상 XP 는 진행이 지역당 한 번만 준다
 *   (핀을 뺐다 다시 꽂아도 한 번 — 진행 장부 refId wish:{e}:{code}).
 */
public final class Wishlist {

    private final ExplorerId explorerId;
    private final WishPins pins;

    private Wishlist(ExplorerId explorerId, WishPins pins) {
        this.explorerId = Objects.requireNonNull(explorerId, "explorerId");
        this.pins = Objects.requireNonNull(pins, "pins");
    }

    public static Wishlist empty(ExplorerId explorerId) {
        return new Wishlist(explorerId, WishPins.empty());
    }

    public static Wishlist restore(ExplorerId explorerId, WishPins pins) {
        return new Wishlist(explorerId, pins);
    }

    /**
     * 핀을 꽂는다. 이미 꽂혀 있으면 그 핀 그대로(멱등). 칠한 지역이면 409 WISH_ALREADY_VISITED, 상한이면 422 WISHLIST_FULL.
     *
     * @param painted 이 탐험가가 그 지역을 이미 칠했는지(어느 지도든 보이는 방문)
     */
    public WishPin pin(RegionCode region, boolean painted, Instant at, WishlistPolicy policy) {
        Optional<WishPin> existing = pins.find(region);
        if (existing.isPresent()) return existing.get();
        if (painted) {
            throw ExplorationError.WISH_ALREADY_VISITED.exception(region.value());
        }
        if (pins.pendingCount() >= policy.maxPins()) {
            throw ExplorationError.WISHLIST_FULL.exception(policy.maxPins());
        }
        WishPin pin = WishPin.pinned(region, at);
        pins.put(pin);
        return pin;
    }

    /** 핀을 뺀다(다녀온 핀도). 없으면 아무것도 하지 않는다(멱등). */
    public boolean unpin(RegionCode region) {
        return pins.remove(region);
    }

    /** 이 처리 시각에 그 지역을 칠했다 — 핀을 꽂은 뒤의 체크인이고 아직 다녀오지 않았으면 다녀옴. 멱등. */
    public Optional<WishFulfillment> fulfill(RegionCode region, Instant visitedAt) {
        return fulfill(region, visitedAt, false);
    }

    /**
     * 체크인 소식 — 아직 다녀오지 않은 핀이고, 핀을 꽂은 뒤의 체크인이거나 지금 그 지역이 칠해져 있으면(paintedNow — 핀 꽂기와 거의 동시에 칠한
     * 경우) 다녀옴. 취소된 옛 체크인 소식이 늦게 와도 지금 칠해져 있지 않으면 다녀옴이 되지 않는다. 멱등.
     */
    public Optional<WishFulfillment> fulfill(RegionCode region, Instant visitedAt, boolean paintedNow) {
        return pins.find(region).filter(pin -> pin.fulfilledBy(visitedAt, paintedNow)).map(pin -> {
            WishPin done = pin.fulfilledAt(visitedAt);
            pins.put(done);
            return new WishFulfillment(region, pin.pinnedAt(), done.fulfilledAt());
        });
    }

    /**
     * 계정 병합 뒤(리더 결정 Q1): 이제 칠해진 지역(painted — 계정·익명 어느 쪽 방문이든)의 대기 핀을 다녀옴으로 — 그 사람이 실제로 다녀온 곳이다.
     * 멱등. @return 새로 다녀옴이 된 핀
     */
    public List<WishFulfillment> fulfillPainted(Set<RegionCode> painted, Instant at) {
        List<WishFulfillment> done = new ArrayList<>();
        pins.pendingRegions().stream().filter(painted::contains)
            .forEach(region -> fulfill(region, at, true).ifPresent(done::add));
        return List.copyOf(done);
    }

    /**
     * 계정 병합(ExplorerMerged): 익명 탐험가의 핀을 합친다 — 같은 지역은 하나(먼저 꽂은 시각, 어느 쪽이든 다녀왔으면 다녀옴). 상한은 입력
     * 규칙이라 병합에는 적용하지 않는다. 멱등. @return 새로 다녀옴이 된 핀(진행은 재계산으로 보상을 맞춘다)
     */
    public List<WishPin> absorb(Wishlist merged) {
        List<WishPin> newlyFulfilled = new ArrayList<>();
        merged.pins.newestFirst().forEach(theirs -> {
            WishPin next = pins.find(theirs.region()).map(mine -> mine.mergedWith(theirs)).orElse(theirs);
            Optional<WishPin> before = pins.find(theirs.region());
            if (before.isPresent() && before.get().equals(next)) return;
            if (next.fulfilled() && before.map(pin -> !pin.fulfilled()).orElse(true)) newlyFulfilled.add(next);
            pins.put(next);
        });
        return List.copyOf(newlyFulfilled);
    }

    public ExplorerId explorerId() { return explorerId; }
    public WishPins pins() { return pins; }
}
