package com.kobi.territory.sharing.domain.showcase;

import java.util.List;

/** 공개 정보의 장면 요약(꾸미기 점수·입은 아이템 이름·보유 수). */
public record ShowcaseScene(int stylePoints, List<String> wornItemNames, int ownedCount) {
    public ShowcaseScene {
        wornItemNames = List.copyOf(wornItemNames);
    }

    public static ShowcaseScene empty() {
        return new ShowcaseScene(0, List.of(), 0);
    }
}
