package com.kobi.territory.analytics.infra.repository;

import com.kobi.territory.analytics.domain.metrics.CohortSnapshot;
import com.kobi.territory.analytics.domain.metrics.DailySnapshot;
import com.kobi.territory.analytics.domain.metrics.DayRange;
import com.kobi.territory.analytics.domain.metrics.MetricsStore;
import com.kobi.territory.analytics.infra.entity.CohortRow;
import com.kobi.territory.analytics.infra.entity.DailyBreakdownRow;
import com.kobi.territory.analytics.infra.entity.DailyMetricRow;
import com.kobi.territory.analytics.infra.entity.SqlTimes;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 집계 저장(analytics_daily·analytics_daily_breakdown·analytics_cohort). 같은 날을 다시 쓰면 지우고 다시 넣는다. */
@Repository
public class JdbcMetricsStore implements MetricsStore {

    private final JdbcTemplate jdbc;

    public JdbcMetricsStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void replaceDaily(DailySnapshot snapshot) {
        jdbc.update(DailyBreakdownRow.DELETE, snapshot.day());
        jdbc.update(DailyMetricRow.DELETE, snapshot.day());
        jdbc.update(DailyMetricRow.INSERT, DailyMetricRow.parameters(snapshot));
        jdbc.batchUpdate(DailyBreakdownRow.INSERT, DailyBreakdownRow.parameters(snapshot));
    }

    @Override
    public void replaceCohort(CohortSnapshot snapshot) {
        jdbc.update(CohortRow.DELETE, snapshot.cohortDay());
        jdbc.update(CohortRow.INSERT, CohortRow.parameters(snapshot));
    }

    @Override
    public List<DailySnapshot> daily(DayRange range) {
        Map<LocalDate, List<DailyBreakdownRow>> breakdowns = jdbc.query(DailyBreakdownRow.SELECT_RANGE,
                (rs, rowNum) -> DailyBreakdownRow.read(rs), range.from(), range.to()).stream()
            .collect(Collectors.groupingBy(DailyBreakdownRow::day));
        return jdbc.query(DailyMetricRow.SELECT_RANGE,
            (rs, rowNum) -> DailyMetricRow.toDomain(rs, breakdowns.getOrDefault(SqlTimes.date(rs, "metric_day"), List.of())),
            range.from(), range.to());
    }

    @Override
    public List<CohortSnapshot> cohorts(DayRange range) {
        return jdbc.query(CohortRow.SELECT_RANGE, (rs, rowNum) -> CohortRow.toDomain(rs), range.from(), range.to());
    }

    @Override
    public Set<LocalDate> computedDays(DayRange range) {
        return Set.copyOf(jdbc.queryForList(DailyMetricRow.SELECT_DAYS, LocalDate.class, range.from(), range.to()));
    }

    @Override
    public Set<LocalDate> computedCohortDays(DayRange range) {
        return Set.copyOf(jdbc.queryForList(CohortRow.SELECT_DAYS, LocalDate.class, range.from(), range.to()));
    }

    @Override
    public Optional<Instant> lastComputedAt() {
        return Optional.ofNullable(jdbc.query(DailyMetricRow.LAST_COMPUTED,
            rs -> rs.next() ? SqlTimes.instant(rs, "computed_at") : null));
    }
}
