package com.kobi.territory.social.domain.ranking;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.Rarity;
import java.util.Objects;

/**
 * 지도 안 랭킹의 재료 — 지도에 보이는 방문 한 건(탐험 공개 Query 에서 옮긴 값, 숨긴 방문은 처음부터 없다).
 *
 * @param claimOrder 지역 안 선점 순서(1 = 선점 방문, 2 = 다음 …)
 * @param disputed   지도장 이의 — 집계에서 빼고, 선점이었다면 다음 이의 아닌 방문이 선점으로 세진다(§7 치팅 대응, QA Q1)
 */
public record MapVisitFact(ExplorerId explorerId, String regionCode, Rarity rarity, int claimOrder, boolean disputed) {
    public MapVisitFact {
        Objects.requireNonNull(explorerId, "explorerId");
        Objects.requireNonNull(regionCode, "regionCode");
        Objects.requireNonNull(rarity, "rarity");
        if (claimOrder < 1) throw new IllegalArgumentException("claimOrder=" + claimOrder);
    }
}
