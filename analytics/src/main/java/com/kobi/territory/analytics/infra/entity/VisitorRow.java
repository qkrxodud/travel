package com.kobi.territory.analytics.infra.entity;

import com.kobi.territory.analytics.domain.actor.VisitorSighting;

/** analytics_visitor 한 줄(방문 — 처음 본 날·들어온 길·이어진 탐험가). 사실은 처음 한 번만 채운다. */
public final class VisitorRow {

    /**
     * 없으면 넣고, 있으면 비어 있는 칸만 채운다 — 한 문장이라 같은 방문의 동시 요청이 행 배타 잠금 하나로 줄을 선다(따로 INSERT 를 시도하고
     * 중복이면 UPDATE 하면 MySQL 이 중복 검사의 공유 잠금을 배타 잠금으로 올리다 교착한다 — MySQL 동시성 테스트에서 확인).
     */
    public static final String UPSERT = "INSERT INTO analytics_visitor (visitor_id, first_seen_at, first_seen_day, entry, explorer_hash, "
        + "device) VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE entry = COALESCE(entry, VALUES(entry)), "
        + "explorer_hash = COALESCE(explorer_hash, VALUES(explorer_hash))";
    public static final String SELECT_INACTIVE = "SELECT v.visitor_id FROM analytics_visitor v WHERE v.first_seen_day < ? "
        + "AND NOT EXISTS (SELECT 1 FROM analytics_event e WHERE e.visitor_id = v.visitor_id) LIMIT " + PurgeChunk.SIZE;
    public static final String DELETE_IN = "DELETE FROM analytics_visitor WHERE visitor_id IN (:ids)";
    public static final String SELECT_EXPLORER = "SELECT explorer_hash FROM analytics_visitor WHERE visitor_id = ?";

    private VisitorRow() {}

    public static Object[] upsertParameters(VisitorSighting sighting) {
        return new Object[] {
            sighting.visitorId().value(), SqlTimes.utc(sighting.seenAt()), sighting.day(),
            sighting.entry() == null ? null : sighting.entry().label(),
            sighting.explorerHash() == null ? null : sighting.explorerHash().value(), sighting.device().name()
        };
    }
}
