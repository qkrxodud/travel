package com.kobi.territory.progression.domain;

import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.common.model.RegionCode;
import java.util.Objects;

/**
 * 퀘스트가 보는 체크인 사실.
 *
 * @param inAnySet        도감 세트에 속한 지역인지(SetCatalog 판단)
 * @param firstInProvince 탐험가가 이 체크인 전에 그 시·도를 한 번도 밟은 적 없는지(explorer_region 기준, QA P3-3) —
 *                        지도 기준 이벤트 값이 아니라 공유 지도에서도 탐험가 단위로 맞는다
 */
public record QuestFact(RegionCode region, String provinceCode, Rarity rarity, boolean inAnySet, boolean firstInProvince) {

    public QuestFact {
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(provinceCode, "provinceCode");
        Objects.requireNonNull(rarity, "rarity");
    }

    /** 체크인 사실 + 세트 정의 + 탐험가의 지역 기록으로 퀘스트 사실을 만든다. */
    public static QuestFact of(ProgressVisit visit, SetCatalog sets, ExploredRegions explored) {
        return new QuestFact(visit.region(), visit.provinceCode(), visit.rarity(), sets.inAnySet(visit.region()),
            !explored.visitedProvinceBefore(visit.provinceCode(), visit.visitedAt()));
    }
}
