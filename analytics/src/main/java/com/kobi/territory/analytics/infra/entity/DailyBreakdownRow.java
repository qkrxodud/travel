package com.kobi.territory.analytics.infra.entity;

import com.kobi.territory.analytics.domain.metrics.DailySnapshot;
import com.kobi.territory.analytics.domain.metrics.ErrorCount;
import com.kobi.territory.analytics.domain.metrics.FeatureUse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/** analytics_daily_breakdown 한 줄 — 하루 지표의 갈래 값(FEATURE: 기능별 사용자 수, ERROR: 오류 코드별 횟수). */
public record DailyBreakdownRow(LocalDate day, String kind, String name, int ordinal, int amount) {

    public static final String FEATURE = "FEATURE";
    public static final String ERROR = "ERROR";

    public static final String INSERT = "INSERT INTO analytics_daily_breakdown (metric_day, kind, name, ordinal, amount) "
        + "VALUES (?, ?, ?, ?, ?)";
    public static final String SELECT_RANGE = "SELECT metric_day, kind, name, ordinal, amount FROM analytics_daily_breakdown "
        + "WHERE metric_day BETWEEN ? AND ? ORDER BY metric_day, kind, ordinal";
    public static final String DELETE = "DELETE FROM analytics_daily_breakdown WHERE metric_day = ?";

    /** 하루 지표의 갈래 값 줄들(기능은 정의 순서대로 0도 넣는다 — 다시 읽을 때 같은 목록이 되게). */
    public static List<Object[]> parameters(DailySnapshot snapshot) {
        List<Object[]> rows = new ArrayList<>();
        List<FeatureUse> features = snapshot.featureUsage().ranked();
        IntStream.range(0, features.size()).forEach(i -> rows.add(new Object[] {
            snapshot.day(), FEATURE, features.get(i).name(), i, features.get(i).users()}));
        List<ErrorCount> errors = snapshot.errors().top(Integer.MAX_VALUE);
        IntStream.range(0, errors.size()).forEach(i -> rows.add(new Object[] {
            snapshot.day(), ERROR, errors.get(i).code(), i, errors.get(i).count()}));
        return rows;
    }

    public static DailyBreakdownRow read(ResultSet rs) throws SQLException {
        return new DailyBreakdownRow(SqlTimes.date(rs, "metric_day"), rs.getString("kind"), rs.getString("name"), rs.getInt("ordinal"),
            rs.getInt("amount"));
    }

    static List<String> featureNames(List<DailyBreakdownRow> rows) {
        return rows.stream().filter(row -> row.kind().equals(FEATURE)).map(DailyBreakdownRow::name).toList();
    }

    static Map<String, Integer> amounts(List<DailyBreakdownRow> rows, String kind) {
        Map<String, Integer> amounts = new LinkedHashMap<>();
        rows.stream().filter(row -> row.kind().equals(kind)).forEach(row -> amounts.put(row.name(), row.amount()));
        return amounts;
    }
}
