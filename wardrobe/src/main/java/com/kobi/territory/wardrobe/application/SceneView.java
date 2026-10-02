package com.kobi.territory.wardrobe.application;

import com.kobi.territory.catalog.api.query.ItemView;
import com.kobi.territory.wardrobe.domain.scene.Scene;
import java.util.List;

/**
 * 장면 조회 결과: 장면 + 서버가 계산한 꾸미기 점수 + 입은 아이템의 정의(화면 표시용).
 *
 * @param stylePoints 착용 아이템 희귀도 점수 합(StylePolicy)
 */
public record SceneView(Scene scene, int stylePoints, List<ItemView> wornItems) {
    public SceneView {
        wornItems = List.copyOf(wornItems);
    }
}
