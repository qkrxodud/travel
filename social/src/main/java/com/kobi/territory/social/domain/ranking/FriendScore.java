package com.kobi.territory.social.domain.ranking;

import com.kobi.territory.common.model.ExplorerId;
import java.util.Objects;

/**
 * 친구 랭킹 재료 한 명: 탐험가 단위 중복 제거 지역 수(explorer_region — 여러 지도에서 같은 지역 = 1)와 레벨.
 */
public record FriendScore(ExplorerId explorerId, int regionCount, int level) {
    public FriendScore {
        Objects.requireNonNull(explorerId, "explorerId");
        if (regionCount < 0) throw new IllegalArgumentException("regionCount=" + regionCount);
    }
}
