package com.kobi.territory.wardrobe.application;

import com.kobi.territory.catalog.api.query.ItemView;
import com.kobi.territory.wardrobe.domain.scene.Scene;
import com.kobi.territory.wardrobe.domain.inventory.RevisitMarks;
import java.util.List;
import java.util.Map;

/**
 * 장면 조회 결과: 장면 + 서버가 계산한 꾸미기 점수 + 입은 아이템의 정의(화면 표시용).
 *
 * @param stylePoints 착용 아이템 희귀도 점수 합(StylePolicy)
 */
public record SceneView(Scene scene, int stylePoints, List<ItemView> wornItems, Map<String, Integer> variants) {
    public SceneView {
        wornItems = List.copyOf(wornItems);
        variants = Map.copyOf(variants);
    }

    /** 입은 아이템의 색 변형 번호(9단계 — 재방문 도장 지역의 특산물이면 2, 아니면 1). */
    public int variantOf(String itemId) {
        return variants.getOrDefault(itemId, RevisitMarks.BASE_VARIANT);
    }
}
