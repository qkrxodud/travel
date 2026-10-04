package com.kobi.territory.wardrobe.api.web;

import com.kobi.territory.catalog.api.query.ItemView;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.wardrobe.domain.inventory.OwnedItem;
import com.kobi.territory.wardrobe.domain.inventory.RevisitMarks;
import com.kobi.territory.wardrobe.domain.item.EquipSlot;
import com.kobi.territory.wardrobe.domain.scene.Gender;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 꾸미기 API 요청·응답 DTO. */
public final class WardrobeDtos {

    private WardrobeDtos() {}

    /**
     * 아이템 표시 정보(카탈로그 정의) — 정의가 사라진 아이템은 name 등이 null.
     *
     * @param variant     색 변형 번호(9단계): 1 = 기본, 2 = 재방문 2회차 — 2 면 variantLook 으로 그린다
     * @param variantLook 2회차 색 변형 룩(variant 2 인 지역 특산물만, 그 외 null)
     */
    public record ItemResponse(String itemId, String name, String emoji, String slot, Rarity tier, String theme,
                               ItemView.Look look, String regionCode, int variant, ItemView.Look variantLook) {
        static ItemResponse of(String itemId, ItemView view) {
            return of(itemId, view, RevisitMarks.BASE_VARIANT);
        }

        static ItemResponse of(String itemId, ItemView view, int variant) {
            return view == null ? new ItemResponse(itemId, null, null, null, null, null, null, null, RevisitMarks.BASE_VARIANT, null)
                : new ItemResponse(itemId, view.name(), view.emoji(), view.slot(), view.tier(), view.theme(), view.look(),
                view.regionCode(), variant, variant == RevisitMarks.REVISIT_VARIANT ? view.variantLook() : null);
        }
    }

    /**
     * @param source   REGION | SET_REWARD | EVENT
     * @param equipped 지금 장면에 입고 있는지
     */
    public record OwnedItemResponse(String itemId, String name, String emoji, String slot, Rarity tier, String theme,
                                    ItemView.Look look, String regionCode, String source, Instant acquiredAt,
                                    boolean favorite, boolean equipped, int variant, ItemView.Look variantLook) {
        static OwnedItemResponse of(OwnedItem item, ItemView view, boolean equipped, int variant) {
            ItemResponse shown = ItemResponse.of(item.itemId(), view, variant);
            return new OwnedItemResponse(item.itemId(), shown.name(), shown.emoji(), shown.slot(), shown.tier(), shown.theme(),
                shown.look(), shown.regionCode(), item.source().name(), item.acquiredAt(),
                item.favorite(), equipped, shown.variant(), shown.variantLook());
        }
    }

    public record InventoryResponse(int count, List<OwnedItemResponse> items) {}

    public record FavoriteRequest(@NotNull Boolean favorite) {}

    /**
     * @param slots       HAT·HAND·BADGE·BAG·PET·BG → 입은 아이템(비어 있으면 null)
     * @param props       장식 칸(순서대로, 최대 3)
     * @param stylePoints 꾸미기 점수 = 착용 아이템 희귀도 점수 합(서버 계산)
     */
    public record SceneResponse(String gender, Map<String, ItemResponse> slots, List<ItemResponse> props, int stylePoints,
                                int wornCount, Instant updatedAt) {}

    /**
     * PUT /scene — 비어 있는 필드는 그대로 둔다. 적용 순서: gender → unequip → equip → props.
     *
     * @param equip   슬롯 → 입을 아이템 id
     * @param unequip 벗을 슬롯
     * @param props   장식 칸 전체(순서대로, 최대 3 — 빈 목록이면 모두 내림)
     */
    public record SceneRequest(Gender gender, Map<EquipSlot, @NotNull String> equip, Set<EquipSlot> unequip,
                               @Size(max = 10) List<@NotNull String> props) {}
}
