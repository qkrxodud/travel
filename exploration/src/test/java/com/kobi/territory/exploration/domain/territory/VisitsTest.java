package com.kobi.territory.exploration.domain.territory;

import static com.kobi.territory.exploration.domain.Fixtures.FRIEND;
import static com.kobi.territory.exploration.domain.Fixtures.GAPYEONG;
import static com.kobi.territory.exploration.domain.Fixtures.JONGNO;
import static com.kobi.territory.exploration.domain.Fixtures.JUNG;
import static com.kobi.territory.exploration.domain.Fixtures.KST;
import static com.kobi.territory.exploration.domain.Fixtures.ME;
import static com.kobi.territory.exploration.domain.Fixtures.NOON;
import static com.kobi.territory.exploration.domain.Fixtures.TODAY;
import static com.kobi.territory.exploration.domain.Fixtures.refusal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.domain.ExplorationError;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("지도의 방문 모음")
class VisitsTest {

    /** region 을 who 가 daysAgo 일 전 날짜로, at 에 칠한 방문. */
    static Visit visit(RegionSnapshot region, ExplorerId who, int daysAgo, Instant at) {
        return new Visit(region, who, VisitDate.of(TODAY.minusDays(daysAgo)), Memo.EMPTY, null, Verification.NONE, at);
    }

    @Nested
    @DisplayName("방문을 찾을 때")
    class Find {

        @Test
        @DisplayName("지역과 멤버로 방문을 찾는다")
        void findsByRegionAndMember() {
            Visits visits = Visits.of(List.of(visit(JONGNO, ME, 0, NOON)));
            assertThat(visits.contains(JONGNO.code(), ME)).isTrue();
            assertThat(visits.contains(JONGNO.code(), FRIEND)).isFalse();
        }

        @Test
        @DisplayName("칠하지 않은 지역을 요구하면 방문이 없다고 거절된다")
        void missingRefused() {
            Visits visits = Visits.of(List.of(visit(JONGNO, ME, 0, NOON)));
            assertThat(refusal(() -> visits.require(JUNG.code(), ME))).isEqualTo(ExplorationError.VISIT_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("방문을 더할 때")
    class Add {

        @Test
        @DisplayName("다른 멤버는 같은 지역 방문을 더할 수 있다")
        void otherMemberSameRegion() {
            Visits visits = Visits.of(List.of(visit(JONGNO, ME, 0, NOON)));
            visits.add(visit(JONGNO, FRIEND, 0, NOON));
            assertThat(visits.size()).isEqualTo(2);
        }

        @Test
        @DisplayName("같은 멤버의 같은 지역 방문은 더할 수 없다")
        void duplicateRefused() {
            Visits visits = Visits.of(List.of(visit(JONGNO, ME, 0, NOON)));
            assertThat(refusal(() -> visits.add(visit(JONGNO, ME, 1, NOON)))).isEqualTo(ExplorationError.DUPLICATE_VISIT);
        }

        @Test
        @DisplayName("같은 멤버의 같은 지역 방문이 둘인 기록은 거절된다")
        void duplicateRestoreRefused() {
            assertThatThrownBy(() -> Visits.of(List.of(visit(JONGNO, ME, 0, NOON), visit(JONGNO, ME, 2, NOON))))
                .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("멤버와 지역으로 나눠 볼 때")
    class Views {

        Visits visits() {
            return Visits.of(List.of(visit(JONGNO, FRIEND, 0, NOON), visit(JONGNO, ME, 0, NOON.plusSeconds(5)),
                visit(GAPYEONG, ME, 0, NOON)));
        }

        @Test
        @DisplayName("멤버마다 칠한 방문과 밟은 시·도를 안다")
        void perMember() {
            Visits visits = visits();
            assertThat(visits.of(ME).size()).isEqualTo(2);
            assertThat(visits.of(ME).touches("KR-31")).isTrue();
            assertThat(visits.of(FRIEND).touches("KR-31")).isFalse();
        }

        @Test
        @DisplayName("지역이 지도에 칠해졌는지 안다")
        void paintedRegion() {
            Visits visits = visits();
            assertThat(visits.anyIn(JONGNO.code())).isTrue();
            assertThat(visits.anyIn(JUNG.code())).isFalse();
        }

        @Test
        @DisplayName("지역의 선점은 먼저 칠한 멤버다")
        void claim() {
            assertThat(visits().claimOf(JONGNO.code())).get().extracting(Visit::checkedInBy).isEqualTo(FRIEND);
        }

        @Test
        @DisplayName("칠해진 지역은 멤버와 상관없이 한 번씩만 센다")
        void distinctRegions() {
            assertThat(visits().regions()).containsExactly(JONGNO, GAPYEONG);
        }
    }

    @Nested
    @DisplayName("하루에 칠한 곳을 셀 때")
    class PerDay {

        Visits visits() {
            Instant lateUtc = Instant.parse("2026-10-02T15:30:00Z"); // KST 10-03 00:30
            return Visits.of(List.of(visit(JONGNO, ME, 5, NOON), visit(JUNG, ME, 5, lateUtc)));
        }

        @Test
        @DisplayName("한국 시간의 날짜로 나눈다")
        void byKoreanDay() {
            assertThat(visits().processedOn(TODAY, KST)).isEqualTo(1);
            assertThat(visits().processedOn(TODAY.plusDays(1), KST)).isEqualTo(1);
        }

        @Test
        @DisplayName("다른 시간대를 주면 그 시간대의 날짜로 나눈다")
        void byGivenZone() {
            assertThat(visits().processedOn(TODAY, ZoneOffset.UTC)).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("탐험 일지는 방문일 최근 순이고 같은 날이면 나중에 칠한 것이 먼저다")
    void recentFirst() {
        Visit threeDaysAgo = visit(JONGNO, ME, 3, NOON);
        Visit todayNoon = visit(JUNG, ME, 0, NOON);
        Visit todayLater = visit(GAPYEONG, ME, 0, NOON.plus(Duration.ofMinutes(1)));
        assertThat(Visits.of(List.of(threeDaysAgo, todayNoon, todayLater)).recentFirst())
            .containsExactly(todayLater, todayNoon, threeDaysAgo);
    }
}
