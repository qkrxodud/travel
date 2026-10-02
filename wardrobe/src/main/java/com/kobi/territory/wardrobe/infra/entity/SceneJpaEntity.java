package com.kobi.territory.wardrobe.infra.entity;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.wardrobe.domain.item.EquipSlot;
import com.kobi.territory.wardrobe.domain.scene.EquippedSlots;
import com.kobi.territory.wardrobe.domain.scene.Gender;
import com.kobi.territory.wardrobe.domain.scene.PropSlots;
import com.kobi.territory.wardrobe.domain.scene.Scene;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * scene — 장면 한 행(Scene). 슬롯마다 컬럼(slot_hat … slot_bg), 장식은 순서 있는 쉼표 구분 문자열(props), version 낙관적 락.
 */
@Entity
@Table(name = "scene")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SceneJpaEntity {

    @Id
    @Column(name = "explorer_id", length = 36)
    private String explorerId;

    @Column(nullable = false, length = 1)
    private String gender;

    @Column(name = "slot_hat", length = 60)
    private String slotHat;

    @Column(name = "slot_hand", length = 60)
    private String slotHand;

    @Column(name = "slot_badge", length = 60)
    private String slotBadge;

    @Column(name = "slot_bag", length = 60)
    private String slotBag;

    @Column(name = "slot_pet", length = 60)
    private String slotPet;

    @Column(name = "slot_bg", length = 60)
    private String slotBg;

    @Column(nullable = false, length = 200)
    private String props;

    @Version
    private Long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static SceneJpaEntity from(Scene scene) {
        SceneJpaEntity entity = new SceneJpaEntity();
        entity.explorerId = scene.explorerId().value();
        entity.apply(scene);
        return entity;
    }

    public void apply(Scene scene) {
        EquippedSlots slots = scene.equippedSlots();
        this.gender = scene.gender().name();
        this.slotHat = slots.itemAt(EquipSlot.HAT).orElse(null);
        this.slotHand = slots.itemAt(EquipSlot.HAND).orElse(null);
        this.slotBadge = slots.itemAt(EquipSlot.BADGE).orElse(null);
        this.slotBag = slots.itemAt(EquipSlot.BAG).orElse(null);
        this.slotPet = slots.itemAt(EquipSlot.PET).orElse(null);
        this.slotBg = slots.itemAt(EquipSlot.BG).orElse(null);
        this.props = String.join(",", scene.propSlots().itemIds());
        this.updatedAt = scene.updatedAt();
    }

    public Scene toDomain() {
        Map<EquipSlot, String> slots = new EnumMap<>(EquipSlot.class);
        slots.put(EquipSlot.HAT, slotHat);
        slots.put(EquipSlot.HAND, slotHand);
        slots.put(EquipSlot.BADGE, slotBadge);
        slots.put(EquipSlot.BAG, slotBag);
        slots.put(EquipSlot.PET, slotPet);
        slots.put(EquipSlot.BG, slotBg);
        List<String> propIds = props == null || props.isBlank() ? List.of() : Arrays.asList(props.split(","));
        return Scene.restore(ExplorerId.of(explorerId), Gender.valueOf(gender), EquippedSlots.of(slots),
            PropSlots.of(propIds), updatedAt);
    }
}
