package com.kobi.territory.wardrobe.domain.scene;

import static com.kobi.territory.wardrobe.domain.Fixtures.ME;
import static com.kobi.territory.wardrobe.domain.Fixtures.STYLE;
import static com.kobi.territory.wardrobe.domain.Fixtures.T0;
import static com.kobi.territory.wardrobe.domain.Fixtures.at;
import static com.kobi.territory.wardrobe.domain.Fixtures.regionItem;
import static com.kobi.territory.wardrobe.domain.Fixtures.themeBackground;
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
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 회귀 출처: 3단계 D1(장면 — 슬롯 일치·장식 3칸·가방에 있는 것만·자동 착용·회수 시 벗김·꾸미기 점수). */
@DisplayName("장면")
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
    static final ItemSpecs CATALOG = ItemSpecs.of(List.of(LANTERN, CAMERA, MAP, CAP, ULLEUNG, PARASOL, STATUE, VASE, TREE,
        themeBackground("han")));
    static final Holdings OWNS_ALL = Holdings.of(List.of(LANTERN.itemId(), CAMERA.itemId(), MAP.itemId(), CAP.itemId(),
        ULLEUNG.itemId(), PARASOL.itemId(), STATUE.itemId(), VASE.itemId(), TREE.itemId()));

    static Scene blank() {
        return Scene.blank(ME, T0);
    }

    /** 이 아이템들을 차례로 얻어 자동으로 입은 장면. */
    static Scene wearing(ItemSpec... items) {
        Scene scene = blank();
        for (ItemSpec item : items) scene.autoEquip(item, CATALOG, at(1));
        return scene;
    }

    static SceneEdit equip(EquipSlot slot, ItemSpec item) {
        return new SceneEdit(null, Map.of(slot, item.itemId()), null, null);
    }

    static SceneEdit props(ItemSpec... items) {
        return new SceneEdit(null, null, null, List.of(items).stream().map(ItemSpec::itemId).toList());
    }

    static void assertRejected(ThrowingCallable edit, String code) {
        assertThatThrownBy(edit).isInstanceOf(TerritoryException.class).hasFieldOrPropertyWithValue("code", code);
    }

    @Nested
    @DisplayName("새 아이템을 얻으면 자동으로 입는다")
    class AutoEquip {

        @Test
        @DisplayName("슬롯이 비어 있으면 입는다")
        void emptySlot() {
            assertThat(blank().autoEquip(LANTERN, CATALOG, at(1))).isPresent();
        }

        @Test
        @DisplayName("지금 입은 것과 희귀도가 같으면 그대로 둔다")
        void sameRarityKeepsCurrent() {
            Scene scene = wearing(LANTERN);

            assertThat(scene.autoEquip(MAP, CATALOG, at(2))).isEmpty();
            assertThat(scene.equippedSlots().itemAt(EquipSlot.HAND)).contains(LANTERN.itemId());
        }

        @Test
        @DisplayName("더 희귀하면 바꿔 입는다")
        void rarerReplaces() {
            Scene scene = wearing(LANTERN);

            assertThat(scene.autoEquip(CAMERA, CATALOG, at(3))).isPresent();
            assertThat(scene.equippedSlots().itemAt(EquipSlot.HAND)).contains(CAMERA.itemId());
        }

        @Test
        @DisplayName("덜 희귀하면 바꾸지 않는다")
        void lessRareKeepsCurrent() {
            Scene scene = wearing(CAMERA);

            assertThat(scene.autoEquip(LANTERN, CATALOG, at(4))).isEmpty();
            assertThat(scene.equippedSlots().itemAt(EquipSlot.HAND)).contains(CAMERA.itemId());
        }

        @Test
        @DisplayName("장식은 빈 칸에 놓는다")
        void propGoesToFreeSlot() {
            assertThat(blank().autoEquip(PARASOL, CATALOG, at(1))).isPresent();
        }

        @Test
        @DisplayName("이미 놓인 장식은 다시 놓지 않는다")
        void samePropOnce() {
            assertThat(wearing(PARASOL).autoEquip(PARASOL, CATALOG, at(1))).isEmpty();
        }

        @Test
        @DisplayName("장식 세 칸이 다 차면 더 놓지 않고 놓인 순서를 지킨다")
        void fullPropSlots() {
            Scene scene = wearing(PARASOL, STATUE, VASE);

            assertThat(scene.autoEquip(TREE, CATALOG, at(4))).isEmpty();
            assertThat(scene.propSlots().itemIds()).containsExactly(PARASOL.itemId(), STATUE.itemId(), VASE.itemId());
        }
    }

    @Nested
    @DisplayName("직접 꾸미면")
    class Edit {

        static final SceneEdit OUTFIT = new SceneEdit(Gender.F, Map.of(EquipSlot.HAT, CAP.itemId(), EquipSlot.BG, ULLEUNG.itemId()),
            Set.of(EquipSlot.HAND), List.of(STATUE.itemId()));

        @Test
        @DisplayName("성별·입기·장식을 한 번에 적용한다")
        void appliesAllAtOnce() {
            Scene scene = wearing(LANTERN);

            assertThat(scene.edit(OUTFIT, CATALOG, OWNS_ALL, at(2))).isPresent();
            assertThat(scene.gender()).isEqualTo(Gender.F);
            assertThat(scene.wornItemIds()).containsExactly(CAP.itemId(), ULLEUNG.itemId(), STATUE.itemId());
        }

        @Test
        @DisplayName("벗긴 슬롯은 비어 있다")
        void unequipClearsSlot() {
            Scene scene = wearing(LANTERN);
            scene.edit(OUTFIT, CATALOG, OWNS_ALL, at(2));

            assertThat(scene.equippedSlots().itemAt(EquipSlot.HAND)).isEmpty();
        }

        @Test
        @DisplayName("같은 슬롯에 다른 아이템을 입히면 이전 것은 벗겨진다 — 한 슬롯에 하나")
        void oneItemPerSlot() {
            Scene scene = wearing(LANTERN);

            scene.edit(equip(EquipSlot.HAND, MAP), CATALOG, OWNS_ALL, at(2));

            assertThat(scene.wornItemIds()).containsExactly(MAP.itemId());
        }

        @Test
        @DisplayName("바뀐 것이 없으면 장면이 바뀌지 않는다")
        void noChangeNoUpdate() {
            Scene scene = wearing(LANTERN);
            scene.edit(OUTFIT, CATALOG, OWNS_ALL, at(2));

            assertThat(scene.edit(OUTFIT, CATALOG, OWNS_ALL, at(3))).isEmpty();
            assertThat(scene.updatedAt()).isEqualTo(at(2));
        }
    }

    @Nested
    @DisplayName("입힐 수 없는 아이템")
    class Rejections {

        @Test
        @DisplayName("가방에 없는 아이템은 입힐 수 없고 장면도 그대로다")
        void notOwned() {
            Scene scene = blank();

            assertRejected(() -> scene.edit(equip(EquipSlot.HAND, LANTERN), CATALOG, Holdings.of(List.of()), at(1)), "ITEM_NOT_OWNED");
            assertThat(scene.wornItemIds()).isEmpty();
        }

        @Test
        @DisplayName("가방에 없는 장식은 놓을 수 없다")
        void propNotOwned() {
            assertRejected(() -> blank().edit(props(PARASOL), CATALOG, Holdings.of(List.of()), at(1)), "ITEM_NOT_OWNED");
        }

        @Test
        @DisplayName("아이템 슬롯과 다른 슬롯에는 입힐 수 없다")
        void slotMismatch() {
            assertRejected(() -> blank().edit(equip(EquipSlot.HAT, LANTERN), CATALOG, OWNS_ALL, at(1)), "SLOT_MISMATCH");
        }

        @Test
        @DisplayName("장식이 아닌 아이템은 장식 칸에 놓을 수 없다")
        void notAProp() {
            assertRejected(() -> blank().edit(props(LANTERN), CATALOG, OWNS_ALL, at(1)), "SLOT_MISMATCH");
        }

        @Test
        @DisplayName("카탈로그에 없는 아이템은 입힐 수 없다")
        void unknownItem() {
            ItemSpec unknown = regionItem("KR-99999", ItemSlot.HAND, Rarity.COMMON);

            assertRejected(() -> blank().edit(equip(EquipSlot.HAND, unknown), CATALOG, OWNS_ALL, at(1)), "ITEM_NOT_FOUND");
        }

        @Test
        @DisplayName("장식은 세 개까지만 놓을 수 있다")
        void tooManyProps() {
            assertRejected(() -> blank().edit(props(PARASOL, STATUE, VASE, TREE), CATALOG, OWNS_ALL, at(1)), "TOO_MANY_PROPS");
        }

        @Test
        @DisplayName("같은 장식을 두 칸에 놓을 수 없다")
        void duplicateProp() {
            assertRejected(() -> blank().edit(props(PARASOL, PARASOL), CATALOG, OWNS_ALL, at(1)), "DUPLICATE_PROP");
        }
    }

    @Nested
    @DisplayName("아이템을 잃으면")
    class TakeOff {

        @Test
        @DisplayName("그 아이템을 슬롯에서 벗긴다")
        void fromSlot() {
            Scene scene = wearing(LANTERN);

            assertThat(scene.takeOff(LANTERN.itemId(), at(2))).isPresent();
            assertThat(scene.wornItemIds()).isEmpty();
        }

        @Test
        @DisplayName("그 장식을 장식 칸에서 치운다")
        void fromPropSlots() {
            Scene scene = wearing(PARASOL);

            assertThat(scene.takeOff(PARASOL.itemId(), at(2))).isPresent();
            assertThat(scene.wornItemIds()).isEmpty();
        }

        @Test
        @DisplayName("입지 않은 아이템을 잃으면 장면은 그대로다")
        void notWorn() {
            assertThat(wearing(LANTERN).takeOff(CAP.itemId(), at(2))).isEmpty();
        }
    }

    @Nested
    @DisplayName("다시 계산한 가방에 맞추면")
    class KeepOnly {

        @Test
        @DisplayName("가방에 없게 된 아이템만 벗긴다")
        void removesOnlyMissing() {
            Scene scene = wearing(LANTERN, CAP, PARASOL);

            assertThat(scene.keepOnly(Holdings.of(List.of(CAP.itemId())), at(2))).isPresent();
            assertThat(scene.wornItemIds()).containsExactly(CAP.itemId());
        }

        @Test
        @DisplayName("이미 가방과 맞으면 장면이 바뀌지 않는다")
        void alreadyConsistent() {
            Scene scene = wearing(CAP);

            assertThat(scene.keepOnly(Holdings.of(List.of(CAP.itemId())), at(3))).isEmpty();
        }
    }

    @Nested
    @DisplayName("꾸미기 점수")
    class StylePoints {

        @Test
        @DisplayName("입은 아이템과 장식의 희귀도 점수를 더한다")
        void sumOfRarityPoints() {
            Scene scene = wearing(CAMERA, CAP, themeBackground("han"), PARASOL); // 희귀 3 + 일반 1 + 전설 8 + 일반 1

            assertThat(scene.stylePoints(CATALOG, STYLE)).isEqualTo(13);
        }

        @Test
        @DisplayName("아무것도 입지 않으면 0점이다")
        void blankIsZero() {
            assertThat(blank().stylePoints(CATALOG, STYLE)).isZero();
        }

        @Test
        @DisplayName("카탈로그에서 사라진 아이템은 0점으로 친다")
        void unknownWornItemScoresZero() {
            Scene scene = Scene.restore(ME, Gender.M, EquippedSlots.of(Map.of(EquipSlot.HAT, "region:KR-00000",
                EquipSlot.HAND, CAMERA.itemId())), PropSlots.empty(), T0);

            assertThat(scene.stylePoints(CATALOG, STYLE)).isEqualTo(3);
        }
    }
}
