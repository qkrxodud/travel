package com.kobi.territory.exploration.domain.territory;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.domain.map.MapId;
import java.util.List;

/**
 * Territory.reassignMember(공유 지도 재귀속) 의 결과.
 *
 * @param reassigned from 의 방문이 그대로 into 의 것이 된 지역(into 방문이 없던 곳)
 * @param replaced   into 방문이 있었는데 from 쪽 선점 순서가 앞서 그 방문으로 바뀐 지역
 * @param dropped    into 방문이 선점 순서가 앞서 from 방문을 버린 지역
 */
public record ReassignResult(MapId mapId, ExplorerId from, ExplorerId into, List<RegionCode> reassigned,
                             List<RegionCode> replaced, List<RegionCode> dropped) {
    public ReassignResult {
        reassigned = List.copyOf(reassigned);
        replaced = List.copyOf(replaced);
        dropped = List.copyOf(dropped);
    }

    public boolean changed() {
        return !reassigned.isEmpty() || !replaced.isEmpty() || !dropped.isEmpty();
    }
}
