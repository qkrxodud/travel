package com.kobi.territory.sharing.domain.showcase;

import static com.kobi.territory.sharing.domain.Fixtures.ATLAS;
import static com.kobi.territory.sharing.domain.Fixtures.JONGNO;
import static com.kobi.territory.sharing.domain.Fixtures.JUNG;
import static com.kobi.territory.sharing.domain.Fixtures.POHANG;
import static com.kobi.territory.sharing.domain.Fixtures.ULLEUNG;
import static com.kobi.territory.sharing.domain.Fixtures.visit;
import static com.kobi.territory.sharing.domain.Fixtures.visits;
import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.common.model.TerritoryComparison;
import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 회귀 출처: 4단계 프라이버시(§7 — 메모·사진 비공개, 날짜는 월 단위) · 5단계 영토 비교 공유 규칙. */
@DisplayName("공개 방문")
class PublicVisitsTest {

    @Nested
    @DisplayName("공개되는 시기")
    class Months {

        @Test
        @DisplayName("방문일은 연과 월로만 보인다")
        void monthLabel() {
            VisitMonth month = VisitMonth.of(LocalDate.parse("2026-10-17"));

            assertThat(month.label()).isEqualTo("2026년 10월");
            assertThat(month.iso()).isEqualTo("2026-10");
        }

        @Test
        @DisplayName("가장 최근 방문은 몇 번째로 칠했는지와 그 달로 보인다")
        void mostRecent() {
            PublicVisit latest = visits(visit(JONGNO, "2026-10-17", 1), visit(ULLEUNG, "2025-05-02", 2)).mostRecent().orElseThrow();

            assertThat(latest.region().code()).isEqualTo(JONGNO);
            assertThat(latest.nth()).isEqualTo(1);
            assertThat(latest.month().label()).isEqualTo("2026년 10월");
        }

        @Test
        @DisplayName("최근 방문 목록은 방문일이 늦은 순서이고 달만 보인다")
        void latestByVisitDate() {
            PublicVisits visits = visits(visit(JONGNO, "2026-10-17", 1), visit(ULLEUNG, "2025-05-02", 2));

            assertThat(visits.latest(10)).extracting(visit -> visit.month().iso()).containsExactly("2026-10", "2025-05");
        }

        @Test
        @DisplayName("공개 방문에는 메모·사진·정확한 날짜가 담길 자리가 없다")
        void noPrivateFields() {
            assertThat(Arrays.stream(PublicVisit.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("region", "month", "nth");
            assertThat(Arrays.stream(VisitFact.class.getRecordComponents()).map(RecordComponent::getName))
                .doesNotContain("memo", "photo", "photoRef");
        }
    }

    @Nested
    @DisplayName("집계")
    class Tallies {

        /** 서울 두 곳·울릉 + 지도책에 없는 지역 하나. */
        static final PublicVisits MINE = visits(visit(JONGNO, "2026-03-01", 1), visit(JUNG, "2026-03-09", 2),
            visit(ULLEUNG, "2025-08-01", 3), visit("KR-99999", "2026-01-01", 4));

        @Test
        @DisplayName("지도책에 없는 지역은 세지 않는다")
        void skipsUnknownRegions() {
            assertThat(MINE.count()).isEqualTo(3);
        }

        @Test
        @DisplayName("같은 지역을 두 번 칠해도 처음 것 하나만 센다")
        void firstVisitPerRegion() {
            PublicVisits twice = visits(visit(JONGNO, "2026-01-01", 1), visit(JONGNO, "2026-05-01", 2));

            assertThat(twice.count()).isEqualTo(1);
            assertThat(twice.mostRecent().orElseThrow().month().iso()).isEqualTo("2026-01");
        }

        @Test
        @DisplayName("전국 정복률은 칠한 지역 비율이다")
        void conquestPercent() {
            assertThat(MINE.conquestPercent(ATLAS)).isEqualTo(75);
        }

        @Test
        @DisplayName("모든 지역을 칠한 시·도만 정복한 시·도로 센다")
        void conqueredProvinces() {
            assertThat(MINE.conqueredProvinceCount(ATLAS)).isEqualTo(1);
        }

        @Test
        @DisplayName("칠한 전설 지역을 센다")
        void legends() {
            assertThat(MINE.legendCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("다른 탐험가와 비교하면")
    class Versus {

        @Test
        @DisplayName("소셜 영토 비교와 같은 규칙으로 센다")
        void sameAsSharedComparison() {
            PublicVisits mine = visits(visit(JONGNO, "2026-01-01", 1), visit(JUNG, "2026-01-02", 2));
            PublicVisits theirs = visits(visit(JUNG, "2026-01-01", 1), visit(ULLEUNG, "2026-01-02", 2));

            assertThat(mine.versus(theirs)).isEqualTo(VersusTally.of(TerritoryComparison.of(mine.paintedCodes(), theirs.paintedCodes())))
                .isEqualTo(new VersusTally(1, 1, 1));
        }

        @Test
        @DisplayName("나만 간 곳·둘 다 간 곳·상대만 간 곳을 센다")
        void countsEachSide() {
            PublicVisits mine = visits(visit(JONGNO, "2026-03-01", 1), visit(JUNG, "2026-03-09", 2), visit(ULLEUNG, "2025-08-01", 3));
            PublicVisits theirs = visits(visit(JONGNO, "2026-01-01", 1), visit(POHANG, "2026-01-02", 2));

            VersusTally tally = mine.versus(theirs);

            assertThat(tally.onlyMine()).isEqualTo(2);
            assertThat(tally.both()).isEqualTo(1);
            assertThat(tally.onlyTheirs()).isEqualTo(1);
        }
    }
}
