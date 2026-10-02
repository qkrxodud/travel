package com.kobi.territory.exploration.domain;

import com.kobi.territory.common.model.ExplorerId;
import java.util.Optional;

public interface ExplorerRepository {

    void save(Explorer explorer);

    Optional<Explorer> findById(ExplorerId id);
}
