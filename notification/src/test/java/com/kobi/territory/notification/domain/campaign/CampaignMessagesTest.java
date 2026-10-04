package com.kobi.territory.notification.domain.campaign;

import static com.kobi.territory.notification.domain.Fixtures.월요일;
import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.notification.domain.push.PushMessage;
import java.time.MonthDay;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("알림 문구")
class CampaignMessagesTest {

    @Nested
    @DisplayName("이번 주 미스터리 지역")
    class Mystery {

        @Test
        @DisplayName("지역 이름은 감추고, 누르면 지도를 연다")
        void hidesRegion() {
            PushMessage message = CampaignMessages.weeklyMystery(월요일);

            assertThat(message.body()).contains("비밀").doesNotContain("KR-");
            assertThat(message.url()).isEqualTo("/?from=push&push=mystery#map");
            assertThat(message.tag()).isEqualTo("mystery-2026-10-05");
        }
    }

    @Nested
    @DisplayName("스트릭 지키기")
    class Streak {

        @Test
        @DisplayName("연속 개월과 남은 날을 알려 준다")
        void monthsAndDaysLeft() {
            PushMessage message = CampaignMessages.streakGuard(new StreakFacts(5, 0, 1), 4, "2026-10");

            assertThat(message.title()).contains("5개월");
            assertThat(message.body()).contains("4일 남았어요");
            assertThat(message.url()).isEqualTo("/?from=push&push=streak#map");
        }

        @Test
        @DisplayName("보호권으로 지킬 수 있으면 몇 개가 드는지 알려 준다")
        void covered() {
            assertThat(CampaignMessages.streakGuard(new StreakFacts(3, 2, 1), 4, "2026-10").body()).contains("보호권 2개 중 1개");
        }

        @Test
        @DisplayName("보호권이 모자라거나 없으면 놓치면 끊긴다고 알려 준다")
        void notCovered() {
            assertThat(CampaignMessages.streakGuard(new StreakFacts(3, 1, 2), 4, "2026-10").body()).contains("보호권 1개로는 모자라");
            assertThat(CampaignMessages.streakGuard(new StreakFacts(3, 0, 1), 4, "2026-10").body()).contains("보호권이 없어");
        }
    }

    @Nested
    @DisplayName("계절 테마 시작")
    class Season {

        @Test
        @DisplayName("계절 이름과 끝나는 날을 알려 주고, 누르면 도감을 연다")
        void nameAndEnd() {
            PushMessage message = CampaignMessages.seasonStart(
                new SeasonStart("autumn", "단풍 명소", "🍁", MonthDay.of(10, 1), MonthDay.of(11, 30)), "autumn-2026");

            assertThat(message.title()).isEqualTo("🍁 단풍 명소 시즌이 시작됐어요");
            assertThat(message.body()).contains("11월 30일");
            assertThat(message.url()).isEqualTo("/?from=push&push=season#sets");
            assertThat(message.tag()).isEqualTo("season-autumn-2026");
        }
    }
}
