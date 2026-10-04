package com.kobi.territory.analytics.infra.entity;

import com.kobi.territory.analytics.domain.metrics.CohortSnapshot;
import com.kobi.territory.analytics.domain.metrics.FunnelCounts;
import com.kobi.territory.analytics.domain.metrics.RetentionCounts;
import java.sql.ResultSet;
import java.sql.SQLException;

/** analytics_cohort 한 줄(코호트 하루 — 퍼널 + D1/D7/D30 리텐션, 아직 셀 수 없는 날은 NULL). */
public final class CohortRow {

    public static final String INSERT = "INSERT INTO analytics_cohort (cohort_day, funnel_first_screen, funnel_first_check_in, "
        + "funnel_revisited, funnel_settled, new_explorers, retained_d1, retained_d7, retained_d30, computed_at) "
        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    public static final String SELECT_RANGE = "SELECT * FROM analytics_cohort WHERE cohort_day BETWEEN ? AND ? ORDER BY cohort_day";
    public static final String SELECT_DAYS = "SELECT cohort_day FROM analytics_cohort WHERE cohort_day BETWEEN ? AND ?";
    public static final String DELETE = "DELETE FROM analytics_cohort WHERE cohort_day = ?";

    private CohortRow() {}

    public static Object[] parameters(CohortSnapshot snapshot) {
        FunnelCounts funnel = snapshot.funnel();
        RetentionCounts retention = snapshot.retention();
        return new Object[] {
            snapshot.cohortDay(), funnel.firstScreen(), funnel.firstCheckIn(), funnel.revisited(), funnel.settled(),
            retention.newExplorers(), retention.day1(), retention.day7(), retention.day30(), SqlTimes.utc(snapshot.computedAt())
        };
    }

    public static CohortSnapshot toDomain(ResultSet rs) throws SQLException {
        return new CohortSnapshot(SqlTimes.date(rs, "cohort_day"),
            new FunnelCounts(rs.getInt("funnel_first_screen"), rs.getInt("funnel_first_check_in"), rs.getInt("funnel_revisited"),
                rs.getBoolean("funnel_settled")),
            new RetentionCounts(rs.getInt("new_explorers"), SqlTimes.nullableInt(rs, "retained_d1"),
                SqlTimes.nullableInt(rs, "retained_d7"), SqlTimes.nullableInt(rs, "retained_d30")),
            SqlTimes.instant(rs, "computed_at"));
    }
}
