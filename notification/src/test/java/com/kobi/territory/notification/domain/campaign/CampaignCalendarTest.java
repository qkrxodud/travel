package com.kobi.territory.notification.domain.campaign;

import static com.kobi.territory.notification.domain.Fixtures.달력;
import static com.kobi.territory.notification.domain.Fixtures.월요일;
import static com.kobi.territory.notification.domain.Fixtures.서울;
import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.notification.domain.campaign.SeasonStarts.SeasonRoundStart;
import com.kobi.territory.notification.domain.policy.NotificationKind;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("알림 달력")
class CampaignCalendarTest {

    private static final SeasonStart 단풍 = new SeasonStart("autumn", "단풍 명소", "🍁", MonthDay.of(10, 1), MonthDay.of(11, 30));
    private static final SeasonStart 벚꽃 = new SeasonStart("spring", "벚꽃 명소", "🌸", MonthDay.of(3, 20), MonthDay.of(4, 30));
    private static final SeasonStarts 계절들 = SeasonStarts.of(List.of(벚꽃, 단풍));

    @Nested
    @DisplayName("이번 주 미스터리 지역")
    class WeeklyMystery {

        @Test
        @DisplayName("월요일 아침에 그 주(월요일 날짜) 알림을 만든다")
        void monday() {
            Campaign campaign = 달력.weeklyMystery(월요일, 서울(월요일, 9, 0), false).orElseThrow();

            assertThat(campaign.kind()).isEqualTo(NotificationKind.WEEKLY_MYSTERY);
            assertThat(campaign.period()).isEqualTo("2026-10-05");
            assertThat(campaign.dueAt()).isEqualTo(서울(월요일, 9, 0));
            assertThat(campaign.deliveryDay()).isEqualTo(월요일);
        }

        @Test
        @DisplayName("월요일이 아니면 만들지 않는다")
        void otherDays() {
            assertThat(달력.weeklyMystery(월요일, 서울(월요일.plusDays(1), 9, 0), false)).isEmpty();
        }

        @Test
        @DisplayName("스케줄이 조용한 시간(새벽 6시)에 돌면 아침 8시에 보낸다")
        void quietHours() {
            assertThat(달력.weeklyMystery(월요일, 서울(월요일, 6, 0), false).orElseThrow().dueAt()).isEqualTo(서울(월요일, 8, 0));
        }

        @Test
        @DisplayName("밤 늦게 돌면 다음 날 아침으로 미루고, 그날이 하루 한 개를 세는 날이 된다")
        void lateNightMovesToNextDay() {
            Campaign campaign = 달력.weeklyMystery(월요일, 서울(월요일, 23, 0), false).orElseThrow();

            assertThat(campaign.dueAt()).isEqualTo(서울(월요일.plusDays(1), 8, 0));
            assertThat(campaign.deliveryDay()).isEqualTo(월요일.plusDays(1));
        }

        @Test
        @DisplayName("개발용 즉시 발송은 요일과 조용한 시간을 건너뛴다")
        void forced() {
            Campaign campaign = 달력.weeklyMystery(월요일, 서울(월요일.plusDays(3), 23, 0), true).orElseThrow();

            assertThat(campaign.dueAt()).isEqualTo(서울(월요일.plusDays(3), 23, 0));
            assertThat(campaign.period()).isEqualTo("2026-10-05");
        }
    }

    @Nested
    @DisplayName("스트릭 지키기")
    class StreakGuard {

        @Test
        @DisplayName("그 달 마지막 날 사흘 전(10월은 28일)에 그 달 알림을 만든다")
        void threeDaysBeforeMonthEnd() {
            LocalDate reminder = LocalDate.of(2026, 10, 28);

            Campaign campaign = 달력.streakGuard(서울(reminder, 19, 0), false).orElseThrow();

            assertThat(campaign.period()).isEqualTo("2026-10");
            assertThat(달력.daysLeftInMonth(서울(reminder, 19, 0))).isEqualTo(4);
        }

        @Test
        @DisplayName("짧은 달도 마지막 날에서 센다 — 2027년 2월은 25일")
        void shortMonth() {
            assertThat(달력.streakReminderDay(YearMonth.of(2027, 2))).isEqualTo(LocalDate.of(2027, 2, 25));
            assertThat(달력.streakGuard(서울(LocalDate.of(2027, 2, 25), 19, 0), false)).isPresent();
        }

        @Test
        @DisplayName("그 밖의 날에는 만들지 않는다")
        void otherDays() {
            assertThat(달력.streakGuard(서울(LocalDate.of(2026, 10, 27), 19, 0), false)).isEmpty();
            assertThat(달력.streakGuard(서울(LocalDate.of(2026, 10, 29), 19, 0), false)).isEmpty();
        }
    }

    @Nested
    @DisplayName("계절 테마 시작일")
    class SeasonStart_ {

        @Test
        @DisplayName("시작일에 그 회차(계절-연도) 알림을 만든다")
        void startDay() {
            List<CampaignCalendar.SeasonCampaign> campaigns = 달력.seasonStarts(계절들, 서울(LocalDate.of(2026, 10, 1), 8, 30), false);

            assertThat(campaigns).singleElement().satisfies(seasonCampaign -> {
                assertThat(seasonCampaign.campaign().period()).isEqualTo("autumn-2026");
                assertThat(seasonCampaign.season()).isEqualTo(단풍);
            });
        }

        @Test
        @DisplayName("시작일이 아니면 만들지 않는다")
        void otherDays() {
            assertThat(달력.seasonStarts(계절들, 서울(LocalDate.of(2026, 10, 2), 8, 30), false)).isEmpty();
        }

        @Test
        @DisplayName("개발용 즉시 발송은 지금 열린 계절, 없으면 다음에 열리는 계절로 보낸다")
        void forced() {
            assertThat(달력.seasonStarts(계절들, 서울(LocalDate.of(2026, 11, 3), 12, 0), true))
                .singleElement().satisfies(seasonCampaign -> assertThat(seasonCampaign.campaign().period()).isEqualTo("autumn-2026"));
            assertThat(계절들.openOrNext(LocalDate.of(2026, 12, 10))).map(SeasonRoundStart::roundId).contains("spring-2027");
        }

        @Test
        @DisplayName("해를 넘는 계절은 시작한 해의 회차로 센다")
        void crossYear() {
            SeasonStart winter = new SeasonStart("winter", "겨울 바다", "❄️", MonthDay.of(12, 1), MonthDay.of(2, 28));

            assertThat(SeasonStarts.of(List.of(winter)).openOrNext(LocalDate.of(2027, 1, 15))).map(SeasonRoundStart::roundId)
                .contains("winter-2026");
        }
    }
}
