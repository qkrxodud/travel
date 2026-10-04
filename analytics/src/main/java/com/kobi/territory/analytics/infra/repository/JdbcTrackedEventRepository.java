package com.kobi.territory.analytics.infra.repository;

import com.kobi.territory.analytics.domain.actor.ActorKey;
import com.kobi.territory.analytics.domain.actor.VisitorId;
import com.kobi.territory.analytics.domain.tracking.TrackedEvent;
import com.kobi.territory.analytics.domain.tracking.TrackedEventRepository;
import com.kobi.territory.analytics.infra.entity.PurgeChunk;
import com.kobi.territory.analytics.infra.entity.TrackedEventRow;
import java.time.LocalDate;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 원본 이벤트 저장(analytics_event). 묶음은 JDBC 배치 insert, 서버 사실은 지문 UNIQUE 로 한 번만, 삭제는 조각으로 나눠 짧게. */
@Repository
public class JdbcTrackedEventRepository implements TrackedEventRepository {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public JdbcTrackedEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    @Override
    public void appendAll(List<TrackedEvent> events) {
        if (events.isEmpty()) return;
        jdbc.batchUpdate(TrackedEventRow.INSERT, events.stream().map(TrackedEventRow::parameters).toList());
    }

    @Override
    public boolean appendOnce(TrackedEvent event) {
        Integer existing = jdbc.queryForObject(TrackedEventRow.COUNT_BY_DEDUP, Integer.class, event.dedupKey());
        if (existing != null && existing > 0) return false;
        try {
            jdbc.update(TrackedEventRow.INSERT, TrackedEventRow.parameters(event));
            return true;
        } catch (DuplicateKeyException alreadyRecorded) {
            return false;
        }
    }

    @Override
    public int relinkVisitor(VisitorId visitorId, ActorKey explorerActor) {
        return jdbc.update(TrackedEventRow.RELINK, explorerActor.value(), visitorId.value(), ActorKey.ofVisitor(visitorId).value());
    }

    @Override
    public int purgeBefore(LocalDate before) {
        return PurgeChunk.drain(() -> jdbc.queryForList(TrackedEventRow.SELECT_EXPIRED, Long.class, before),
            ids -> named.update(TrackedEventRow.DELETE_IN, new MapSqlParameterSource("ids", ids)));
    }
}
