package com.kobi.territory.analytics.infra.entity;

import com.kobi.territory.analytics.domain.metrics.DailySnapshot;
import com.kobi.territory.analytics.domain.metrics.DayRange;
import com.kobi.territory.analytics.domain.metrics.ErrorTally;
import com.kobi.territory.analytics.domain.metrics.FeatureUsage;
import com.kobi.territory.analytics.domain.metrics.KFactor;
import com.kobi.territory.analytics.domain.metrics.PageViews;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** analytics_daily 한 줄(하루 지표 — 원본을 지워도 남는다). 기능별·오류 코드별 값은 {@link DailyBreakdownRow}. */
public final class DailyMetricRow {

    public static final String INSERT = "INSERT INTO analytics_daily (metric_day, new_visitors, new_explorers, dau, wau, mau, "
        + "profile_views, card_views, bot_views, k_from, k_to, k_invited_new, k_card_new, k_viral_new, k_active_explorers, "
        + "feature_from, feature_to, feature_active_users, computed_at, partial_window) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    public static final String SELECT_RANGE = "SELECT * FROM analytics_daily WHERE metric_day BETWEEN ? AND ? ORDER BY metric_day";
    public static final String SELECT_DAYS = "SELECT metric_day FROM analytics_daily WHERE metric_day BETWEEN ? AND ?";
    public static final String DELETE = "DELETE FROM analytics_daily WHERE metric_day = ?";
    public static final String LAST_COMPUTED = "SELECT MAX(computed_at) AS computed_at FROM analytics_daily";

    private DailyMetricRow() {}

    public static Object[] parameters(DailySnapshot snapshot) {
        KFactor kFactor = snapshot.kFactor();
        FeatureUsage usage = snapshot.featureUsage();
        PageViews views = snapshot.pageViews();
        return new Object[] {
            snapshot.day(), snapshot.newVisitors(), snapshot.newExplorers(), snapshot.dau(), snapshot.wau(), snapshot.mau(),
            views.profileViews(), views.cardViews(), views.botViews(),
            kFactor.range().from(), kFactor.range().to(), kFactor.invitedNewExplorers(), kFactor.cardNewExplorers(),
            kFactor.viralNewExplorers(), kFactor.activeExplorers(),
            usage.range().from(), usage.range().to(), usage.activeUsers(), SqlTimes.utc(snapshot.computedAt()), snapshot.partialWindow()
        };
    }

    /** 줄 + 그날의 갈래 값들 → 하루 지표. */
    public static DailySnapshot toDomain(ResultSet rs, List<DailyBreakdownRow> breakdowns) throws SQLException {
        DayRange kRange = new DayRange(SqlTimes.date(rs, "k_from"), SqlTimes.date(rs, "k_to"));
        DayRange featureRange = new DayRange(SqlTimes.date(rs, "feature_from"), SqlTimes.date(rs, "feature_to"));
        KFactor kFactor = new KFactor(kRange, rs.getInt("k_invited_new"), rs.getInt("k_card_new"), rs.getInt("k_viral_new"),
            rs.getInt("k_active_explorers"));
        FeatureUsage usage = FeatureUsage.of(featureRange, rs.getInt("feature_active_users"), DailyBreakdownRow.featureNames(breakdowns),
            DailyBreakdownRow.amounts(breakdowns, DailyBreakdownRow.FEATURE));
        ErrorTally errors = ErrorTally.of(featureRange, DailyBreakdownRow.amounts(breakdowns, DailyBreakdownRow.ERROR));
        return new DailySnapshot(SqlTimes.date(rs, "metric_day"), rs.getInt("new_visitors"), rs.getInt("new_explorers"),
            rs.getInt("dau"), rs.getInt("wau"), rs.getInt("mau"),
            new PageViews(rs.getInt("profile_views"), rs.getInt("card_views"), rs.getInt("bot_views")), kFactor, usage, errors,
            SqlTimes.instant(rs, "computed_at"), rs.getBoolean("partial_window"));
    }
}
