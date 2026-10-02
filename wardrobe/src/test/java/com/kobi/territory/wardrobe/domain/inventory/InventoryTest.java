package com.kobi.territory.wardrobe.domain.inventory;

import static com.kobi.territory.wardrobe.domain.Fixtures.EXPLORER;
import static com.kobi.territory.wardrobe.domain.Fixtures.PERSONAL_MAP;
import static com.kobi.territory.wardrobe.domain.Fixtures.SHARED_MAP;
import static com.kobi.territory.wardrobe.domain.Fixtures.T0;
import static com.kobi.territory.wardrobe.domain.Fixtures.at;
import static com.kobi.territory.wardrobe.domain.Fixtures.eventItem;
import static com.kobi.territory.wardrobe.domain.Fixtures.regionItem;
import static com.kobi.territory.wardrobe.domain.Fixtures.setBackground;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.common.error.TerritoryException;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.wardrobe.domain.item.GrantKind;
import com.kobi.territory.wardrobe.domain.item.ItemSlot;
import com.kobi.territory.wardrobe.domain.item.ItemSpec;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** D1: Inventory — 중복 없음, 지역 아이템 회수(탐험가 단위), 세트·이슈 보상 유지, 세대 번호 무시. */
class InventoryTest {

    static final RegionCode JONGNO = RegionCode.of("KR-11010");
    static final ItemSpec LANTERN = regionItem("KR-11010", ItemSlot.HAND, Rarity.COMMON);
    static final ItemSpec HANBOK = eventItem("hanbok", ItemSlot.HAT, Rarity.RARE);
    static final RegionCode BUSAN = RegionCode.of("KR-21090");
    static final ItemSpec PARASOL = regionItem("KR-21090", ItemSlot.PROP, Rarity.COMMON);

    static CheckInGrant visit(String mapId, int generation, ItemSpec... items) {
        return new CheckInGrant(mapId, JONGNO, generation, List.of(items), at(generation));
    }

    @Test
    void 체크인하면_지역_아이템을_얻고_같은_이벤트가_다시_와도_한_번만() {
        Inventory inventory = Inventory.empty(EXPLORER, T0);
        InventoryChange first = inventory.applyCheckIn(visit(PERSONAL_MAP, 1, LANTERN));
        InventoryChange again = inventory.applyCheckIn(visit(PERSONAL_MAP, 1, LANTERN));

        assertThat(first.granted()).extracting(OwnedItem::itemId).containsExactly("region:KR-11010");
        assertThat(first.granted().get(0).source()).isEqualTo(ItemSource.REGION);
        assertThat(first.granted().get(0).basis()).containsExactly(new VisitKey(JONGNO, PERSONAL_MAP));
        assertThat(again.changed()).isFalse();
        assertThat(inventory.ownedItems().count()).isEqualTo(1);
    }

    @Test
    void 체크인_즉시_취소로_이슈_아이템을_얻을_수_없다_근거_방문이_모두_취소되면_회수_Q2() {
        Inventory inventory = Inventory.empty(EXPLORER, T0);
        inventory.applyCheckIn(visit(PERSONAL_MAP, 1, LANTERN, HANBOK));

        InventoryChange cancelled = inventory.applyCancel(PERSONAL_MAP, JONGNO, 1, at(5));

        assertThat(cancelled.revoked()).extracting(OwnedItem::itemId).containsExactlyInAnyOrder("region:KR-11010", "event:hanbok");
        assertThat(inventory.ownedItems().count()).isZero();
    }

