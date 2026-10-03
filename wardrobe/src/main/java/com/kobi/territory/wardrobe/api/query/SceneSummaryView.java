package com.kobi.territory.wardrobe.api.query;

import java.util.List;

/**
 * 장면 요약(공개 Query DTO).
 *
 * @param gender      M | F
 * @param stylePoints 꾸미기 점수(착용 아이템 희귀도 점수 합)
 * @param wornItems   입은 아이템(슬롯 → 장식 순)
 * @param ownedCount  가방의 아이템 수
 */
public record SceneSummaryView(String gender, int stylePoints, List<WornItemView> wornItems, int ownedCount) {
    public SceneSummaryView {
        wornItems = List.copyOf(wornItems);
    }

    /** @param tier COMMON | RARE | LEGEND */
    public record WornItemView(String itemId, String name, String slot, String tier) {}
}
