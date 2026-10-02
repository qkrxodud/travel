package com.kobi.territory.exploration.application;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.domain.ExpeditionMap;
import com.kobi.territory.exploration.domain.ExpeditionMapRepository;
import com.kobi.territory.exploration.domain.ExplorationError;
import com.kobi.territory.exploration.domain.Explorer;
import com.kobi.territory.exploration.domain.ExplorerRepository;
import com.kobi.territory.exploration.domain.MapSelector;
import org.springframework.stereotype.Component;

/** 탐험가·지도 조회(DB 접근). 멤버십 판정은 ExpeditionMap(Members)이 한다. */
@Component
public class MapAccess {

    private final ExplorerRepository explorers;
    private final ExpeditionMapRepository maps;

    public MapAccess(ExplorerRepository explorers, ExpeditionMapRepository maps) {
        this.explorers = explorers;
        this.maps = maps;
    }

    public Explorer requireExplorer(ExplorerId id) {
        return explorers.findById(id).orElseThrow(ExplorationError.EXPLORER_NOT_FOUND::exception);
    }

    public MapMembership resolve(ExplorerId explorerId, MapSelector selector) {
        requireExplorer(explorerId);
        ExpeditionMap map = maps.find(selector, explorerId).orElseThrow(selector::notFound);
        return new MapMembership(map, map.requireMember(explorerId));
    }
}
