package com.kobi.territory.wardrobe.application;

import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.wardrobe.domain.scene.StylePolicy;
import java.util.Map;

/**
 * 꾸미기 컨텍스트가 쓰는 게임 규칙 값. app-api 설정(territory.wardrobe.style-points.*)이 이 record 빈으로 넣는다
 * (모듈은 app-api 를 모른다). 도메인에는 정책 VO(StylePolicy)로 넘긴다.
 */
public record WardrobeSettings(int commonStylePoints, int rareStylePoints, int legendStylePoints) {
    public WardrobeSettings {
        if (commonStylePoints < 0 || rareStylePoints < 0 || legendStylePoints < 0) {
            throw new IllegalArgumentException("territory.wardrobe.style-points.* >= 0");
        }
    }

    public StylePolicy stylePolicy() {
        return new StylePolicy(Map.of(Rarity.COMMON, commonStylePoints, Rarity.RARE, rareStylePoints,
            Rarity.LEGEND, legendStylePoints));
    }
}
