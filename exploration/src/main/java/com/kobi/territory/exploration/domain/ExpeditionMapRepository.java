package com.kobi.territory.exploration.domain;

import com.kobi.territory.common.model.ExplorerId;
import java.util.List;
import java.util.Optional;

public interface ExpeditionMapRepository {

    void save(ExpeditionMap map);

    Optional<ExpeditionMap> findById(MapId id);

    Optional<ExpeditionMap> findPersonalMap(ExplorerId owner);

    boolean existsByInviteCode(InviteCode code);

    /** 탐험가가 멤버인 지도 id 전부. */
    List<MapId> mapIdsOf(ExplorerId explorerId);

    /** selector 가 가리키는 지도(생략이면 requester 의 개인 지도). */
    default Optional<ExpeditionMap> find(MapSelector selector, ExplorerId requester) {
        return selector.personal() ? findPersonalMap(requester) : findById(selector.mapId());
    }
}
