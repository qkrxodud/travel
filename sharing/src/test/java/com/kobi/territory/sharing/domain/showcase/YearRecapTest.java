package com.kobi.territory.sharing.domain.showcase;

import static com.kobi.territory.sharing.domain.Fixtures.JONGNO;
import static com.kobi.territory.sharing.domain.Fixtures.JUNG;
import static com.kobi.territory.sharing.domain.Fixtures.POHANG;
import static com.kobi.territory.sharing.domain.Fixtures.ULLEUNG;
import static com.kobi.territory.sharing.domain.Fixtures.visit;
import static com.kobi.territory.sharing.domain.Fixtures.visits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Year;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 회귀 출처: 6단계 QA P2-1(리캡 동점 규칙 — 카드와 화면이 같은 계산). */
@DisplayName("연간 리캡")
class YearRecapTest {

    static final Year Y2026 = Year.of(2026);

    @Nested
    @DisplayName("올해 칠한 곳을 돌아보면")
    class ThisYear {

        /** 2026년 3월 서울 두 곳, 2025년 울릉. */
        static final YearRecap RECAP = visits(visit(JONGNO, "2026-03-01", 1), visit(JUNG, "2026-03-09", 2),
            visit(ULLEUNG, "2025-08-01", 3)).recap(Y2026);

        @Test
        @DisplayName("올해 새로 칠한 곳을 센다")
        void newRegions() {
            assertThat(RECAP.newRegions()).isEqualTo(2);
        }

        @Test
        @DisplayName("달마다 칠한 곳을 센다")
        void monthCounts() {
            assertThat(RECAP.monthCounts().get(2)).isEqualTo(2);
        }

        @Test
        @DisplayName("가장 많이 간 시·도를 고른다")
        void topProvince() {
            assertThat(RECAP.topProvince()).isEqualTo(new YearRecap.ProvinceTally("KR-11", "서울", 2));
        }

        @Test
        @DisplayName("이전 해에 간 적 없는 시·도만 새로 밟은 시·도로 센다")
        void newProvinces() {
            assertThat(RECAP.newProvinces()).isEqualTo(1);
        }

        @Test
        @DisplayName("가장 많이 칠한 달을 고른다")
        void busiestMonth() {
            assertThat(RECAP.busiestMonth()).isEqualTo(new YearRecap.MonthTally(3, 2));
        }

        @Test
        @DisplayName("그해 가장 희귀한 곳을 고른다")
        void rarest() {
            assertThat(visits(visit(JONGNO, "2026-03-01", 1), visit(ULLEUNG, "2025-08-01", 3)).recap(Year.of(2025)).rarest().code())
                .isEqualTo(ULLEUNG);
        }
    }

    @Nested
    @DisplayName("동점이면")
    class Ties {

        /** 칠한 순서는 포항(5월) → 중구(2월) → 종로(작년) — 칠한 순서가 아니라 지역 코드로 가른다. */
        static final YearRecap RECAP = visits(visit(POHANG, "2026-05-01", 1), visit(JUNG, "2026-02-01", 2),
            visit(JONGNO, "2025-01-01", 3)).recap(Y2026);

        @Test
        @DisplayName("가장 많이 간 시·도는 지역 코드가 앞선 쪽이다")
        void topProvinceByCode() {
            assertThat(RECAP.newRegions()).isEqualTo(2);
            assertThat(RECAP.topProvince()).isEqualTo(new YearRecap.ProvinceTally("KR-11", "서울", 1));
        }

        @Test
        @DisplayName("가장 희귀한 곳은 지역 코드가 앞선 쪽이다")
        void rarestByCode() {
            assertThat(RECAP.rarest().code()).isEqualTo(JUNG);
            assertThat(visits(visit(JUNG, "2026-03-01", 1), visit(JONGNO, "2026-03-02", 2)).recap(Y2026).rarest().code())
                .isEqualTo(JONGNO);
        }

        @Test
        @DisplayName("가장 많이 칠한 달은 이른 달이다")
        void busiestEarlierMonth() {
            assertThat(RECAP.busiestMonth()).isEqualTo(new YearRecap.MonthTally(2, 1));
        }

        @Test
        @DisplayName("작년에 간 시·도는 새로 밟은 시·도가 아니다")
        void lastYearProvinceNotNew() {
            assertThat(RECAP.newProvinces()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("칠한 곳이 없으면")
    class Empty {

        static final YearRecap RECAP = visits().recap(Y2026);

        @Test
        @DisplayName("가장 많이 간 시·도·가장 희귀한 곳·가장 많이 칠한 달이 없다")
        void noHighlights() {
            assertThat(RECAP.topProvince()).isNull();
            assertThat(RECAP.rarest()).isNull();
            assertThat(RECAP.busiestMonth()).isNull();
        }

        @Test
        @DisplayName("열두 달 모두 0곳이다")
        void allMonthsZero() {
            assertThat(RECAP.monthCounts()).hasSize(12).containsOnly(0);
        }
    }

    @Nested
    @DisplayName("리캡 연도는")
    class YearChoice {

        @Test
        @DisplayName("고르지 않으면 올해다")
        void defaultsToThisYear() {
            assertThat(YearRecap.yearOf(null, Y2026)).isEqualTo(Y2026);
        }

        @Test
        @DisplayName("고른 해를 쓴다")
        void chosenYear() {
            assertThat(YearRecap.yearOf(2025, Y2026)).isEqualTo(Year.of(2025));
        }

        @Test
        @DisplayName("있을 수 없는 해는 고를 수 없다")
        void rejectsOutOfRange() {
            assertThatThrownBy(() -> YearRecap.yearOf(0, Y2026)).hasFieldOrPropertyWithValue("code", "INVALID_YEAR");
            assertThatThrownBy(() -> YearRecap.yearOf(10000, Y2026)).hasFieldOrPropertyWithValue("code", "INVALID_YEAR");
        }
    }
}
