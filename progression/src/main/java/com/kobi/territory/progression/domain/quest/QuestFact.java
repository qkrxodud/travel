package com.kobi.territory.progression.domain.quest;

import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.common.model.RegionCode;
import java.util.Objects;

/**
 * 퀘스트가 보는 체크인 사실.
 *
 * @param inAnyTheme      도감 테마(세트)에 속한 지역인지(Themes 판단)
 * @param firstInProvince 탐험가가 이 체크인 전에 그 시·도를 한 번도 밟은 적 없는지(explorer_region 기준, QA P3-3) —
 *                        지도 기준 이벤트 값이 아니라 공유 지도에서도 탐험가 단위로 맞는다
 */
public record QuestFact(RegionCode region, String provinceCode, Rarity rarity, boolean inAnyTheme, boolean firstInProvince) {

    public QuestFact {
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(provinceCode, "provinceCode");
        Objects.requireNonNull(rarity, "rarity");
    }

    /**
     * 퀘스트 사실을 만든다. 다른 애그리거트(진행의 지역 기록·도감의 테마 정의)에서 구한 판단 결과만 값으로 받는다 —
     * 애그리거트 폴더끼리 내부 타입을 참조하지 않게.
     *
     * @param provinceVisitedBefore 이 체크인 전에 탐험가가 그 시·도를 밟은 적 있는지
     */
    public static QuestFact of(RegionCode region, String provinceCode, Rarity rarity, boolean inAnyTheme,
                               boolean provinceVisitedBefore) {
        return new QuestFact(region, provinceCode, rarity, inAnyTheme, !provinceVisitedBefore);
    }
}
