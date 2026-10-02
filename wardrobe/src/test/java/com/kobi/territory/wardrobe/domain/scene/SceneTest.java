package com.kobi.territory.wardrobe.domain.scene;

import static com.kobi.territory.wardrobe.domain.Fixtures.EXPLORER;
import static com.kobi.territory.wardrobe.domain.Fixtures.STYLE;
import static com.kobi.territory.wardrobe.domain.Fixtures.T0;
import static com.kobi.territory.wardrobe.domain.Fixtures.at;
import static com.kobi.territory.wardrobe.domain.Fixtures.regionItem;
import static com.kobi.territory.wardrobe.domain.Fixtures.setBackground;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.common.error.TerritoryException;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.wardrobe.domain.item.EquipSlot;
import com.kobi.territory.wardrobe.domain.item.ItemSlot;
import com.kobi.territory.wardrobe.domain.item.ItemSpec;
import com.kobi.territory.wardrobe.domain.item.ItemSpecs;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** D1: Scene — 슬롯 일치, 장식 ≤3, 보유 아이템만, autoEquip 희귀도, 회수 시 벗김, 꾸미기 점수. */
class SceneTest {

    static final ItemSpec LANTERN = regionItem("KR-11010", ItemSlot.HAND, Rarity.COMMON);
    static final ItemSpec CAMERA = regionItem("KR-42720", ItemSlot.HAND, Rarity.RARE);
    static final ItemSpec MAP = regionItem("KR-11020", ItemSlot.HAND, Rarity.COMMON);
    static final ItemSpec CAP = regionItem("KR-11030", ItemSlot.HAT, Rarity.COMMON);
    static final ItemSpec ULLEUNG = regionItem("KR-37430", ItemSlot.BG, Rarity.LEGEND);
    static final ItemSpec PARASOL = regionItem("KR-26350", ItemSlot.PROP, Rarity.COMMON);
    static final ItemSpec STATUE = regionItem("KR-50110", ItemSlot.PROP, Rarity.RARE);
    static final ItemSpec VASE = regionItem("KR-38390", ItemSlot.PROP, Rarity.COMMON);
    static final ItemSpec TREE = regionItem("KR-36350", ItemSlot.PROP, Rarity.COMMON);
    static final ItemSpecs ALL = ItemSpecs.of(List.of(LANTERN, CAMERA, MAP, CAP, ULLEUNG, PARASOL, STATUE, VASE, TREE,
        setBackground("han")));
    static final Holdings OWNS_ALL = Holdings.of(List.of(LANTERN.itemId(), CAMERA.itemId(), MAP.itemId(), CAP.itemId(),
        ULLEUNG.itemId(), PARASOL.itemId(), STATUE.itemId(), VASE.itemId(), TREE.itemId()));

    static SceneEdit equip(EquipSlot slot, ItemSpec item) {
        return new SceneEdit(null, Map.of(slot, item.itemId()), null, null);
    }

    @Test
    void 자동_착용은_빈_슬롯이거나_더_희귀할_때만() {
        Scene scene = Scene.blank(EXPLORER, T0);
        assertThat(scene.autoEquip(LANTERN, ALL, at(1))).isPresent();
        assertThat(scene.autoEquip(MAP, ALL, at(2))).isEmpty(); // 같은 희귀도는 바꾸지 않는다
        assertThat(scene.autoEquip(CAMERA, ALL, at(3))).isPresent();
        assertThat(scene.autoEquip(LANTERN, ALL, at(4))).isEmpty();
        assertThat(scene.equippedSlots().itemAt(EquipSlot.HAND)).contains(CAMERA.itemId());
    }

    @Test
    void 자동_착용_장식은_빈_칸이_있을_때만_3개까지() {
        Scene scene = Scene.blank(EXPLORER, T0);
        assertThat(scene.autoEquip(PARASOL, ALL, at(1))).isPresent();
        assertThat(scene.autoEquip(PARASOL, ALL, at(1))).isEmpty();
        scene.autoEquip(STATUE, ALL, at(2));
        scene.autoEquip(VASE, ALL, at(3));
        assertThat(scene.autoEquip(TREE, ALL, at(4))).isEmpty();
        assertThat(scene.propSlots().itemIds()).containsExactly(PARASOL.itemId(), STATUE.itemId(), VASE.itemId());
    }

