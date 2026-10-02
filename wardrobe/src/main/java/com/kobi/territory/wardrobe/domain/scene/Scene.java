package com.kobi.territory.wardrobe.domain.scene;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.wardrobe.domain.WardrobeError;
import com.kobi.territory.wardrobe.domain.item.EquipSlot;
import com.kobi.territory.wardrobe.domain.item.ItemSpec;
import com.kobi.territory.wardrobe.domain.item.ItemSpecs;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 장면(꾸미기) 애그리거트(explorerId): 성별, 슬롯별 착용, 장식 3칸.
 *
 * 불변식
 * - 사용자가 입히는 아이템은 가방(Inventory)에 있어야 하고, 아이템 슬롯과 장착 슬롯이 같아야 한다(장식은 PROP 아이템만).
 * - 한 슬롯에 하나, 장식 ≤ {@link PropSlots#MAX}, 같은 장식 중복 없음.
 * - 회수된 아이템(ItemRevoked)은 벗긴다. 새로 얻은 아이템(ItemGranted)은 빈 슬롯이거나 지금 입은 것보다 희귀하면 자동 착용.
 * Inventory 와 분리한 이유: 보유는 탐험 파생(이벤트), 착용은 사용자 선택 — 변경 주체·빈도가 다르다(§2-6).
 */
public final class Scene {

    private final ExplorerId explorerId;
    private final EquippedSlots equippedSlots;
    private PropSlots propSlots;
    private Gender gender;
    private Instant updatedAt;

    private Scene(ExplorerId explorerId, Gender gender, EquippedSlots equippedSlots, PropSlots propSlots, Instant updatedAt) {
        this.explorerId = Objects.requireNonNull(explorerId, "explorerId");
        this.gender = Objects.requireNonNull(gender, "gender");
        this.equippedSlots = Objects.requireNonNull(equippedSlots, "equippedSlots");
        this.propSlots = Objects.requireNonNull(propSlots, "propSlots");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /** 아무것도 입지 않은 기본 장면(남성 여행자). */
    public static Scene blank(ExplorerId explorerId, Instant at) {
        return new Scene(explorerId, Gender.M, EquippedSlots.empty(), PropSlots.empty(), at);
    }

    public static Scene restore(ExplorerId explorerId, Gender gender, EquippedSlots equippedSlots, PropSlots propSlots,
                                Instant updatedAt) {
        return new Scene(explorerId, gender, equippedSlots, propSlots, updatedAt);
    }

    // ---- 커맨드 ----------------------------------------------------------------------------------------------

    /**
     * 사용자 편집(PUT /scene): 성별 → 벗기 → 입기 → 장식 칸. 입히는 아이템은 모두 검증한다(가방에 있는지, 슬롯이 맞는지).
     * @param itemSpecs 편집에 나온 아이템들의 사양
     */
    public Optional<SceneUpdate> edit(SceneEdit sceneEdit, ItemSpecs itemSpecs, Holdings holdings, Instant at) {
        sceneEdit.equip().forEach((slot, itemId) -> requireWearable(itemSpecs.require(itemId), slot, holdings));
        Optional<PropSlots> nextProps = Optional.ofNullable(sceneEdit.props()).map(itemIds -> props(itemIds, itemSpecs, holdings));
        boolean changed = sceneEdit.gender() != null && sceneEdit.gender() != gender;
        gender = sceneEdit.gender() == null ? gender : sceneEdit.gender();
        for (EquipSlot slot : sceneEdit.unequip()) changed |= equippedSlots.clear(slot);
        for (var equip : sceneEdit.equip().entrySet()) changed |= equippedSlots.put(equip.getKey(), equip.getValue());
        if (nextProps.isPresent() && !nextProps.get().equals(propSlots)) {
            propSlots = nextProps.get();
            changed = true;
        }
        return settle(changed, at);
    }

    /** 새로 얻은 아이템 자동 착용(ItemGranted): 장식은 빈 칸이 있으면, 그 밖은 빈 슬롯이거나 지금 것보다 희귀하면. */
    public Optional<SceneUpdate> autoEquip(ItemSpec item, ItemSpecs wornSpecs, Instant at) {
        if (item.slot().prop()) {
            boolean placed = !propSlots.contains(item.itemId()) && !propSlots.full();
            if (placed) propSlots = propSlots.with(item.itemId());
            return settle(placed, at);
        }
        EquipSlot slot = item.slot().equipSlot().orElseThrow();
        boolean better = equippedSlots.itemAt(slot)
            .map(current -> wornSpecs.find(current).map(item::rarerThan).orElse(true))
            .orElse(true);
        return settle(better && equippedSlots.put(slot, item.itemId()), at);
    }

    /** 회수된 아이템을 벗긴다(ItemRevoked). 입고 있지 않으면 no-op. */
    public Optional<SceneUpdate> takeOff(String itemId, Instant at) {
        boolean changed = equippedSlots.takeOff(itemId);
        if (propSlots.contains(itemId)) {
            propSlots = propSlots.without(itemId);
            changed = true;
        }
        return settle(changed, at);
    }

    /**
     * 재계산 정리: 가방에 없는 아이템만 벗긴다(착용 선택은 사용자 몫이라 그 밖은 그대로 둔다).
     */
    public Optional<SceneUpdate> keepOnly(Holdings holdings, Instant at) {
        boolean changed = false;
        for (String itemId : wornItemIds()) {
            if (!holdings.owns(itemId)) changed |= takeOff(itemId, at).isPresent();
        }
        return settle(changed, at);
    }

    private static void requireWearable(ItemSpec item, EquipSlot slot, Holdings holdings) {
        if (!holdings.owns(item.itemId())) throw WardrobeError.ITEM_NOT_OWNED.exception(item.itemId());
        if (item.slot().equipSlot().filter(slot::equals).isEmpty()) {
            throw WardrobeError.SLOT_MISMATCH.exception(item.itemId(), slot);
        }
    }

    private static PropSlots props(List<String> itemIds, ItemSpecs itemSpecs, Holdings holdings) {
        itemIds.forEach(itemId -> {
            ItemSpec item = itemSpecs.require(itemId);
            if (!holdings.owns(itemId)) throw WardrobeError.ITEM_NOT_OWNED.exception(itemId);
            if (!item.slot().prop()) throw WardrobeError.SLOT_MISMATCH.exception(itemId, "PROP");
        });
        return PropSlots.of(itemIds);
    }

    private Optional<SceneUpdate> settle(boolean changed, Instant at) {
        if (!changed) return Optional.empty();
        updatedAt = at;
        return Optional.of(new SceneUpdate(at));
    }

    // ---- 조회 ----------------------------------------------------------------------------------------------

    /** 꾸미기 점수 = 착용 아이템(슬롯 + 장식) 희귀도 점수 합. 카탈로그에 없는 아이템은 0점. */
    public int stylePoints(ItemSpecs wornSpecs, StylePolicy policy) {
        return wornItemIds().stream().map(wornSpecs::find).flatMap(Optional::stream)
            .mapToInt(spec -> policy.pointsOf(spec.tier())).sum();
    }

    /** 입고 있는 아이템 id 전부(슬롯 순서 → 장식 순서). */
    public List<String> wornItemIds() {
        List<String> worn = new ArrayList<>(equippedSlots.itemIds());
        worn.addAll(propSlots.itemIds());
        return List.copyOf(worn);
    }

    public boolean wears(String itemId) {
        return equippedSlots.itemIds().contains(itemId) || propSlots.contains(itemId);
    }

    public ExplorerId explorerId() { return explorerId; }
    public Gender gender() { return gender; }
    public EquippedSlots equippedSlots() { return equippedSlots; }
    public PropSlots propSlots() { return propSlots; }
    public Instant updatedAt() { return updatedAt; }
}
