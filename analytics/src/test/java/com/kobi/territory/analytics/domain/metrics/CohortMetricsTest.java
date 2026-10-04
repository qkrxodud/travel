package com.kobi.territory.analytics.domain.metrics;

import static com.kobi.territory.analytics.domain.Fixtures.METRICS;
import static com.kobi.territory.analytics.domain.Fixtures.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("코호트 — 퍼널과 리텐션")
class CohortMetricsTest {

    @Nested
    @DisplayName("첫 방문 퍼널")
    class Funnel {

        @Test
        @DisplayName("단계별 전환율은 바로 앞 단계 대비, 전체 전환율은 첫 화면 대비다")
        void rates() {
            FunnelCounts funnel = new FunnelCounts(200, 50, 20, true);

            assertThat(funnel.checkInRate()).isEqualTo(0.25);
            assertThat(funnel.revisitRate()).isEqualTo(0.4);
            assertThat(funnel.overallRate()).isEqualTo(0.1);
        }

        @Test
        @DisplayName("첫 화면이 하나도 없으면 전환율은 0% 가 아니라 셀 수 없음이다")
        void noVisitors() {
            assertThat(new FunnelCounts(0, 0, 0, false).checkInRate()).isNull();
        }

        @Test
        @DisplayName("뒤 단계가 앞 단계보다 많을 수는 없다")
        void monotonic() {
            assertThatThrownBy(() -> new FunnelCounts(10, 11, 0, false)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new FunnelCounts(10, 5, 6, false)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("D1/D7/D30 리텐션")
    class Retention {

        @Test
        @DisplayName("가입한 날로부터 1·7·30일째 되는 날 하루의 활동을 센다")
        void exactDays() {
            LocalDate cohort = TODAY.minusDays(40);
            List<LocalDate> asked = new ArrayList<>();

            RetentionCounts retention = RetentionCounts.measure(cohort, TODAY, 10, METRICS, activeDay -> {
                asked.add(activeDay);
                return 3;
            });

            assertThat(asked).containsExactly(cohort.plusDays(1), cohort.plusDays(7), cohort.plusDays(30));
            assertThat(retention.day30Rate()).isEqualTo(0.3);
        }

        @Test
        @DisplayName("아직 다 지나지 않은 날은 세지 않고 비워 둔다 — 어제 가입한 코호트는 D1 도 아직이다")
        void notYetObservable() {
            RetentionCounts yesterday = RetentionCounts.measure(TODAY.minusDays(1), TODAY, 5, METRICS, activeDay -> 1);
            RetentionCounts tenDaysAgo = RetentionCounts.measure(TODAY.minusDays(10), TODAY, 5, METRICS, activeDay -> 1);

            assertThat(yesterday.day1()).isNull();
            assertThat(tenDaysAgo.day1()).isEqualTo(1);
            assertThat(tenDaysAgo.day7()).isEqualTo(1);
            assertThat(tenDaysAgo.day30()).isNull();
            assertThat(tenDaysAgo.day30Rate()).isNull();
        }

        @Test
        @DisplayName("D1 경계 — 가입 이틀 뒤가 되어야 D1 이 채워진다")
        void dayOneBoundary() {
            LocalDate cohort = TODAY.minusDays(2);

            assertThat(RetentionCounts.measure(cohort, TODAY, 4, METRICS, activeDay -> 2).day1Rate()).isEqualTo(0.5);
        }
    }
}
