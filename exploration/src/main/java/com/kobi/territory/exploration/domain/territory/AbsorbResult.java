package com.kobi.territory.exploration.domain.territory;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.domain.map.MapId;
import java.util.List;

/**
 * Territory.absorb(병합) 의 결과.
 *
 * @param added    into 가 새로 칠한 지역(into 방문이 없던 곳)
 * @param replaced from 쪽 방문일이 더 일러 그 기록으로 바뀐 지역
 */
public record AbsorbResult(MapId mapId, ExplorerId from, ExplorerId into, List<RegionCode> added, List<RegionCode> replaced) {
    public AbsorbResult {
        added = List.copyOf(added);
        replaced = List.copyOf(replaced);
    }

    public int moved() {
        return added.size() + replaced.size();
    }
}
