package com.kobi.territory.notification.domain.policy;

import static com.kobi.territory.notification.domain.Fixtures.SEOUL;
import static com.kobi.territory.notification.domain.Fixtures.월요일;
import static com.kobi.territory.notification.domain.Fixtures.조용한_시간;
import static com.kobi.territory.notification.domain.Fixtures.서울;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("조용한 시간")
class QuietHoursTest {

    @Nested
    @DisplayName("밤 10시부터 아침 8시까지(서울)")
    class NightToMorning {

        @Test
        @DisplayName("낮에는 바로 보낸다")
        void daytime() {
            Instant noon = 서울(월요일, 12, 0);

            assertThat(조용한_시간.allows(noon)).isTrue();
            assertThat(조용한_시간.nextAllowed(noon)).isEqualTo(noon);
        }

        @Test
        @DisplayName("밤 10시 정각부터는 다음 날 아침 8시로 미룬다")
        void lateNight() {
            assertThat(조용한_시간.allows(서울(월요일, 22, 0))).isFalse();
            assertThat(조용한_시간.nextAllowed(서울(월요일, 23, 30))).isEqualTo(서울(월요일.plusDays(1), 8, 0));
        }

        @Test
        @DisplayName("자정을 넘긴 새벽은 그날 아침 8시로 미룬다")
        void earlyMorning() {
            assertThat(조용한_시간.nextAllowed(서울(월요일, 3, 0))).isEqualTo(서울(월요일, 8, 0));
        }

        @Test
        @DisplayName("아침 8시 정각과 밤 9시 59분은 보낼 수 있다")
        void boundaries() {
            assertThat(조용한_시간.allows(서울(월요일, 8, 0))).isTrue();
            assertThat(조용한_시간.allows(서울(월요일, 21, 59))).isTrue();
            assertThat(조용한_시간.allows(서울(월요일, 7, 59))).isFalse();
        }

        @Test
        @DisplayName("하루는 서울 날짜로 센다 — 세계 표준시 15시는 서울의 다음 날 자정이다")
        void seoulDay() {
            assertThat(조용한_시간.dayOf(Instant.parse("2026-10-05T15:00:00Z"))).isEqualTo(월요일.plusDays(1));
            assertThat(조용한_시간.dayOf(Instant.parse("2026-10-05T14:59:59Z"))).isEqualTo(월요일);
        }
    }

    @Nested
    @DisplayName("다르게 정했을 때")
    class Other {

        @Test
        @DisplayName("자정을 넘지 않는 구간(오후 1시~2시)도 그 안에서만 미룬다")
        void sameDayWindow() {
            QuietHours lunch = new QuietHours(LocalTime.of(13, 0), LocalTime.of(14, 0), SEOUL);

            assertThat(lunch.nextAllowed(서울(월요일, 13, 30))).isEqualTo(서울(월요일, 14, 0));
            assertThat(lunch.allows(서울(월요일, 23, 0))).isTrue();
        }

        @Test
        @DisplayName("시작과 끝이 같으면 조용한 시간이 없다")
        void none() {
            QuietHours always = new QuietHours(LocalTime.of(22, 0), LocalTime.of(22, 0), SEOUL);

            assertThat(always.allows(서울(월요일, 22, 30))).isTrue();
        }
    }
}
