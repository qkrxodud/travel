package com.kobi.territory.catalog.domain.mystery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.common.model.RegionCode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 미스터리 지역의 "주": 서울 시각 월요일 0시에 시작해 다음 월요일 0시 직전까지. 지난 주를 나중에 고르지 않는다. */
@DisplayName("미스터리 주")
class MysteryWeekTest {

    private static final ZoneId 서울시각 = ZoneId.of("Asia/Seoul");
    private static final LocalDate 월요일 = LocalDate.of(2026, 10, 5);

    private static Instant 서울(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(서울시각).toInstant();
    }

    @Nested
    @DisplayName("주의 경계는")
    class Boundary {

        @Test
        @DisplayName("서울 시각 월요일 0시부터 새 주다")
        void mondayMidnight() {
            assertThat(MysteryWeek.weekStartOf(서울("2026-10-05T00:00:00"), 서울시각)).isEqualTo(월요일);
        }

        @Test
        @DisplayName("일요일 밤 23시 59분 59초는 아직 지난 주다")
        void sundayNight() {
            assertThat(MysteryWeek.weekStartOf(서울("2026-10-04T23:59:59"), 서울시각)).isEqualTo(월요일.minusWeeks(1));
        }

        @Test
        @DisplayName("세계 표준시로는 일요일 오후여도 서울이 월요일이면 새 주다")
        void zoneMatters() {
            assertThat(MysteryWeek.weekStartOf(Instant.parse("2026-10-04T15:00:00Z"), 서울시각)).isEqualTo(월요일);
        }

        @Test
        @DisplayName("주는 다음 월요일 0시에 끝난다")
        void endsNextMonday() {
            MysteryWeek week = new MysteryWeek(월요일, RegionCode.of("KR-37430"), 서울("2026-10-05T09:00:00"));

            assertThat(week.endsAt(서울시각)).isEqualTo(서울("2026-10-12T00:00:00"));
            assertThat(week.covers(서울("2026-10-11T23:59:59"), 서울시각)).isTrue();
            assertThat(week.covers(서울("2026-10-12T00:00:00"), 서울시각)).isFalse();
        }

        @Test
        @DisplayName("월요일이 아닌 날로 시작하는 주는 없다")
        void mustStartOnMonday() {
            assertThatThrownBy(() -> new MysteryWeek(월요일.plusDays(1), RegionCode.of("KR-37430"), Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("기록이 없는 주는")
    class Drawable {

        @Test
        @DisplayName("지금 주일 때만 새로 고른다")
        void onlyCurrentWeek() {
            assertThat(MysteryWeek.drawableAt(월요일, 서울("2026-10-07T12:00:00"), 서울시각)).isTrue();
        }

        @Test
        @DisplayName("지난 주는 나중에 고르지 않는다")
        void notPastWeek() {
            assertThat(MysteryWeek.drawableAt(월요일.minusWeeks(1), 서울("2026-10-07T12:00:00"), 서울시각)).isFalse();
        }

        @Test
        @DisplayName("다음 주를 미리 고르지 않는다")
        void notFutureWeek() {
            assertThat(MysteryWeek.drawableAt(월요일.plusWeeks(1), 서울("2026-10-07T12:00:00"), 서울시각)).isFalse();
        }
    }

    @Test
    @DisplayName("지금보다 앞선 주만 지난 주다")
    void pastWeek() {
        assertThat(MysteryWeek.pastAt(월요일.minusWeeks(1), 서울("2026-10-05T00:00:00"), 서울시각)).isTrue();
        assertThat(MysteryWeek.pastAt(월요일, 서울("2026-10-05T00:00:00"), 서울시각)).isFalse();
    }
}
