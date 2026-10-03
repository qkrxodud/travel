package com.kobi.territory.wardrobe.domain.inventory;

import com.kobi.territory.wardrobe.domain.WardrobeError;
import com.kobi.territory.wardrobe.domain.item.GrantKind;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 일급 컬렉션: 보유 아이템. 같은 itemId 는 한 번만(지급 재전달이 no-op). 저장소가 바뀐 행만 쓰도록 복원 이후의
 * 추가·변경(근거·즐겨찾기)·삭제를 기억한다({@link #added()}·{@link #updated()}·{@link #removed()}).
 */
public final class OwnedItems {

    private final Map<String, OwnedItem> byId = new LinkedHashMap<>();
    private final Set<String> added = new LinkedHashSet<>();
    private final Set<String> updated = new LinkedHashSet<>();
    private final Set<String> removed = new LinkedHashSet<>();

    private OwnedItems(Collection<OwnedItem> restored) {
        restored.forEach(item -> {
            if (byId.put(item.itemId(), item) != null) throw new IllegalStateException("보유 아이템 중복: " + item.itemId());
        });
    }

    public static OwnedItems empty() {
        return new OwnedItems(List.of());
    }

    public static OwnedItems of(Collection<OwnedItem> restored) {
        return new OwnedItems(restored);
    }

    /**
     * 방문으로 받는 아이템: 없으면 그 방문을 근거로 얻고, 이미 방문 근거로 갖고 있으면 근거에 이 방문을 더한다(같은 조건을 만족하는
     * 다른 방문 — 하나가 취소돼도 유지되게). @return 새로 얻었으면 그 아이템
     */
    Optional<OwnedItem> grantByVisit(String itemId, GrantKind grantKind, VisitKey visit, Instant at) {
        OwnedItem current = byId.get(itemId);
        if (current == null) return Optional.of(put(OwnedItem.byVisit(itemId, grantKind, visit, at)));
        if (current.byVisit() && !current.supportedBy(visit)) replace(current.withSupport(visit));
        return Optional.empty();
    }

    /** 보상(근거 없음, 회수 없음). 이미 있으면 no-op. */
    Optional<OwnedItem> grantReward(String itemId, GrantKind grantKind, Instant at) {
        if (byId.containsKey(itemId)) return Optional.empty();
        return Optional.of(put(OwnedItem.asReward(itemId, grantKind, at)));
    }

    /** 병합된 탐험가의 재생 불가 아이템(수동·초대 보상)을 받는다 — 처음 얻은 시각 유지, 이미 있으면 no-op. @return 새로 얻은 것 */
    List<OwnedItem> adoptUnreplayable(OwnedItems merged) {
        List<OwnedItem> adopted = new ArrayList<>();
        merged.byId.values().stream().filter(item -> !item.grantKind().replayable() && !byId.containsKey(item.itemId()))
            .forEach(item -> adopted.add(put(OwnedItem.asReward(item.itemId(), item.grantKind(), item.acquiredAt()))));
        return adopted;
    }

    /** 방문이 취소됐다 — 그 방문을 근거에서 빼고, 근거가 남지 않은 아이템을 회수한다. @return 회수한 것 */
    List<OwnedItem> withdraw(VisitKey visit) {
        List<OwnedItem> revoked = new ArrayList<>();
        byId.values().stream().filter(item -> item.supportedBy(visit)).toList().forEach(item -> {
            OwnedItem next = item.withoutSupport(visit);
            if (next.visitBacked()) replace(next);
            else {
                remove(item.itemId());
                revoked.add(item);
            }
        });
        return revoked;
    }

    /** 즐겨찾기 표시. 가방에 없으면 422 ITEM_NOT_OWNED(정의는 있어도 가방에 없을 수 있다). */
    OwnedItem markFavorite(String itemId, boolean favorite) {
        OwnedItem current = find(itemId).orElseThrow(() -> WardrobeError.ITEM_NOT_OWNED.exception(itemId));
        return replace(current.withFavorite(favorite));
    }

    /**
     * 재계산 출발점: 다시 재생할 지도의 근거를 빼고, 근거가 남지 않은 방문형 아이템(지역·기간·시·도)은 뺀다 — 재생이 다시 주거나,
     * 근거가 처음부터 없던 손상 데이터면 정리된다(QA P3-R2-2). 세트 보상·수동 지급은 근거 없이 유지.
     */
    OwnedItems rebuildBase(Set<String> replayableMaps) {
        List<OwnedItem> kept = new ArrayList<>();
        byId.values().forEach(item -> {
            OwnedItem next = item.withoutSupportOn(replayableMaps);
            if (!next.byVisit() || next.visitBacked()) kept.add(next);
        });
        return new OwnedItems(kept);
    }

    /** 재계산 마무리: 예전에도 갖고 있던 아이템은 처음 얻은 시각·즐겨찾기를 유지한다. */
    void adoptHistory(OwnedItems previous) {
        List.copyOf(byId.values()).forEach(item -> previous.find(item.itemId())
            .ifPresent(before -> byId.put(item.itemId(), item.adoptHistory(before))));
    }

    private OwnedItem put(OwnedItem item) {
        byId.put(item.itemId(), item);
        if (removed.remove(item.itemId())) updated.add(item.itemId());
        else added.add(item.itemId());
        return item;
    }

    private OwnedItem replace(OwnedItem item) {
        byId.put(item.itemId(), item);
        if (!added.contains(item.itemId())) updated.add(item.itemId());
        return item;
    }

    private void remove(String itemId) {
        byId.remove(itemId);
        updated.remove(itemId);
        if (!added.remove(itemId)) removed.add(itemId);
    }

    public boolean owns(String itemId) {
        return byId.containsKey(itemId);
    }

    public Optional<OwnedItem> find(String itemId) {
        return Optional.ofNullable(byId.get(itemId));
    }

    public int count() {
        return byId.size();
    }

    /** 보유 아이템 id(착용 검증용 사본). */
    public Set<String> itemIds() {
        return Set.copyOf(byId.keySet());
    }

    /** 최근에 얻은 순(같으면 id 순). */
    public List<OwnedItem> newestFirst() {
        return byId.values().stream()
            .sorted(Comparator.comparing(OwnedItem::acquiredAt).reversed().thenComparing(OwnedItem::itemId))
            .toList();
    }

    /** 전부(재계산 저장 — 저장소가 통째로 다시 넣는다). */
    public List<OwnedItem> all() {
        return List.copyOf(byId.values());
    }

    /** 복원 이후 새로 생긴 아이템(저장소가 insert). */
    public List<OwnedItem> added() {
        return added.stream().map(byId::get).toList();
    }

    /** 복원 이후 값(근거·즐겨찾기)이 바뀐 아이템(저장소가 update). */
    public List<OwnedItem> updated() {
        return updated.stream().map(byId::get).toList();
    }

    /** 복원 이후 회수된 아이템 id(저장소가 delete). */
    public Set<String> removed() {
        return Set.copyOf(removed);
    }
}
