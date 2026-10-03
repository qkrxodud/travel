package com.kobi.territory.exploration.application;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.api.query.ExplorerProfileQuery;
import com.kobi.territory.exploration.domain.explorer.Explorer;
import com.kobi.territory.exploration.domain.explorer.ExplorerRepository;
import com.kobi.territory.exploration.domain.explorer.Handle;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 공개 Query 구현 — handle ↔ explorerId(계정 연결된 활성 탐험가만). */
@Service
@Transactional(readOnly = true)
public class ExplorerProfileService implements ExplorerProfileQuery {

    private final ExplorerRepository explorers;

    public ExplorerProfileService(ExplorerRepository explorers) {
        this.explorers = explorers;
    }

    @Override
    public Optional<String> explorerIdByHandle(String handle) {
        return Handle.parse(handle).flatMap(explorers::findByHandle).filter(Explorer::publicProfile)
            .map(explorer -> explorer.id().value());
    }

    @Override
    public Optional<String> handleOf(String explorerId) {
        return find(explorerId).filter(Explorer::publicProfile).map(explorer -> explorer.handle().value());
    }

    @Override
    public boolean accountLinked(String explorerId) {
        return find(explorerId).filter(Explorer::publicProfile).isPresent();
    }

    private Optional<Explorer> find(String explorerId) {
        try {
            return explorers.findById(ExplorerId.of(explorerId));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }
}
