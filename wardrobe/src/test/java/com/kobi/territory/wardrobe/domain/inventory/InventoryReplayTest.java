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

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.wardrobe.domain.item.GrantKind;
import com.kobi.territory.wardrobe.domain.item.ItemSlot;
import com.kobi.territory.wardrobe.domain.item.ItemSpec;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** D1: 인벤토리 재계산(QA P2-1) — 이벤트 누적과 같은 결과, 손상 복구, 멱등, 탈퇴한 지도의 아이템 유지, 이력(시각·즐겨찾기) 유지. */
class InventoryReplayTest {

    static final RegionCode JONGNO = RegionCode.of("KR-11010");
    static final RegionCode BUSAN = RegionCode.of("KR-21090");
    static final RegionCode GURYE = RegionCode.of("KR-36330");
    static final ItemSpec LANTERN = regionItem("KR-11010", ItemSlot.HAND, Rarity.COMMON);
    static final ItemSpec PARASOL = regionItem("KR-21090", ItemSlot.PROP, Rarity.COMMON);
    static final ItemSpec BEANIE = regionItem("KR-36330", ItemSlot.HAT, Rarity.RARE);
    static final ItemSpec HANBOK = eventItem("hanbok", ItemSlot.HAT, Rarity.RARE);
    static final ExplorerId OTHER = ExplorerId.of("22222222-2222-2222-2222-222222222222");

    static CheckInGrant grant(String mapId, RegionCode region, int generation, int minute, ItemSpec... items) {
        return new CheckInGrant(mapId, region, generation, List.of(items), at(minute));
    }

    static ReplayVisit mine(CheckInGrant grant) {
        return new ReplayVisit(EXPLORER, grant);
    }

    /** 이벤트로 쌓은 상태: 개인 지도 종로(1)·부산(2, 이슈 포함) — 종로는 취소 후 재체크인(세대 2), 공유 지도 구례(1), 세트 배경. */
    static Inventory accumulated() {
        Inventory inventory = Inventory.empty(EXPLORER, T0);
        inventory.applyCheckIn(grant(PERSONAL_MAP, JONGNO, 1, 1, LANTERN));
        inventory.applyCheckIn(grant(PERSONAL_MAP, BUSAN, 1, 2, PARASOL, HANBOK));
        inventory.applyCancel(PERSONAL_MAP, JONGNO, 1, at(3));
        inventory.applyCheckIn(grant(PERSONAL_MAP, JONGNO, 2, 4, LANTERN));
        inventory.applyCheckIn(grant(SHARED_MAP, GURYE, 1, 5, BEANIE));
        inventory.grantRewards(List.of(setBackground("jiri")), at(6));
        return inventory;
    }

    /** 지금 남아 있는 방문(지도의 방문 이력) — 다른 멤버의 방문도 섞여 온다. */
    static final List<ReplayVisit> HISTORY = List.of(
        mine(grant(PERSONAL_MAP, BUSAN, 1, 2, PARASOL, HANBOK)),
        mine(grant(PERSONAL_MAP, JONGNO, 2, 4, LANTERN)),
        mine(grant(SHARED_MAP, GURYE, 1, 5, BEANIE)),
        new ReplayVisit(OTHER, grant(SHARED_MAP, JONGNO, 1, 7, LANTERN)));

    static Set<String> ownedIds(Inventory inventory) {
        return inventory.ownedItems().itemIds();
    }

    @Test
    void 재계산은_이벤트_누적과_같고_두_번_돌려도_같다() {
        Inventory accumulated = accumulated();
        Inventory once = InventoryReplay.replay(accumulated, Set.of(PERSONAL_MAP, SHARED_MAP), HISTORY,
            List.of(setBackground("jiri")), at(10));
        Inventory twice = InventoryReplay.replay(once, Set.of(PERSONAL_MAP, SHARED_MAP), HISTORY,
            List.of(setBackground("jiri")), at(11));

        assertThat(ownedIds(once)).isEqualTo(ownedIds(accumulated))
            .containsExactlyInAnyOrder("region:KR-11010", "region:KR-21090", "event:hanbok", "region:KR-36330", "set:jiri");
        assertThat(once.ownedItems().all()).containsExactlyInAnyOrderElementsOf(accumulated.ownedItems().all());
        assertThat(twice.ownedItems().all()).containsExactlyInAnyOrderElementsOf(once.ownedItems().all());
        assertThat(once.visitTraces().find(JONGNO, PERSONAL_MAP).orElseThrow().generation()).isEqualTo(2);
        assertThat(once.visitTraces().find(JONGNO, SHARED_MAP)).as("다른 멤버의 방문은 재생하지 않는다").isEmpty();
    }

