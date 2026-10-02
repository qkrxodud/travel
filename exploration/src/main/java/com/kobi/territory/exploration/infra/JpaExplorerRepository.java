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
        jpa.save(new ExplorerJpaEntity(explorer.id().value(), explorer.handle(), explorer.createdAt()));
    }

    @Override
    public List<ExplorerId> allIds() {
        return jpa.findAll().stream().map(entity -> ExplorerId.of(entity.getId())).toList();
    }

    @Override
    public Optional<Explorer> findById(ExplorerId id) {
        return jpa.findById(id.value()).map(entity -> Explorer.restore(ExplorerId.of(entity.getId()), entity.getHandle(), entity.getCreatedAt()));
    }
}
