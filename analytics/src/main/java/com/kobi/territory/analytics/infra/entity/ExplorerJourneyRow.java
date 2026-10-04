package com.kobi.territory.analytics.infra.entity;

import com.kobi.territory.analytics.domain.actor.ExplorerHash;
import com.kobi.territory.analytics.domain.journey.ExplorerJourney;
import java.sql.ResultSet;
import java.sql.SQLException;

/** analytics_explorer 한 줄(탐험가 여정 — 가입일·첫 체크인·재방문 마감일·초대 합류). */
public final class ExplorerJourneyRow {

    public static final String SELECT = "SELECT explorer_hash, created_day, first_check_in_day, revisit_deadline, invited_join_day, "
        + "invite_acquired FROM analytics_explorer WHERE explorer_hash = ?";
    public static final String INSERT = "INSERT INTO analytics_explorer (created_day, first_check_in_day, revisit_deadline, "
        + "invited_join_day, invite_acquired, explorer_hash) VALUES (?, ?, ?, ?, ?, ?)";
    public static final String UPDATE = "UPDATE analytics_explorer SET created_day = ?, first_check_in_day = ?, revisit_deadline = ?, "
        + "invited_join_day = ?, invite_acquired = ? WHERE explorer_hash = ?";

    public static final String SELECT_INACTIVE = "SELECT x.explorer_hash FROM analytics_explorer x WHERE (x.created_day IS NULL "
        + "OR x.created_day < ?) AND NOT EXISTS (SELECT 1 FROM analytics_event e WHERE e.actor_key = x.explorer_hash) LIMIT "
        + PurgeChunk.SIZE;
    public static final String DELETE_IN = "DELETE FROM analytics_explorer WHERE explorer_hash IN (:ids)";

    private ExplorerJourneyRow() {}

    /** INSERT·UPDATE 공용 자리표시자 순서(열쇠가 마지막). */
    public static Object[] parameters(ExplorerJourney journey) {
        return new Object[] {
            journey.createdDay().orElse(null), journey.firstCheckInDay().orElse(null), journey.revisitDeadline().orElse(null),
            journey.invitedJoinDay().orElse(null), journey.inviteAcquired(), journey.explorerHash().value()
        };
    }

    public static ExplorerJourney toDomain(ResultSet rs) throws SQLException {
        return ExplorerJourney.restore(new ExplorerHash(rs.getString("explorer_hash")), SqlTimes.date(rs, "created_day"),
            SqlTimes.date(rs, "first_check_in_day"), SqlTimes.date(rs, "revisit_deadline"), SqlTimes.date(rs, "invited_join_day"),
            rs.getBoolean("invite_acquired"));
    }
}
