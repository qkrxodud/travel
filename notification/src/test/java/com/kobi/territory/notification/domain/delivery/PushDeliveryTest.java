package com.kobi.territory.notification.domain.delivery;

import static com.kobi.territory.notification.domain.Fixtures.거절;
import static com.kobi.territory.notification.domain.Fixtures.달력;
import static com.kobi.territory.notification.domain.Fixtures.미스터리_문구;
import static com.kobi.territory.notification.domain.Fixtures.미스터리_캠페인;
import static com.kobi.territory.notification.domain.Fixtures.발송_규칙;
import static com.kobi.territory.notification.domain.Fixtures.받음;
import static com.kobi.territory.notification.domain.Fixtures.사라짐;
import static com.kobi.territory.notification.domain.Fixtures.월요일;
import static com.kobi.territory.notification.domain.Fixtures.잠시_실패;
import static com.kobi.territory.notification.domain.Fixtures.크롬;
import static com.kobi.territory.notification.domain.Fixtures.탐험가;
import static com.kobi.territory.notification.domain.Fixtures.서울;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.notification.domain.campaign.Campaign;
import com.kobi.territory.notification.domain.policy.Reach;
import com.kobi.territory.notification.domain.push.SendReport;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("발송 기록")
class PushDeliveryTest {

    private static final Instant 아홉시 = 서울(월요일, 9, 0);

    private PushDelivery 계획된_알림() {
        Campaign campaign = 미스터리_캠페인();
        return PushDelivery.schedule(new DeliveryKey(탐험가, campaign.kind(), campaign.period()), 미스터리_문구(), campaign, 아홉시);
    }

    private PushDelivery 보내는_중(Instant claim) {
        PushDelivery delivery = 계획된_알림();
        assertThat(delivery.claim(claim, 발송_규칙)).isEqualTo(ClaimOutcome.CLAIMED);
        return delivery;
    }

    @Nested
    @DisplayName("발송기가 잡을 때")
    class Claim {

        @Test
        @DisplayName("보낼 시각이 되면 잡아서 보내는 중이 된다")
        void due() {
            PushDelivery delivery = 계획된_알림();

            assertThat(delivery.claim(아홉시, 발송_규칙)).isEqualTo(ClaimOutcome.CLAIMED);
            assertThat(delivery.status()).isEqualTo(DeliveryStatus.SENDING);
            assertThat(delivery.claimedAt()).contains(아홉시);
        }

        @Test
        @DisplayName("보낼 시각 전에는 잡지 않는다")
        void notYet() {
            assertThat(계획된_알림().claim(아홉시.minusSeconds(1), 발송_규칙)).isEqualTo(ClaimOutcome.NOT_DUE);
        }

        @Test
        @DisplayName("다른 발송기가 보내는 중이면 잡지 않고, 그 발송기가 멈춘 채 오래 지나면 다시 잡는다")
        void staleClaim() {
            PushDelivery delivery = 보내는_중(아홉시);

            assertThat(delivery.claim(아홉시.plus(Duration.ofMinutes(9)), 발송_규칙)).isEqualTo(ClaimOutcome.NOT_DUE);
            assertThat(delivery.claim(아홉시.plus(Duration.ofMinutes(10)), 발송_규칙)).isEqualTo(ClaimOutcome.CLAIMED);
        }

        @Test
        @DisplayName("보낼 날이 지났으면 다음 날 늦게 보내지 않고 만료로 닫는다")
        void nextDayExpires() {
            PushDelivery delivery = 계획된_알림();

            assertThat(delivery.claim(서울(월요일.plusDays(1), 9, 0), 발송_규칙)).isEqualTo(ClaimOutcome.EXPIRED);
            assertThat(delivery.status()).isEqualTo(DeliveryStatus.EXPIRED);
        }

        @Test
        @DisplayName("개발용 즉시 발송은 조용한 시간에도 보낸다 — 날짜가 바뀌면 만료")
        void immediate() {
            Campaign forced = 달력.weeklyMystery(월요일, 서울(월요일, 23, 0), true).orElseThrow();
            PushDelivery delivery = PushDelivery.schedule(new DeliveryKey(탐험가, forced.kind(), forced.period()), 미스터리_문구(), forced,
                서울(월요일, 23, 0));

            assertThat(delivery.claim(서울(월요일, 23, 1), 발송_규칙)).isEqualTo(ClaimOutcome.CLAIMED);
            assertThat(delivery.sendableAt(서울(월요일.plusDays(1), 0, 1), 발송_규칙)).isFalse();
        }

        @Test
        @DisplayName("조용한 시간이 되었으면 만료로 닫는다")
        void quietHoursExpire() {
            assertThat(계획된_알림().claim(서울(월요일, 22, 30), 발송_규칙)).isEqualTo(ClaimOutcome.EXPIRED);
        }
    }

    @Nested
    @DisplayName("보내기 직전에 다시 보면")
    class Reconfirm {

        @Test
        @DisplayName("그새 그 종류를 껐거나 기기를 모두 해지했으면 보내지 않고 닫는다")
        void cancelled() {
            PushDelivery off = 보내는_중(아홉시);
            PushDelivery gone = 보내는_중(아홉시);

            assertThat(off.confirmReach(Reach.KIND_OFF, 아홉시)).isFalse();
            assertThat(gone.confirmReach(Reach.NO_DEVICE, 아홉시)).isFalse();
            assertThat(off.status()).isEqualTo(DeliveryStatus.CANCELLED);
            assertThat(off.lastError()).contains("KIND_OFF");
            assertThat(gone.lastError()).contains("NO_DEVICE");
        }

