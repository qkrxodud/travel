package com.kobi.territory.wardrobe.domain.inventory;

import com.kobi.territory.wardrobe.domain.item.GrantKind;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 보유 아이템 한 개(owned_item 행 + owned_item_basis 행들). 다음 값을 계산하므로 class(class vs record 기준).
 * <p>
 * basis = 이 아이템을 지금 뒷받침하는 활성 방문(지역, 지도)들. 체크인으로 받은 아이템(지역 특산물 REGION, 기간·시·도 이슈 EVENT)은
 * 받을 때의 방문과, 그 뒤 같은 조건을 만족한 방문이 모두 근거가 된다 — 근거 방문이 모두 취소되면 회수한다(리더 결정 Q2).
 * 세트 보상·수동 지급은 근거가 없고(빈 basis) 회수하지 않는다.
 */
public final class OwnedItem {

    private final String itemId;
    private final ItemSource source;
    private final GrantKind grantKind;
    private final Instant acquiredAt;
    private final boolean favorite;
    private final Set<VisitKey> basis;

    private OwnedItem(String itemId, GrantKind grantKind, Instant acquiredAt, boolean favorite, Set<VisitKey> basis) {
        this.itemId = Objects.requireNonNull(itemId, "itemId");
        this.grantKind = Objects.requireNonNull(grantKind, "grantKind");
        this.source = ItemSource.of(grantKind);
        this.acquiredAt = Objects.requireNonNull(acquiredAt, "acquiredAt");
        this.favorite = favorite;
        this.basis = Set.copyOf(basis);
    }

    /** 방문으로 얻음(근거 = 그 방문). */
    static OwnedItem byVisit(String itemId, GrantKind grantKind, VisitKey visit, Instant at) {
        return new OwnedItem(itemId, grantKind, at, false, Set.of(visit));
    }

    /** 보상으로 얻음(근거 없음 — 회수 없음). */
    static OwnedItem asReward(String itemId, GrantKind grantKind, Instant at) {
        return new OwnedItem(itemId, grantKind, at, false, Set.of());
    }

    public static OwnedItem restore(String itemId, GrantKind grantKind, Instant acquiredAt, boolean favorite,
                                    Set<VisitKey> basis) {
        return new OwnedItem(itemId, grantKind, acquiredAt, favorite, basis);
    }

    /** 방문으로 받는 종류(지역·기간·시·도)인지 — 근거 방문이 하나도 없으면 가질 수 없다. */
    public boolean byVisit() {
        return grantKind.byVisit();
    }

    /** 지금 근거 방문이 있는지. */
    public boolean visitBacked() {
        return !basis.isEmpty();
    }

    boolean supportedBy(VisitKey visit) {
        return basis.contains(visit);
    }

    OwnedItem withSupport(VisitKey visit) {
        Set<VisitKey> next = new LinkedHashSet<>(basis);
        next.add(visit);
        return new OwnedItem(itemId, grantKind, acquiredAt, favorite, next);
    }

    OwnedItem withoutSupport(VisitKey visit) {
        Set<VisitKey> next = new LinkedHashSet<>(basis);
        next.remove(visit);
        return new OwnedItem(itemId, grantKind, acquiredAt, favorite, next);
    }

    /** 재계산용: 다시 재생할 지도의 근거를 뺀 사본. */
    OwnedItem withoutSupportOn(Set<String> mapIds) {
        Set<VisitKey> next = new LinkedHashSet<>(basis);
        next.removeIf(visit -> visit.on(mapIds));
        return new OwnedItem(itemId, grantKind, acquiredAt, favorite, next);
    }

    OwnedItem withFavorite(boolean flag) {
        return new OwnedItem(itemId, grantKind, acquiredAt, flag, basis);
    }

    /** 재계산용: 예전에 갖고 있던 아이템이면 처음 얻은 시각과 즐겨찾기를 그대로 둔다. */
    OwnedItem adoptHistory(OwnedItem previous) {
        return new OwnedItem(itemId, grantKind, previous.acquiredAt, previous.favorite, basis);
    }

    public String itemId() { return itemId; }
    public ItemSource source() { return source; }
    public GrantKind grantKind() { return grantKind; }
    public Instant acquiredAt() { return acquiredAt; }
    public boolean favorite() { return favorite; }
    public Set<VisitKey> basis() { return basis; }

    @Override
    public boolean equals(Object other) {
        return other instanceof OwnedItem owned && itemId.equals(owned.itemId) && grantKind == owned.grantKind
            && acquiredAt.equals(owned.acquiredAt) && favorite == owned.favorite && basis.equals(owned.basis);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemId, grantKind, acquiredAt, favorite, basis);
    }

    @Override
    public String toString() {
        return "OwnedItem[" + itemId + ", " + source + (favorite ? ", ★" : "") + ", basis=" + basis.size() + "]";
    }
}
