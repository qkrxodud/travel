package com.kobi.territory.analytics.infra.repository;

import com.kobi.territory.analytics.domain.actor.ExplorerHash;
import com.kobi.territory.analytics.domain.journey.ExplorerJourney;
import com.kobi.territory.analytics.domain.journey.ExplorerJourneyRepository;
import com.kobi.territory.analytics.infra.entity.ExplorerJourneyRow;
import com.kobi.territory.analytics.infra.entity.PurgeChunk;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 여정 저장(analytics_explorer). 있으면 바꾸고 없으면 넣는다. */
@Repository
public class JdbcExplorerJourneyRepository implements ExplorerJourneyRepository {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public JdbcExplorerJourneyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    @Override
    public Optional<ExplorerJourney> find(ExplorerHash explorerHash) {
        return jdbc.query(ExplorerJourneyRow.SELECT, (rs, rowNum) -> ExplorerJourneyRow.toDomain(rs), explorerHash.value())
            .stream().findFirst();
    }

    @Override
    public void save(ExplorerJourney journey) {
        Object[] parameters = ExplorerJourneyRow.parameters(journey);
        if (jdbc.update(ExplorerJourneyRow.UPDATE, parameters) == 0) jdbc.update(ExplorerJourneyRow.INSERT, parameters);
    }

    @Override
    public int purgeInactive(LocalDate before) {
        return PurgeChunk.drain(() -> jdbc.queryForList(ExplorerJourneyRow.SELECT_INACTIVE, String.class, before),
            hashes -> named.update(ExplorerJourneyRow.DELETE_IN, new MapSqlParameterSource("ids", hashes)));
    }
}
