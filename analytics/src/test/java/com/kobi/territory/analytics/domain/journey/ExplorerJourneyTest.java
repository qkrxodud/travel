package com.kobi.territory.analytics.domain.journey;

import static com.kobi.territory.analytics.domain.Fixtures.EXPLORER;
import static com.kobi.territory.analytics.domain.Fixtures.JOURNEY;
import static com.kobi.territory.analytics.domain.Fixtures.TODAY;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("탐험가 여정")
class ExplorerJourneyTest {

    @Nested
    @DisplayName("체크인")
    class CheckIn {

        @Test
        @DisplayName("가입한 탐험가의 처음 체크인이 첫 체크인이고, 다음 날부터 7일 안이 재방문 기간이다")
        void firstCheckIn() {
            ExplorerJourney journey = ExplorerJourney.begin(EXPLORER, TODAY);

            assertThat(journey.checkedIn(TODAY.plusDays(1), JOURNEY)).isTrue();
            assertThat(journey.firstCheckInDay()).hasValue(TODAY.plusDays(1));
            assertThat(journey.revisitDeadline()).hasValue(TODAY.plusDays(8));
        }

        @Test
        @DisplayName("두 번째 체크인부터는 첫 체크인이 아니다 — 같은 사실이 다시 와도 날짜가 바뀌지 않는다")
        void onlyOnce() {
            ExplorerJourney journey = ExplorerJourney.begin(EXPLORER, TODAY);
            journey.checkedIn(TODAY, JOURNEY);

            assertThat(journey.checkedIn(TODAY.plusDays(3), JOURNEY)).isFalse();
            assertThat(journey.firstCheckInDay()).hasValue(TODAY);
        }

        @Test
        @DisplayName("분석을 켜기 전에 가입한 탐험가는 처음 본 체크인을 첫 체크인으로 치지 않는다")
        void unknownStart() {
            ExplorerJourney journey = ExplorerJourney.unknownStart(EXPLORER);

            assertThat(journey.checkedIn(TODAY, JOURNEY)).isFalse();
            assertThat(journey.firstCheckInDay()).isEmpty();
        }
    }

    @Nested
    @DisplayName("가입")
    class Created {

        @Test
        @DisplayName("가입 사실이 체크인보다 늦게 와도 가입일은 채워지고, 이미 있으면 바뀌지 않는다")
        void lateCreated() {
            ExplorerJourney journey = ExplorerJourney.unknownStart(EXPLORER);

            assertThat(journey.created(TODAY)).isTrue();
            assertThat(journey.created(TODAY.plusDays(1))).isFalse();
            assertThat(journey.createdDay()).hasValue(TODAY);
        }
    }

    @Nested
    @DisplayName("초대 합류")
    class InvitedJoin {

        @Test
        @DisplayName("가입하고 7일 안에 초대로 공유 지도에 합류하면 초대 유입이다")
        void withinWindow() {
            ExplorerJourney journey = ExplorerJourney.begin(EXPLORER, TODAY);

            journey.joinedByInvite(TODAY.plusDays(7), JOURNEY);

            assertThat(journey.inviteAcquired()).isTrue();
        }

        @Test
        @DisplayName("가입하고 8일째 처음 초대로 합류하면 초대 유입이 아니다(이미 들어와 있던 사람)")
        void afterWindow() {
            ExplorerJourney journey = ExplorerJourney.begin(EXPLORER, TODAY);

            journey.joinedByInvite(TODAY.plusDays(8), JOURNEY);

            assertThat(journey.inviteAcquired()).isFalse();
            assertThat(journey.invitedJoinDay()).hasValue(TODAY.plusDays(8));
        }

        @Test
        @DisplayName("처음 초대 합류만 센다 — 나중에 다른 지도에 초대로 합류해도 바뀌지 않는다")
        void firstJoinOnly() {
            ExplorerJourney journey = ExplorerJourney.begin(EXPLORER, TODAY);
            journey.joinedByInvite(TODAY.plusDays(20), JOURNEY);

            assertThat(journey.joinedByInvite(TODAY.plusDays(21), JOURNEY)).isFalse();
            assertThat(journey.inviteAcquired()).isFalse();
        }

        @Test
        @DisplayName("가입일을 모르는 탐험가의 초대 합류는 초대 유입이 아니다")
        void unknownStart() {
            ExplorerJourney journey = ExplorerJourney.unknownStart(EXPLORER);

            journey.joinedByInvite(TODAY, JOURNEY);

            assertThat(journey.inviteAcquired()).isFalse();
        }
    }
}
