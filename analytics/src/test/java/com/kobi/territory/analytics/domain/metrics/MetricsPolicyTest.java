package com.kobi.territory.analytics.domain.metrics;

import static com.kobi.territory.analytics.domain.Fixtures.METRICS;
import static com.kobi.territory.analytics.domain.Fixtures.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.common.error.TerritoryException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("지표 기간")
class MetricsPolicyTest {

    @Nested
    @DisplayName("활동 구간")
    class Windows {

        @Test
        @DisplayName("주간 활동은 그날로 끝나는 7일, 월간 활동은 30일이다")
        void weekAndMonth() {
            assertThat(METRICS.weekEnding(TODAY)).isEqualTo(new DayRange(TODAY.minusDays(6), TODAY));
            assertThat(METRICS.monthEnding(TODAY)).isEqualTo(new DayRange(TODAY.minusDays(29), TODAY));
            assertThat(METRICS.kRange(TODAY).days()).isEqualTo(30);
            assertThat(METRICS.featureRange(TODAY).days()).isEqualTo(7);
        }
    }

    @Nested
    @DisplayName("오래된 빈 날을 늦게 채울 때")
    class LateBackfill {

        @Test
        @DisplayName("그날로 끝나는 30일 구간 앞부분 원본이 이미 지워졌으면 월간 활동·K 계수를 믿기 어렵다고 표시한다")
        void truncatedWindow() {
            assertThat(METRICS.windowTruncated(TODAY.minusDays(70), TODAY)).isTrue();
        }

        @Test
        @DisplayName("30일 구간이 모두 보관 기간 안이면 표시하지 않는다 — 61일째까지")
        void fullWindow() {
            assertThat(METRICS.windowTruncated(TODAY.minusDays(61), TODAY)).isFalse();
            assertThat(METRICS.windowTruncated(TODAY.minusDays(62), TODAY)).isTrue();
            assertThat(METRICS.windowTruncated(TODAY, TODAY)).isFalse();
        }
    }

    @Nested
    @DisplayName("일 배치")
    class Batch {

        @Test
        @DisplayName("코호트는 최근 35일을 늘 다시 계산하고, 그 앞은 원본이 남은 날 중 비어 있던 날만 채운다 — 오늘은 실시간으로 본다")
        void cohorts() {
            Set<LocalDate> computed = METRICS.backfillRange(TODAY).from().datesUntil(TODAY)
                .filter(day -> !day.equals(TODAY.minusDays(60))).collect(Collectors.toSet());

            List<LocalDate> days = METRICS.cohortDaysToRecompute(TODAY, computed);

            assertThat(days).hasSize(36).startsWith(TODAY.minusDays(60), TODAY.minusDays(35)).endsWith(TODAY.minusDays(1))
                .doesNotContain(TODAY);
        }

        @Test
        @DisplayName("하루 지표는 최근 3일만 늘 다시 계산하고, 그 앞은 원본이 남은 날 중 비어 있던 날만 채운다")
        void dailyRecompute() {
            Set<LocalDate> computed = METRICS.backfillRange(TODAY).from().datesUntil(TODAY)
                .filter(day -> !day.equals(TODAY.minusDays(80))).collect(Collectors.toSet());

            assertThat(METRICS.dailyDaysToRecompute(TODAY, computed))
                .containsExactly(TODAY.minusDays(80), TODAY.minusDays(3), TODAY.minusDays(2), TODAY.minusDays(1));
        }

        @Test
        @DisplayName("처음 돌리면 원본이 남아 있는 지난 90일을 모두 계산한다 — 지표 화면의 90일 보기에 빈 날이 남지 않는다")
        void firstRun() {
            assertThat(METRICS.dailyDaysToRecompute(TODAY, Set.of())).hasSize(90).startsWith(TODAY.minusDays(90));
            assertThat(METRICS.cohortDaysToRecompute(TODAY, Set.of())).hasSize(90);
        }

        @Test
        @DisplayName("원본 보관 기간보다 오래된 날은 다시 계산하지 않는다")
        void beyondRetention() {
            assertThat(METRICS.backfillRange(TODAY)).isEqualTo(new DayRange(TODAY.minusDays(90), TODAY.minusDays(1)));
            assertThat(METRICS.dailyDaysToRecompute(TODAY, Set.of())).doesNotContain(TODAY.minusDays(91));
        }

        @Test
        @DisplayName("90일이 지난 원본은 지운다 — 90일 전 날짜까지는 남는다")
        void purge() {
            assertThat(METRICS.purgeBefore(TODAY)).isEqualTo(TODAY.minusDays(90));
        }

        @Test
        @DisplayName("다시 계산하는 기간이 D30 리텐션이 확정되는 31일보다 짧으면 시작하지 않는다")
        void tooShortRecompute() {
            assertThatThrownBy(() -> new MetricsPolicy(90, 20, 3, 7, 30, 7, 7, 90, 10)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("보관 기간이 다시 계산할 날의 집계 구간보다 짧으면 시작하지 않는다 — 지운 원본으로 다시 세게 된다")
        void tooShortRetention() {
            assertThatThrownBy(() -> new MetricsPolicy(60, 35, 3, 7, 30, 7, 7, 90, 10)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("코호트가 확정되는 때")
    class Settling {

        @Test
        @DisplayName("N일째 날이 다 지나야 DN 리텐션을 셀 수 있다")
        void observable() {
            assertThat(METRICS.observable(TODAY.minusDays(1), 1, TODAY)).isFalse();
            assertThat(METRICS.observable(TODAY.minusDays(2), 1, TODAY)).isTrue();
        }

        @Test
        @DisplayName("퍼널은 첫 체크인 7일과 재방문 7일이 모두 지나야 확정된다")
        void funnelSettled() {
            assertThat(METRICS.funnelSettled(TODAY.minusDays(14), TODAY)).isFalse();
            assertThat(METRICS.funnelSettled(TODAY.minusDays(15), TODAY)).isTrue();
        }
    }

    @Nested
    @DisplayName("조회 구간")
    class Report {

        @Test
        @DisplayName("오늘로 끝나는 1~90일을 볼 수 있다")
        void range() {
            assertThat(METRICS.reportRange(TODAY, 30)).isEqualTo(new DayRange(TODAY.minusDays(29), TODAY));
        }

        @Test
        @DisplayName("90일을 넘거나 0일이면 거절한다")
        void outOfRange() {
            assertThatThrownBy(() -> METRICS.reportRange(TODAY, 91)).isInstanceOfSatisfying(TerritoryException.class,
                rejected -> assertThat(rejected.code()).isEqualTo("INVALID_METRICS_RANGE"));
            assertThatThrownBy(() -> METRICS.reportRange(TODAY, 0)).isInstanceOf(TerritoryException.class);
        }
    }
}
