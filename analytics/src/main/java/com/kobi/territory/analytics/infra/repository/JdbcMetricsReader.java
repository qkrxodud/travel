package com.kobi.territory.analytics.infra.repository;

import com.kobi.territory.analytics.domain.actor.DeviceType;
import com.kobi.territory.analytics.domain.actor.EntryPoint;
import com.kobi.territory.analytics.domain.metrics.DayRange;
import com.kobi.territory.analytics.domain.metrics.MetricsReader;
import com.kobi.territory.analytics.domain.metrics.PageViews;
import com.kobi.territory.analytics.domain.tracking.EventDefinitions;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 집계 질의(analytics_event·analytics_visitor·analytics_explorer). 날짜 계산은 질의 밖(도메인이 구간을 정한다)이고, 질의는 인덱스
 * (event_day, actor_key)·(event_day, name, label)·(actor_key, event_day) 를 타도록 날짜 구간을 먼저 건다. "사람"은 행위자 열쇠가 있고
 * 봇이 아닌 이벤트, "탐험가"는 그중 방문 열쇠(v:)가 아닌 것.
 */
@Repository
public class JdbcMetricsReader implements MetricsReader {

    private static final String HUMAN = " AND actor_key IS NOT NULL AND device <> '" + DeviceType.BOT.name() + "'";
    private static final String EXPLORER_ACTOR = " AND actor_key NOT LIKE 'v:%'";
    private static final String CARD_ENTRY = "EXISTS (SELECT 1 FROM analytics_visitor v WHERE v.explorer_hash = x.explorer_hash "
        + "AND v.entry IN (:entries))";

    static final String NEW_VISITORS = "SELECT COUNT(*) FROM analytics_visitor WHERE first_seen_day = ?";
    static final String NEW_EXPLORERS = "SELECT COUNT(*) FROM analytics_explorer WHERE created_day = ?";
    static final String ACTIVE_ACTORS = "SELECT COUNT(DISTINCT actor_key) FROM analytics_event WHERE event_day BETWEEN ? AND ?" + HUMAN;
    static final String ACTIVE_EXPLORERS = ACTIVE_ACTORS + EXPLORER_ACTOR;
    static final String PAGE_VIEWS = "SELECT name, device, COUNT(*) AS views FROM analytics_event WHERE event_day = ? "
        + "AND name IN (?, ?, ?) GROUP BY name, device";
    static final String FEATURE_USERS = "SELECT name, COUNT(DISTINCT actor_key) AS users FROM analytics_event "
        + "WHERE event_day BETWEEN :from AND :to AND name IN (:features)" + HUMAN + " GROUP BY name";
    static final String ERROR_COUNTS = "SELECT label, COUNT(*) AS times FROM analytics_event WHERE event_day BETWEEN ? AND ? "
        + "AND name = ? AND label IS NOT NULL GROUP BY label";
    static final String INVITED_NEW = "SELECT COUNT(*) FROM analytics_explorer WHERE created_day BETWEEN ? AND ? "
        + "AND invite_acquired = TRUE";
    static final String CARD_NEW = "SELECT COUNT(*) FROM analytics_explorer x WHERE x.created_day BETWEEN :from AND :to AND "
        + CARD_ENTRY;
    static final String VIRAL_NEW = "SELECT COUNT(*) FROM analytics_explorer x WHERE x.created_day BETWEEN :from AND :to "
        + "AND (x.invite_acquired = TRUE OR " + CARD_ENTRY + ")";
    static final String FUNNEL_CHECK_IN = "SELECT COUNT(*) FROM analytics_visitor v JOIN analytics_explorer x "
        + "ON x.explorer_hash = v.explorer_hash WHERE v.first_seen_day = ? AND x.first_check_in_day BETWEEN ? AND ?";
    static final String FUNNEL_REVISITED = FUNNEL_CHECK_IN + " AND EXISTS (SELECT 1 FROM analytics_event e "
        + "WHERE e.actor_key = x.explorer_hash AND e.event_day > x.first_check_in_day AND e.event_day <= x.revisit_deadline "
        + "AND e.device <> '" + DeviceType.BOT.name() + "')";
    static final String RETAINED = "SELECT COUNT(*) FROM analytics_explorer x WHERE x.created_day = ? AND EXISTS (SELECT 1 "
        + "FROM analytics_event e WHERE e.actor_key = x.explorer_hash AND e.event_day = ? AND e.device <> '"
        + DeviceType.BOT.name() + "')";

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public JdbcMetricsReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    @Override
    public int newVisitors(LocalDate day) {
        return count(NEW_VISITORS, day);
    }