    @Test
    void 손상된_인벤토리를_복구하고_처음_얻은_시각과_즐겨찾기는_유지한다() {
        Inventory accumulated = accumulated();
        accumulated.markFavorite("region:KR-21090", true, at(8));
        OwnedItem parasol = accumulated.find("region:KR-21090").orElseThrow();
        // 손상: 보유 아이템 일부와 방문 흔적이 사라짐(버그·FAILED 건너뛰기 등)
        Inventory damaged = Inventory.restore(EXPLORER, OwnedItems.of(List.of(parasol)), VisitTraces.empty(), at(8));

        Inventory repaired = InventoryReplay.replay(damaged, Set.of(PERSONAL_MAP, SHARED_MAP), HISTORY,
            List.of(setBackground("jiri")), at(10));

        assertThat(ownedIds(repaired)).isEqualTo(ownedIds(accumulated));
        assertThat(repaired.find("region:KR-21090").orElseThrow().favorite()).isTrue();
        assertThat(repaired.find("region:KR-21090").orElseThrow().acquiredAt()).isEqualTo(at(2));
        assertThat(repaired.find("event:hanbok").orElseThrow().basis()).containsExactly(new VisitKey(BUSAN, PERSONAL_MAP));
    }

    @Test
    void 탈퇴로_재생할_수_없는_지도에서_얻은_아이템과_근거는_유지한다() {
        Inventory accumulated = accumulated();
        // 공유 지도에서 탈퇴(방문 숨김) — 재생할 지도는 개인 지도뿐이고 공유 지도 이력은 보이지 않는다
        Inventory replayed = InventoryReplay.replay(accumulated, Set.of(PERSONAL_MAP), HISTORY.subList(0, 2), List.of(), at(10));

        assertThat(replayed.owns("region:KR-36330")).isTrue();
        assertThat(replayed.owns("set:jiri")).as("세트 보상은 회수 없음").isTrue();
        assertThat(replayed.visitTraces().find(GURYE, SHARED_MAP).orElseThrow().active()).isTrue();
    }

    @Test
    void 나중_합류_멤버는_완성된_테마_보상을_받는다_P3_1_안전망() {
        Inventory late = Inventory.empty(EXPLORER, T0);
        Inventory replayed = InventoryReplay.replay(late, Set.of(SHARED_MAP), List.of(), List.of(setBackground("jiri")), at(10));
        assertThat(replayed.find("set:jiri").orElseThrow().source()).isEqualTo(ItemSource.SET_REWARD);
    }

    @Test
    void 근거_방문이_없는_방문형_아이템은_재계산이_정리하고_보상_수동_아이템은_남긴다_R2_2() {
        Inventory damaged = Inventory.restore(EXPLORER, OwnedItems.of(List.of(
            OwnedItem.restore("region:KR-26010", GrantKind.REGION_VISIT, T0, false, Set.of()),   // 방문 없이 들어간 지역 아이템
            OwnedItem.restore("event:pin", GrantKind.PROVINCE_CHECK_IN, T0, false, Set.of()),     // 근거 없는 이슈 아이템
            OwnedItem.restore("set:han", GrantKind.THEME_COMPLETE, T0, true, Set.of()),
            OwnedItem.restore("event:gift", GrantKind.MANUAL, T0, false, Set.of()))), VisitTraces.empty(), T0);

        Inventory repaired = InventoryReplay.replay(damaged, Set.of(PERSONAL_MAP), List.of(), List.of(), at(10));

        assertThat(ownedIds(repaired)).containsExactlyInAnyOrder("set:han", "event:gift");
        assertThat(repaired.find("set:han").orElseThrow().favorite()).isTrue();
    }

    @Test
    void 세트_보상_따로_받을_사람은_완성자_몫_한_건에서_수령자_밖의_지금_멤버만_R2_5() {
        ExplorerId completer = EXPLORER;
        ExplorerId late = ExplorerId.of("33333333-3333-3333-3333-333333333333");
        List<ExplorerId> recipients = List.of(completer, OTHER);
        List<ExplorerId> members = List.of(completer, OTHER, late);

        assertThat(ThemeRewardRecipients.lateJoiners(completer, completer, recipients, members)).containsExactly(late);
        assertThat(ThemeRewardRecipients.lateJoiners(OTHER, completer, recipients, members)).as("다른 수령자 몫에서는 내지 않는다").isEmpty();
        // 예전 이벤트(completedBy·recipientIds 없음): 수령자 = 완성자 한 명
        assertThat(ThemeRewardRecipients.lateJoiners(completer, null, null, members)).containsExactly(OTHER, late);
    }
}
