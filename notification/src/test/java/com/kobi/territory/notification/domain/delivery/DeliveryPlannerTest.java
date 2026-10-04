package com.kobi.territory.notification.domain.delivery;

import static com.kobi.territory.notification.domain.Fixtures.계절_캠페인;
import static com.kobi.territory.notification.domain.Fixtures.미스터리_문구;
import static com.kobi.territory.notification.domain.Fixtures.미스터리_캠페인;
import static com.kobi.territory.notification.domain.Fixtures.발송_규칙;
import static com.kobi.territory.notification.domain.Fixtures.월요일;
import static com.kobi.territory.notification.domain.Fixtures.탐험가;
import static com.kobi.territory.notification.domain.Fixtures.서울;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.notification.domain.campaign.Campaign;
import com.kobi.territory.notification.domain.campaign.CampaignMessages;
import com.kobi.territory.notification.domain.campaign.SeasonStart;
import com.kobi.territory.notification.domain.policy.Reach;
import com.kobi.territory.notification.domain.push.PushMessage;
import com.kobi.territory.notification.domain.push.SendReport;
import java.time.Instant;
import java.time.MonthDay;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("알림 계획")
class DeliveryPlannerTest {

    private static final Instant 지금 = 서울(월요일, 9, 0);
    private static final PushMessage 계절_문구 = CampaignMessages.seasonStart(
        new SeasonStart("autumn", "단풍 명소", "🍁", MonthDay.of(10, 1), MonthDay.of(11, 30)), "autumn-2026");

    private PlanResult 계획(Reach reach, ScheduledDeliveries scheduled) {
        return DeliveryPlanner.plan(탐험가, reach, 미스터리_캠페인(), 미스터리_문구(), scheduled, 발송_규칙, 지금);
    }

    private ScheduledDeliveries 그날_기록(PushDelivery... deliveries) {
        return ScheduledDeliveries.of(월요일, List.of(deliveries), false);
    }

    private PushDelivery 계절_알림(DeliveryStatus status) {
        Campaign season = 계절_캠페인(월요일);
        PushDelivery delivery = PushDelivery.schedule(new DeliveryKey(탐험가, season.kind(), season.period()), 계절_문구, season, 지금);
        if (status == DeliveryStatus.PENDING) return delivery;
        delivery.claim(season.dueAt(), 발송_규칙);
        if (status == DeliveryStatus.SENDING) return delivery;
        delivery.complete(SendReport.of(List.of()), season.dueAt(), season.dueAt(), 발송_규칙);  // 기기 없음 → 닫힘
        return delivery;
    }

    @Nested
    @DisplayName("기기가 있고 그 종류를 켜 둔 사람에게")
    class Reachable {

        @Test
        @DisplayName("보낼 시각에 발송 기록을 만든다")
        void planned() {
            PlanResult result = 계획(Reach.REACHABLE, 그날_기록());

            assertThat(result.decision()).isEqualTo(PlanDecision.PLANNED);
            PushDelivery delivery = result.delivery().orElseThrow();
            assertThat(delivery.status()).isEqualTo(DeliveryStatus.PENDING);
            assertThat(delivery.key().period()).isEqualTo("2026-10-05");
            assertThat(delivery.nextAttemptAt()).isEqualTo(지금);
        }

        @Test
        @DisplayName("같은 주의 미스터리 알림은 두 번 계획하지 않는다")
        void idempotent() {
            assertThat(계획(Reach.REACHABLE, ScheduledDeliveries.of(월요일, List.of(), true)).decision())
                .isEqualTo(PlanDecision.ALREADY_PLANNED);
        }
    }

    @Nested
    @DisplayName("동의·설정을 존중해")
    class Consent {

        @Test
        @DisplayName("기기가 없는 사람에게는 계획하지 않는다")
        void noDevice() {
            assertThat(계획(Reach.NO_DEVICE, 그날_기록()).decision()).isEqualTo(PlanDecision.NO_DEVICE);
        }

        @Test
        @DisplayName("그 종류를 끈 사람에게는 계획하지 않는다")
        void kindOff() {
            assertThat(계획(Reach.KIND_OFF, 그날_기록()).decision()).isEqualTo(PlanDecision.KIND_OFF);
        }
    }

    @Nested
    @DisplayName("같은 날 이미 받을 알림이 있으면")
    class DailyLimit {

        @Test
        @DisplayName("하루 한 개까지만 — 계절 시작 알림이 먼저 계획된 날에는 미스터리 알림을 보내지 않는다")
        void onePerDay() {
            assertThat(계획(Reach.REACHABLE, 그날_기록(계절_알림(DeliveryStatus.PENDING))).decision()).isEqualTo(PlanDecision.DAILY_LIMIT);
            assertThat(계획(Reach.REACHABLE, 그날_기록(계절_알림(DeliveryStatus.SENDING))).decision()).isEqualTo(PlanDecision.DAILY_LIMIT);
        }

        @Test
        @DisplayName("그날 알림이 보내지지 못하고 닫혔으면 다른 알림을 받을 수 있다")
        void closedOneDoesNotCount() {
            assertThat(계획(Reach.REACHABLE, 그날_기록(계절_알림(DeliveryStatus.CANCELLED))).decision()).isEqualTo(PlanDecision.PLANNED);
        }

        @Test
        @DisplayName("다른 날의 기록과 섞어 판단하지 않는다")
        void otherDay() {
            assertThatThrownBy(() -> ScheduledDeliveries.of(월요일.plusDays(1), List.of(계절_알림(DeliveryStatus.PENDING)), false))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
