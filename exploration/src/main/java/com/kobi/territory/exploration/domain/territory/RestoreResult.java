package com.kobi.territory.exploration.domain.territory;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.domain.map.MapId;
import java.util.List;

/**
 * Territory.restoreMember(유예 안 재가입) 의 결과.
 *
 * @param restored    복구한 방문의 지역
 * @param regionsBack 복구로 지도에 다시 칠해진 지역(복구 전에는 아무도 칠하지 않았던 곳)
 */
public record RestoreResult(MapId mapId, ExplorerId member, List<RegionCode> restored, List<RegionCode> regionsBack) {
    public RestoreResult {
        restored = List.copyOf(restored);
        regionsBack = List.copyOf(regionsBack);
    }
}
