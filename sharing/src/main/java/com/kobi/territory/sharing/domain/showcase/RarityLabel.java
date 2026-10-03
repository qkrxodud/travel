package com.kobi.territory.sharing.domain.showcase;

import com.kobi.territory.common.model.Rarity;

/** 희귀도 표시 이름(프로토타입 RAR_LABEL). */
public final class RarityLabel {

    private RarityLabel() {}

    public static String of(Rarity rarity) {
        return switch (rarity) {
            case COMMON -> "일반";
            case RARE -> "희귀";
            case LEGEND -> "전설";
        };
    }
}
