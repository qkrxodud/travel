package com.kobi.territory.analytics.domain.metrics;

import static com.kobi.territory.analytics.domain.Fixtures.NOW;
import static com.kobi.territory.analytics.domain.Fixtures.TODAY;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("하루 지표")
class DailyMetricsTest {

    private static final DayRange WEEK = DayRange.ending(TODAY, 7);

    @Nested
    @DisplayName("K 계수")
    class KFactors {

        @Test
        @DisplayName("다른 사람 덕분에 들어온 새 탐험가 ÷ 활성 탐험가 — 초대와 카드 유입이 겹친 사람은 한 번만 센다")
        void viralOverActive() {
            KFactor kFactor = new KFactor(DayRange.ending(TODAY, 30), 6, 5, 9, 60);

            assertThat(kFactor.value()).isEqualTo(0.15);
        }

        @Test
        @DisplayName("활성 탐험가가 없으면 셀 수 없음이다")
        void noActive() {
            assertThat(new KFactor(DayRange.ending(TODAY, 30), 0, 0, 0, 0).value()).isNull();
        }
    }

    @Nested
    @DisplayName("기능별 사용률")
    class Features {

        @Test
        @DisplayName("많이 쓴 기능부터, 아무도 안 쓴 기능도 0 으로 보인다")
        void ranked() {
            FeatureUsage usage = FeatureUsage.of(WEEK, 40, List.of("tab_view", "share_click", "checkin_open"),
                Map.of("tab_view", 30, "checkin_open", 10));

            assertThat(usage.ranked()).containsExactly(new FeatureUse("tab_view", 30, 0.75), new FeatureUse("checkin_open", 10, 0.25),
                new FeatureUse("share_click", 0, 0.0));
        }
    }

    @Nested
    @DisplayName("상위 오류 코드")
    class Errors {

        @Test
        @DisplayName("많이 뜬 코드부터 정해진 개수만 보인다")
        void top() {
            ErrorTally tally = ErrorTally.of(WEEK, Map.of("DAILY_CAP_EXCEEDED", 12, "MAP_FULL", 3, "DUPLICATE_VISIT", 7));

            assertThat(tally.top(2)).containsExactly(new ErrorCount("DAILY_CAP_EXCEEDED", 12), new ErrorCount("DUPLICATE_VISIT", 7));
            assertThat(tally.total()).isEqualTo(22);
        }
    }

    @Nested
    @DisplayName("지표 묶음")
    class Report {

        private DailySnapshot 하루(LocalDate day, int dau) {
            return new DailySnapshot(day, 0, 0, dau, dau, dau, PageViews.NONE, new KFactor(DayRange.ending(day, 30), 0, 0, 0, 0),
                FeatureUsage.of(DayRange.ending(day, 7), 0, List.of(), Map.of()), ErrorTally.of(DayRange.ending(day, 7), Map.of()),
                NOW);
        }

        private CohortSnapshot 코호트(LocalDate day) {
            return new CohortSnapshot(day, new FunnelCounts(0, 0, 0, false), new RetentionCounts(0, null, null, null), NOW);
        }

        @Test
        @DisplayName("지난 날은 배치 값, 오늘은 실시간 값을 날짜 순으로 보여 준다")
        void storedPlusLive() {
            DayRange range = DayRange.ending(TODAY, 3);
            MetricsReport report = MetricsReport.assemble(range, List.of(하루(TODAY.minusDays(1), 4), 하루(TODAY.minusDays(2), 5)),
                하루(TODAY, 9), List.of(코호트(TODAY.minusDays(2))), 코호트(TODAY), TODAY.minusDays(90), Instant.EPOCH, NOW);

            assertThat(report.daily()).extracting(DailySnapshot::dau).containsExactly(5, 4, 9);
            assertThat(report.cohorts()).extracting(CohortSnapshot::cohortDay).containsExactly(TODAY.minusDays(2), TODAY);
            assertThat(report.missingDays()).isEmpty();
        }

        @Test
        @DisplayName("배치가 아직 계산하지 않은 지난 날은 따로 알려 준다 — 원본이 남아 있어 배치로 채울 수 있다")
        void missing() {
            MetricsReport report = MetricsReport.assemble(DayRange.ending(TODAY, 3), List.of(하루(TODAY.minusDays(2), 5)), 하루(TODAY, 9),
                List.of(), 코호트(TODAY), TODAY.minusDays(90), null, NOW);

            assertThat(report.missingDays()).containsExactly(TODAY.minusDays(1));
            assertThat(report.expiredDays()).isEmpty();
        }

        @Test
        @DisplayName("원본 보관 기간이 지나 다시 셀 수 없는 날은 빈 날이 아니라 보관 기간 지남으로 따로 알려 준다")
        void expired() {
            MetricsReport report = MetricsReport.assemble(DayRange.ending(TODAY, 5), List.of(), 하루(TODAY, 9), List.of(), 코호트(TODAY),
                TODAY.minusDays(2), null, NOW);

            assertThat(report.missingDays()).containsExactly(TODAY.minusDays(2), TODAY.minusDays(1));
            assertThat(report.expiredDays()).containsExactly(TODAY.minusDays(4), TODAY.minusDays(3));
        }
    }
}