    @Test
    void 입히려면_가방에_있어야_하고_슬롯이_맞아야_한다() {
        Scene scene = Scene.blank(EXPLORER, T0);
        assertThatThrownBy(() -> scene.edit(equip(EquipSlot.HAND, LANTERN), ALL, Holdings.of(List.of()), at(1)))
            .isInstanceOf(TerritoryException.class).hasFieldOrPropertyWithValue("code", "ITEM_NOT_OWNED");
        assertThatThrownBy(() -> scene.edit(equip(EquipSlot.HAT, LANTERN), ALL, OWNS_ALL, at(1)))
            .isInstanceOf(TerritoryException.class).hasFieldOrPropertyWithValue("code", "SLOT_MISMATCH");
        assertThatThrownBy(() -> scene.edit(new SceneEdit(null, null, null, List.of(LANTERN.itemId())), ALL, OWNS_ALL, at(1)))
            .isInstanceOf(TerritoryException.class).hasFieldOrPropertyWithValue("code", "SLOT_MISMATCH");
        assertThatThrownBy(() -> scene.edit(equip(EquipSlot.HAND, regionItem("KR-99999", ItemSlot.HAND, Rarity.COMMON)),
            ALL, OWNS_ALL, at(1)))
            .isInstanceOf(TerritoryException.class).hasFieldOrPropertyWithValue("code", "ITEM_NOT_FOUND");
        assertThat(scene.wornItemIds()).isEmpty();
    }

    @Test
    void 장식은_3개까지_중복_없이() {
        Scene scene = Scene.blank(EXPLORER, T0);
        assertThatThrownBy(() -> scene.edit(new SceneEdit(null, null, null,
            List.of(PARASOL.itemId(), STATUE.itemId(), VASE.itemId(), TREE.itemId())), ALL, OWNS_ALL, at(1)))
            .isInstanceOf(TerritoryException.class).hasFieldOrPropertyWithValue("code", "TOO_MANY_PROPS");
        assertThatThrownBy(() -> scene.edit(new SceneEdit(null, null, null, List.of(PARASOL.itemId(), PARASOL.itemId())),
            ALL, OWNS_ALL, at(1)))
            .isInstanceOf(TerritoryException.class).hasFieldOrPropertyWithValue("code", "DUPLICATE_PROP");
    }

    @Test
    void 편집은_성별_벗기_입기_장식을_한_번에_적용하고_바뀐_것이_없으면_결과가_없다() {
        Scene scene = Scene.blank(EXPLORER, T0);
        scene.autoEquip(LANTERN, ALL, at(1));
        SceneEdit edit = new SceneEdit(Gender.F, Map.of(EquipSlot.HAT, CAP.itemId(), EquipSlot.BG, ULLEUNG.itemId()),
            Set.of(EquipSlot.HAND), List.of(STATUE.itemId()));

        assertThat(scene.edit(edit, ALL, OWNS_ALL, at(2))).isPresent();
        assertThat(scene.gender()).isEqualTo(Gender.F);
        assertThat(scene.equippedSlots().itemAt(EquipSlot.HAND)).isEmpty();
        assertThat(scene.wornItemIds()).containsExactly(CAP.itemId(), ULLEUNG.itemId(), STATUE.itemId());
        assertThat(scene.edit(edit, ALL, OWNS_ALL, at(3))).isEmpty();
        assertThat(scene.updatedAt()).isEqualTo(at(2));
    }

    @Test
    void 회수된_아이템은_슬롯과_장식에서_벗긴다() {
        Scene scene = Scene.blank(EXPLORER, T0);
        scene.autoEquip(LANTERN, ALL, at(1));
        scene.autoEquip(PARASOL, ALL, at(1));

        assertThat(scene.takeOff(LANTERN.itemId(), at(2))).isPresent();
        assertThat(scene.takeOff(PARASOL.itemId(), at(2))).isPresent();
        assertThat(scene.takeOff(CAP.itemId(), at(2))).isEmpty();
        assertThat(scene.wornItemIds()).isEmpty();
    }

    @Test
    void 꾸미기_점수는_착용_아이템_희귀도_점수_합() {
        Scene scene = Scene.blank(EXPLORER, T0);
        scene.autoEquip(CAMERA, ALL, at(1));    // 희귀 3
        scene.autoEquip(CAP, ALL, at(1));       // 일반 1
        scene.autoEquip(setBackground("han"), ALL, at(1)); // 전설 8
        scene.autoEquip(PARASOL, ALL, at(1));   // 일반 1
        assertThat(scene.stylePoints(ALL, STYLE)).isEqualTo(13);
        assertThat(Scene.blank(EXPLORER, T0).stylePoints(ALL, STYLE)).isZero();
    }

    @Test
    void 재계산_정리는_가방에_없는_아이템만_벗긴다() {
        Scene scene = Scene.blank(EXPLORER, T0);
        scene.autoEquip(LANTERN, ALL, at(1));
        scene.autoEquip(CAP, ALL, at(1));
        scene.autoEquip(PARASOL, ALL, at(1));

        assertThat(scene.keepOnly(Holdings.of(List.of(CAP.itemId())), at(2))).isPresent();
        assertThat(scene.wornItemIds()).containsExactly(CAP.itemId());
        assertThat(scene.keepOnly(Holdings.of(List.of(CAP.itemId())), at(3))).isEmpty();
    }
}
