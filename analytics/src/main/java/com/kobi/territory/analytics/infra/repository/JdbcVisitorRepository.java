package com.kobi.territory.analytics.infra.repository;

import com.kobi.territory.analytics.domain.actor.ExplorerHash;
import com.kobi.territory.analytics.domain.actor.VisitorLink;
import com.kobi.territory.analytics.domain.actor.VisitorRepository;
import com.kobi.territory.analytics.domain.actor.VisitorSighting;
import com.kobi.territory.analytics.infra.entity.PurgeChunk;
import com.kobi.territory.analytics.infra.entity.VisitorRow;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 방문 저장(analytics_visitor). 한 문장 upsert 로 없으면 넣고 있으면 비어 있는 칸(들어온 길·탐험가)만 채운다 — 동시 요청에도 처음 값이 남는다.
 */
@Repository
public class JdbcVisitorRepository implements VisitorRepository {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public JdbcVisitorRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    @Override
    public VisitorLink record(VisitorSighting sighting) {
        jdbc.update(VisitorRow.UPSERT, VisitorRow.upsertParameters(sighting));
        List<String> linked = jdbc.queryForList(VisitorRow.SELECT_EXPLORER, String.class, sighting.visitorId().value());
        ExplorerHash explorerHash = linked.isEmpty() || linked.get(0) == null ? null : new ExplorerHash(linked.get(0));
        return new VisitorLink(explorerHash, explorerHash != null && explorerHash.equals(sighting.explorerHash()));
    }

    @Override
    public int purgeInactive(LocalDate before) {
        return PurgeChunk.drain(() -> jdbc.queryForList(VisitorRow.SELECT_INACTIVE, String.class, before),
            ids -> named.update(VisitorRow.DELETE_IN, new MapSqlParameterSource("ids", ids)));
    }
}
