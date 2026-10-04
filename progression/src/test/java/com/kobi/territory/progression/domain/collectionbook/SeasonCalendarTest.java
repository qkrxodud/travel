package com.kobi.territory.progression.domain.collectionbook;

import static com.kobi.territory.progression.domain.Fixtures.가평군;
import static com.kobi.territory.progression.domain.Fixtures.계절;
import static com.kobi.territory.progression.domain.Fixtures.서울시각;
import static com.kobi.territory.progression.domain.Fixtures.서울정오;
import static com.kobi.territory.progression.domain.Fixtures.종로구;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 계절 한정 테마 달력 — 해마다 같은 기간에 새 회차가 열리고, 기간은 서울 날짜로 양 끝을 포함한다. */
@DisplayName("계절 한정 테마 달력")
class SeasonCalendarTest {

    private static Instant 서울자정(int year, int month, int day) {
        return LocalDate.of(year, month, day).atStartOfDay(서울시각).toInstant();
    }

    @Nested
    @DisplayName("열린 회차")
    class OpenRounds {

        @Test
        @DisplayName("가을 기간에는 그 해 가을 회차가 열려 있다")
        void autumnOpen() {
            assertThat(계절.roundsOpenAt(서울정오(2026, 10, 4))).extracting(SeasonRound::roundId).containsExactly("autumn-2026");
        }

        @Test
        @DisplayName("기간 첫날 0시부터 열리고 마지막 날 다음 날 0시에 닫힌다")
        void boundaries() {
            assertThat(계절.roundsOpenAt(서울자정(2026, 10, 1))).hasSize(1);
            assertThat(계절.roundsOpenAt(서울자정(2026, 10, 1).minusSeconds(1))).isEmpty();
            assertThat(계절.roundsOpenAt(서울자정(2026, 12, 1).minusSeconds(1))).hasSize(1);
            assertThat(계절.roundsOpenAt(서울자정(2026, 12, 1))).isEmpty();
        }

        @Test
        @DisplayName("해가 바뀌면 같은 계절이라도 새 회차다")
        void newRoundEveryYear() {
            assertThat(계절.roundsOpenAt(서울정오(2027, 10, 4))).extracting(SeasonRound::roundId).containsExactly("autumn-2027");
            assertThat(계절.roundsOpenAt(서울정오(2027, 4, 1))).extracting(SeasonRound::roundId).containsExactly("spring-2027");
        }

        @Test
        @DisplayName("그 지역이 들어 있는 회차만 센다")
        void covering() {
            assertThat(계절.roundsCovering(가평군, 서울정오(2026, 10, 4))).isEmpty();
            assertThat(계절.roundsCovering(종로구, 서울정오(2026, 10, 4))).hasSize(1);
        }
    }

    @Nested
    @DisplayName("다음 회차")
    class Next {

        @Test
        @DisplayName("가을이 끝난 겨울에는 다음 해 봄 회차를 알려 준다")
        void nextSpring() {
            assertThat(계절.nextRoundAfter(서울정오(2026, 12, 15)).orElseThrow().roundId()).isEqualTo("spring-2027");
        }

        @Test
        @DisplayName("회차 id 로 그 회차의 기간을 다시 찾는다")
        void byId() {
            SeasonRound round = 계절.round("spring-2027").orElseThrow();
            assertThat(round.startsAt()).isEqualTo(서울자정(2027, 3, 20));
            assertThat(round.endsAt()).isEqualTo(서울자정(2027, 5, 1));
            assertThat(계절.round("winter-2027")).isEmpty();
        }
    }

    @Test
    @DisplayName("재계산용 달력은 이미 닫힌 회차를 내지 않는다")
    void excludingEnded() {
        SeasonCalendar recalcAtWinter = 계절.excludingEndedBy(서울정오(2026, 12, 15));
        assertThat(recalcAtWinter.roundsOpenAt(서울정오(2026, 10, 4))).isEmpty();
        assertThat(계절.excludingEndedBy(서울정오(2026, 10, 20)).roundsOpenAt(서울정오(2026, 10, 4))).hasSize(1);
    }
}
