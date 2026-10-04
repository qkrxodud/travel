package com.kobi.territory.analytics.infra.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kobi.territory.analytics.domain.tracking.TrackedEvent;
import java.util.Map;

/**
 * analytics_event 한 줄(원본 이벤트, 보관 기간이 지나면 지운다). 필드는 JSON 문자열(props)로.
 */
public final class TrackedEventRow {

    private static final ObjectMapper JSON = new ObjectMapper();

    public static final String INSERT = "INSERT INTO analytics_event (name, source, occurred_at, event_day, actor_key, explorer_hash, "
        + "visitor_id, device, country, label, props, dedup_key) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    public static final String COUNT_BY_DEDUP = "SELECT COUNT(*) FROM analytics_event WHERE dedup_key = ?";
    public static final String RELINK = "UPDATE analytics_event SET actor_key = ? WHERE visitor_id = ? AND actor_key = ?";
    public static final String SELECT_EXPIRED = "SELECT id FROM analytics_event WHERE event_day < ? ORDER BY event_day, id LIMIT "
        + PurgeChunk.SIZE;
    public static final String DELETE_IN = "DELETE FROM analytics_event WHERE id IN (:ids)";

    private TrackedEventRow() {}

    /** INSERT 의 자리표시자 순서대로. */
    public static Object[] parameters(TrackedEvent event) {
        return new Object[] {
            event.name(), event.source().name(), SqlTimes.utc(event.occurredAt()), event.day(),
            event.actor() == null ? null : event.actor().value(),
            event.explorerHash() == null ? null : event.explorerHash().value(),
            event.visitorId() == null ? null : event.visitorId().value(),
            event.device().name(), event.country() == null ? null : event.country().code(), event.label(),
            json(event.properties().view()), event.dedupKey()
        };
    }

    /** 필드 JSON — 직렬화기로 따옴표·제어 문자를 이스케이프한다(지금 값 형식으로는 필요 없지만 자유 문자열 필드가 생겨도 깨지지 않게). */
    static String json(Map<String, String> values) {
        try {
            return JSON.writeValueAsString(values);
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException("문자열 맵 직렬화 실패", impossible);
        }
    }
}
