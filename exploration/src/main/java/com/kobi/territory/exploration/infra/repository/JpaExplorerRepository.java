package com.kobi.territory.exploration.infra.repository;

import com.kobi.territory.exploration.infra.entity.ExplorerJpaEntity;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.domain.explorer.Explorer;
import com.kobi.territory.exploration.domain.explorer.ExplorerRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaExplorerRepository implements ExplorerRepository {

    private final ExplorerJpaRepository jpa;

    JpaExplorerRepository(ExplorerJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(Explorer explorer) {
        jpa.save(ExplorerJpaEntity.from(explorer));
    }

    @Override
    public List<ExplorerId> allIds() {
        return jpa.findAll().stream().map(ExplorerJpaEntity::explorerId).toList();
    }

    @Override
    public Optional<Explorer> findById(ExplorerId id) {
        return jpa.findById(id.value()).map(ExplorerJpaEntity::toDomain);
    }
}
