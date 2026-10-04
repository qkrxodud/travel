package com.kobi.territory.exploration.domain.territory;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.domain.map.MapId;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Territory.restoreMember(유예 안 재가입) 의 결과.
 *
 * @param restored    복구한 방문의 지역
 * @param regionsBack 복구로 지도에 다시 칠해진 지역(복구 전에는 아무도 칠하지 않았던 곳)
 * @param visitedAt   복구한 방문마다 원래 처리 시각(9단계 — 계절 한정 테마는 회차 기간 안의 방문만 다시 센다)
 */
public record RestoreResult(MapId mapId, ExplorerId member, List<RegionCode> restored, List<RegionCode> regionsBack,
                            Map<RegionCode, Instant> visitedAt) {
    public RestoreResult {
        restored = List.copyOf(restored);
        regionsBack = List.copyOf(regionsBack);
        visitedAt = Map.copyOf(visitedAt);
    }

    public RestoreResult(MapId mapId, ExplorerId member, List<RegionCode> restored, List<RegionCode> regionsBack) {
        this(mapId, member, restored, regionsBack, Map.of());
    }
}
