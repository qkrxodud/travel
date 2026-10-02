package com.kobi.territory.exploration.domain;

import com.kobi.territory.common.model.ExplorerId;
import java.util.Optional;

public interface ExpeditionMapRepository {

    void save(ExpeditionMap map);

    Optional<ExpeditionMap> findById(MapId id);

    Optional<ExpeditionMap> findPersonalMap(ExplorerId owner);

    boolean existsByInviteCode(InviteCode code);

    /** selector 가 가리키는 지도(생략이면 requester 의 개인 지도). */
    default Optional<ExpeditionMap> find(MapSelector selector, ExplorerId requester) {
        return selector.personal() ? findPersonalMap(requester) : findById(selector.mapId());
    }
}
