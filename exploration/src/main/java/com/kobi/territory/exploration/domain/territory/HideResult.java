package com.kobi.territory.exploration.domain.territory;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.domain.map.MapId;
import java.util.List;

/**
 * Territory.hideMember(탈퇴 유예 시작) 의 결과.
 *
 * @param hidden         숨긴 방문의 지역
 * @param regionsGone    이제 지도에 아무도 칠하지 않은 지역
 * @param claimTransfers 넘어간 선점
 */
public record HideResult(MapId mapId, ExplorerId member, List<RegionCode> hidden, List<RegionCode> regionsGone,
                         List<ClaimTransfer> claimTransfers) {
    public HideResult {
        hidden = List.copyOf(hidden);
        regionsGone = List.copyOf(regionsGone);
        claimTransfers = List.copyOf(claimTransfers);
    }
}