    @Test
    void 같은_조건을_만족한_다른_활성_방문이_남으면_이슈_아이템은_유지_Q2() {
        Inventory inventory = Inventory.empty(EXPLORER, T0);
        inventory.applyCheckIn(visit(PERSONAL_MAP, 1, LANTERN, HANBOK));
        inventory.applyCheckIn(new CheckInGrant(PERSONAL_MAP, BUSAN, 1, List.of(PARASOL, HANBOK), at(2)));

        inventory.applyCancel(PERSONAL_MAP, JONGNO, 1, at(5));

        assertThat(inventory.owns("region:KR-11010")).isFalse();
        assertThat(inventory.find("event:hanbok").orElseThrow().basis()).containsExactly(new VisitKey(BUSAN, PERSONAL_MAP));
        assertThat(inventory.find("event:hanbok").orElseThrow().source()).isEqualTo(ItemSource.EVENT);
        inventory.applyCancel(PERSONAL_MAP, BUSAN, 1, at(6));
        assertThat(inventory.owns("event:hanbok")).isFalse();
    }

    @Test
    void 다른_지도에_같은_지역_활성_방문이_남으면_회수하지_않는다() {
        Inventory inventory = Inventory.empty(EXPLORER, T0);
        inventory.applyCheckIn(visit(PERSONAL_MAP, 1, LANTERN));
        inventory.applyCheckIn(visit(SHARED_MAP, 1, LANTERN));

        assertThat(inventory.applyCancel(PERSONAL_MAP, JONGNO, 1, at(5)).changed()).isFalse();
        assertThat(inventory.owns("region:KR-11010")).isTrue();

        assertThat(inventory.applyCancel(SHARED_MAP, JONGNO, 1, at(6)).revoked()).hasSize(1);
        assertThat(inventory.owns("region:KR-11010")).isFalse();
    }

    @Test
    void 세트_보상은_취소해도_회수하지_않고_두_번_와도_한_번만() {
        Inventory inventory = Inventory.empty(EXPLORER, T0);
        inventory.applyCheckIn(visit(PERSONAL_MAP, 1, LANTERN));
        assertThat(inventory.grantRewards(List.of(setBackground("han")), at(2)).granted())
            .extracting(OwnedItem::source).containsExactly(ItemSource.SET_REWARD);
        assertThat(inventory.grantRewards(List.of(setBackground("han")), at(3)).changed()).isFalse();

        inventory.applyCancel(PERSONAL_MAP, JONGNO, 1, at(5));

        assertThat(inventory.owns("set:han")).isTrue();
    }

    @Test
    void 옛_세대_이벤트는_무시한다_재체크인_뒤_늦게_온_취소_재전달() {
        Inventory inventory = Inventory.empty(EXPLORER, T0);
        inventory.applyCheckIn(visit(PERSONAL_MAP, 1, LANTERN));
        inventory.applyCancel(PERSONAL_MAP, JONGNO, 1, at(2));
        inventory.applyCheckIn(visit(PERSONAL_MAP, 2, LANTERN));

        // 세대 1 의 취소가 다시 와도 세대 2 방문은 살아 있다
        assertThat(inventory.applyCancel(PERSONAL_MAP, JONGNO, 1, at(4)).changed()).isFalse();
        assertThat(inventory.owns("region:KR-11010")).isTrue();
        // 세대 1 의 체크인이 다시 와도(이미 취소된 같은 세대) 지급하지 않는다
        inventory.applyCancel(PERSONAL_MAP, JONGNO, 2, at(5));
        assertThat(inventory.applyCheckIn(visit(PERSONAL_MAP, 1, LANTERN)).changed()).isFalse();
        assertThat(inventory.applyCheckIn(visit(PERSONAL_MAP, 2, LANTERN)).changed()).isFalse();
        assertThat(inventory.owns("region:KR-11010")).isFalse();
        assertThat(inventory.visitTraces().find(JONGNO, PERSONAL_MAP).orElseThrow().generation()).isEqualTo(2);
    }

