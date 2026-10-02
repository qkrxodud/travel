package com.kobi.territory.exploration.domain.explorer;

import com.kobi.territory.common.model.ExplorerId;
import java.util.List;
import java.util.Optional;

public interface ExplorerRepository {

    void save(Explorer explorer);

    Optional<Explorer> findById(ExplorerId id);

    /** 모든 탐험가 id(재계산 배치용). */
    List<ExplorerId> allIds();
}