        @Test
        @DisplayName("여전히 닿으면 보낸다")
        void stillReachable() {
            assertThat(보내는_중(아홉시).confirmReach(Reach.REACHABLE, 아홉시)).isTrue();
        }
    }

    @Nested
    @DisplayName("보낸 결과로")
    class Complete {

        @Test
        @DisplayName("한 기기라도 받으면 보냄으로 닫고, 보냈다는 사실을 남긴다")
        void sent() {
            PushDelivery delivery = 보내는_중(아홉시);

            delivery.complete(SendReport.of(List.of(받음(크롬("a")), 잠시_실패(크롬("b"), Duration.ZERO))), 아홉시, 아홉시.plusSeconds(1),
                발송_규칙);

            assertThat(delivery.status()).isEqualTo(DeliveryStatus.SENT);
            assertThat(delivery.sent()).hasValueSatisfying(sent -> {
                assertThat(sent.devices()).isEqualTo(1);
                assertThat(sent.key().period()).isEqualTo("2026-10-05");
            });
        }

        @Test
        @DisplayName("보내지 못했으면 보냈다는 사실이 없다")
        void notSentNoFact() {
            PushDelivery delivery = 보내는_중(아홉시);
            delivery.complete(SendReport.of(List.of(거절(크롬("a")))), 아홉시, 아홉시, 발송_규칙);

            assertThat(delivery.sent()).isEmpty();
        }

        @Test
        @DisplayName("모든 기기 구독이 없어졌으면 기기 없음으로 닫는다")
        void allGone() {
            PushDelivery delivery = 보내는_중(아홉시);

            assertThat(delivery.complete(SendReport.of(List.of(사라짐(크롬("a")))), 아홉시, 아홉시, 발송_규칙))
                .isEqualTo(DeliveryStatus.CANCELLED);
        }

        @Test
        @DisplayName("잠시 실패면 점점 늘어나는 간격으로 다시 보낸다 — 알림 서비스가 기다리라는 시간이 더 길면 그만큼")
        void retryWithBackoff() {
            PushDelivery delivery = 보내는_중(아홉시);

            delivery.complete(SendReport.of(List.of(잠시_실패(크롬("a"), Duration.ZERO))), 아홉시, 아홉시, 발송_규칙);
            assertThat(delivery.status()).isEqualTo(DeliveryStatus.PENDING);
            assertThat(delivery.nextAttemptAt()).isEqualTo(아홉시.plus(Duration.ofMinutes(5)));

            Instant second = delivery.nextAttemptAt();
            delivery.claim(second, 발송_규칙);
            delivery.complete(SendReport.of(List.of(잠시_실패(크롬("a"), Duration.ofMinutes(30)))), second, second, 발송_규칙);
            assertThat(delivery.nextAttemptAt()).isEqualTo(second.plus(Duration.ofMinutes(30)));
            assertThat(delivery.attempts()).isEqualTo(2);
        }

        @Test
        @DisplayName("정해진 횟수만큼 보내도 안 되면 실패로 닫는다")
        void exhausted() {
            PushDelivery delivery = 계획된_알림();
            Instant at = 아홉시;
            for (int i = 0; i < 4; i++) {
                delivery.claim(at, 발송_규칙);
                delivery.complete(SendReport.of(List.of(잠시_실패(크롬("a"), Duration.ZERO))), at, at, 발송_규칙);
                at = delivery.nextAttemptAt();
            }

            assertThat(delivery.status()).isEqualTo(DeliveryStatus.FAILED);
        }

        @Test
        @DisplayName("다시 보낼 시각이 조용한 시간에 걸리면 다음 날로 미루지 않고 만료로 닫는다")
        void retryIntoQuietHours() {
            Campaign campaign = 미스터리_캠페인();
            PushDelivery delivery = 계획된_알림();
            Instant late = 서울(월요일, 21, 58);
            delivery.claim(late, 발송_규칙);

            delivery.complete(SendReport.of(List.of(잠시_실패(크롬("a"), Duration.ZERO))), late, late, 발송_규칙);

            assertThat(campaign.deliveryDay()).isEqualTo(월요일);
            assertThat(delivery.status()).isEqualTo(DeliveryStatus.EXPIRED);
        }

        @Test
        @DisplayName("다시 보내도 안 되는 거절이면 실패로 닫는다")
        void rejected() {
            PushDelivery delivery = 보내는_중(아홉시);

            assertThat(delivery.complete(SendReport.of(List.of(거절(크롬("a")))), 아홉시, 아홉시, 발송_규칙))
                .isEqualTo(DeliveryStatus.FAILED);
        }

        @Test
        @DisplayName("그새 다른 발송기가 다시 잡았으면 결과를 쓰지 않는다")
        void reclaimed() {
            PushDelivery delivery = 보내는_중(아홉시);
            delivery.claim(아홉시.plus(Duration.ofMinutes(11)), 발송_규칙);

            assertThatThrownBy(() -> delivery.complete(SendReport.of(List.of(받음(크롬("a")))), 아홉시, 아홉시, 발송_규칙))
                .isInstanceOf(DeliveryReclaimed.class);
        }
    }
}