    @Test
    void 세대_0_예전_이벤트는_회차_기록이_있으면_무시한다_Q3() {
        Inventory inventory = Inventory.empty(EXPLORER, T0);
        inventory.applyCheckIn(visit(PERSONAL_MAP, 3, LANTERN));
        // 늦게 재전달된 세대 0 취소가 더 새 세대(3)의 활성 방문을 끄지 않는다
        assertThat(inventory.applyCancel(PERSONAL_MAP, JONGNO, 0, at(5)).changed()).isFalse();
        assertThat(inventory.owns("region:KR-11010")).isTrue();
        inventory.applyCancel(PERSONAL_MAP, JONGNO, 3, at(6));
        // 세대 0 체크인도 회차 기록(3, 취소됨)이 있으면 무시 — 다시 지급하지 않는다
        assertThat(inventory.applyCheckIn(visit(PERSONAL_MAP, 0, LANTERN)).changed()).isFalse();
        assertThat(inventory.visitTraces().find(JONGNO, PERSONAL_MAP).orElseThrow().generation()).isEqualTo(3);
    }

    @Test
    void 세대_0_예전_이벤트는_회차_기록이_없으면_그대로_반영한다_Q3() {
        Inventory inventory = Inventory.empty(EXPLORER, T0);
        assertThat(inventory.applyCheckIn(visit(PERSONAL_MAP, 0, LANTERN)).granted()).hasSize(1);
        assertThat(inventory.applyCancel(PERSONAL_MAP, JONGNO, 0, at(5)).revoked()).hasSize(1);
        assertThat(inventory.applyCheckIn(visit(PERSONAL_MAP, 0, LANTERN)).granted()).hasSize(1);
    }

    @Test
    void 모르는_방문의_취소는_no_op() {
        Inventory inventory = Inventory.empty(EXPLORER, T0);
        assertThat(inventory.applyCancel(PERSONAL_MAP, JONGNO, 1, at(1)).changed()).isFalse();
        assertThat(inventory.visitTraces().changed()).isEmpty();
    }

    @Test
    void 즐겨찾기는_가방에_있는_아이템만() {
        Inventory inventory = Inventory.empty(EXPLORER, T0);
        inventory.applyCheckIn(visit(PERSONAL_MAP, 1, LANTERN));

        assertThat(inventory.markFavorite("region:KR-11010", true, at(3)).favorite()).isTrue();
        assertThat(inventory.ownedItems().added()).hasSize(1); // 새 행은 insert 한 번(즐겨찾기가 update 로 따로 가지 않는다)
        assertThat(inventory.ownedItems().updated()).isEmpty();
        assertThatThrownBy(() -> inventory.markFavorite("region:KR-26010", true, at(3)))
            .isInstanceOf(TerritoryException.class).hasFieldOrPropertyWithValue("code", "ITEM_NOT_OWNED");
    }

    @Test
    void 저장소용_변경_추적_복원_후_회수는_삭제_즐겨찾기는_갱신() {
        VisitKey jongno = new VisitKey(JONGNO, PERSONAL_MAP);
        OwnedItem lantern = OwnedItem.restore("region:KR-11010", GrantKind.REGION_VISIT, T0, false, Set.of(jongno));
        OwnedItem background = OwnedItem.restore("set:han", GrantKind.THEME_COMPLETE, T0, false, Set.of());
        Inventory inventory = Inventory.restore(EXPLORER, OwnedItems.of(List.of(lantern, background)),
            VisitTraces.of(List.of(VisitTrace.restore(JONGNO, PERSONAL_MAP, 1, true))), T0);

        inventory.markFavorite("set:han", true, at(1));
        inventory.applyCancel(PERSONAL_MAP, JONGNO, 1, at(2));

        assertThat(inventory.ownedItems().removed()).containsExactly("region:KR-11010");
        assertThat(inventory.ownedItems().updated()).extracting(OwnedItem::itemId).containsExactly("set:han");
        assertThat(inventory.ownedItems().added()).isEmpty();
        assertThat(inventory.visitTraces().changed()).extracting(VisitTrace::active).containsExactly(false);
        assertThat(inventory.ownedItems().newestFirst()).extracting(OwnedItem::itemId).containsExactly("set:han");
    }
}
