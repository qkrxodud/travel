package com.kobi.territory.exploration.infra;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.domain.Explorer;
import com.kobi.territory.exploration.domain.ExplorerRepository;
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