    @Override
    public int newExplorers(LocalDate day) {
        return count(NEW_EXPLORERS, day);
    }

    @Override
    public int activeActors(DayRange range) {
        return count(ACTIVE_ACTORS, range.from(), range.to());
    }

    @Override
    public int activeExplorers(DayRange range) {
        return count(ACTIVE_EXPLORERS, range.from(), range.to());
    }

    @Override
    public PageViews pageViews(LocalDate day) {
        Map<String, Integer> human = new HashMap<>();
        int[] bots = {0};
        jdbc.query(PAGE_VIEWS, rs -> {
                    if (DeviceType.BOT.name().equals(rs.getString("device"))) {
                        bots[0] += rs.getInt("views");
                    } else {
                        human.merge(rs.getString("name"), rs.getInt("views"), Integer::sum);
                    }
                }, day, EventDefinitions.PROFILE_VIEW, EventDefinitions.CARD_VIEW, EventDefinitions.COMPARE_CARD_VIEW);
        return new PageViews(human.getOrDefault(EventDefinitions.PROFILE_VIEW, 0),
            human.getOrDefault(EventDefinitions.CARD_VIEW, 0) + human.getOrDefault(EventDefinitions.COMPARE_CARD_VIEW, 0), bots[0]);
    }

    @Override
    public Map<String, Integer> featureUsers(DayRange range, List<String> features) {
        Map<String, Integer> users = new HashMap<>();
        if (features.isEmpty()) return users;
        named.query(FEATURE_USERS,
            new MapSqlParameterSource("from", range.from()).addValue("to", range.to()).addValue("features", features),
            rs -> {
                users.put(rs.getString("name"), rs.getInt("users"));
            });
        return users;
    }

    @Override
    public Map<String, Integer> errorCounts(DayRange range) {
        Map<String, Integer> counts = new HashMap<>();
        jdbc.query(ERROR_COUNTS, rs -> {
                    counts.put(rs.getString("label"), rs.getInt("times"));
                }, range.from(), range.to(), EventDefinitions.ERROR_TOAST);
        return counts;
    }

    @Override
    public int invitedNewExplorers(DayRange createdIn) {
        return count(INVITED_NEW, createdIn.from(), createdIn.to());
    }

    @Override
    public int cardNewExplorers(DayRange createdIn) {
        return namedCount(CARD_NEW, createdIn);
    }

    @Override
    public int viralNewExplorers(DayRange createdIn) {
        return namedCount(VIRAL_NEW, createdIn);
    }

    @Override
    public int funnelFirstScreen(LocalDate cohortDay) {
        return newVisitors(cohortDay);
    }

    @Override
    public int funnelFirstCheckIn(LocalDate cohortDay, DayRange checkInWindow) {
        return count(FUNNEL_CHECK_IN, cohortDay, checkInWindow.from(), checkInWindow.to());
    }

    @Override
    public int funnelRevisited(LocalDate cohortDay, DayRange checkInWindow) {
        return count(FUNNEL_REVISITED, cohortDay, checkInWindow.from(), checkInWindow.to());
    }

    @Override
    public int cohortSize(LocalDate cohortDay) {
        return newExplorers(cohortDay);
    }

    @Override
    public int retained(LocalDate cohortDay, LocalDate activeDay) {
        return count(RETAINED, cohortDay, activeDay);
    }

    private int count(String sql, Object... arguments) {
        Integer value = jdbc.queryForObject(sql, Integer.class, arguments);
        return value == null ? 0 : value;
    }

    private int namedCount(String sql, DayRange range) {
        Integer value = named.queryForObject(sql, new MapSqlParameterSource("from", range.from()).addValue("to", range.to())
            .addValue("entries", EntryPoint.sharedCardLabels()), Integer.class);
        return value == null ? 0 : value;
    }
}
