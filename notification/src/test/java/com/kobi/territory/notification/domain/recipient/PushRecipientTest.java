package com.kobi.territory.notification.domain.recipient;

import static com.kobi.territory.notification.domain.Fixtures.기기_규칙;
import static com.kobi.territory.notification.domain.Fixtures.다른_탐험가;
import static com.kobi.territory.notification.domain.Fixtures.월요일;
import static com.kobi.territory.notification.domain.Fixtures.주소_규칙;
import static com.kobi.territory.notification.domain.Fixtures.크롬;
import static com.kobi.territory.notification.domain.Fixtures.키;
import static com.kobi.territory.notification.domain.Fixtures.탐험가;
import static com.kobi.territory.notification.domain.Fixtures.서울;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.common.error.TerritoryException;
import com.kobi.territory.notification.domain.policy.NotificationKind;
import com.kobi.territory.notification.domain.policy.Reach;
import com.kobi.territory.notification.domain.push.PushEndpoint;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("알림 받는 사람")
class PushRecipientTest {

    private static final Instant 아침 = 서울(월요일, 9, 0);

    private PushRecipient 처음() {
        return PushRecipient.start(탐험가, 아침);
    }

    private PushRecipient 기기와(PushEndpoint... endpoints) {
        PushRecipient recipient = 처음();
        for (int i = 0; i < endpoints.length; i++) recipient.subscribe(endpoints[i], 키(), 아침.plusSeconds(i), 기기_규칙);
        return recipient;
    }

    @Nested
    @DisplayName("이 브라우저로 알림을 받겠다고 하면")
    class Subscribe {

        @Test
        @DisplayName("새 기기로 등록된다")
        void added() {
            SubscribeResult result = 처음().subscribe(크롬("a"), 키(), 아침, 기기_규칙);

            assertThat(result.added()).isTrue();
            assertThat(result.deviceCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("같은 브라우저가 다시 보내면 기기가 늘지 않고 키만 새로 바뀐다")
        void renewed() {
            PushRecipient recipient = 기기와(크롬("a"));

            SubscribeResult again = recipient.subscribe(크롬("a"), 키(), 아침.plusSeconds(60), 기기_규칙);

            assertThat(again.added()).isFalse();
            assertThat(recipient.devices().count()).isEqualTo(1);
            assertThat(recipient.devices().find(크롬("a")).orElseThrow().registeredAt()).isEqualTo(아침.plusSeconds(60));
        }

        @Test
        @DisplayName("기기가 정해진 수를 넘으면 가장 오래 전에 등록한 기기가 빠진다 — 지금 켠 브라우저는 꼭 받는다")
        void evictsOldest() {
            PushRecipient recipient = 기기와(크롬("a"), 크롬("b"), 크롬("c"));

            SubscribeResult result = recipient.subscribe(크롬("d"), 키(), 아침.plusSeconds(100), 기기_규칙);

            assertThat(result.evicted()).containsExactly(크롬("a"));
            assertThat(recipient.devices().stream().map(PushDevice::endpoint)).containsExactly(크롬("b"), 크롬("c"), 크롬("d"));
            assertThat(recipient.devices().removed()).containsExactly(크롬("a"));
        }

        @Test
        @DisplayName("알려진 브라우저 알림 서비스가 아닌 주소는 받지 않는다")
        void unknownService() {
            assertThatThrownBy(() -> 처음().subscribe(PushEndpoint.of("https://evil.example.com/hook"), 키(), 아침, 기기_규칙))
                .isInstanceOfSatisfying(TerritoryException.class,
                    rejected -> assertThat(rejected.code()).isEqualTo("PUSH_ENDPOINT_NOT_ALLOWED"));
        }

        @Test
        @DisplayName("하위 도메인 규칙은 그 도메인의 하위 주소만 받는다 — 이름만 비슷한 주소는 받지 않는다")
        void subdomainRule() {
            assertThat(주소_규칙.allows(PushEndpoint.of("https://web.push.apple.com/abc"))).isTrue();
            assertThat(주소_규칙.allows(PushEndpoint.of("https://push.apple.com.evil.example/abc"))).isFalse();
            assertThat(주소_규칙.allows(PushEndpoint.of("https://push.apple.com/abc"))).isFalse();
        }

        @Test
        @DisplayName("암호화되지 않은 주소는 받지 않는다 — 개발용 로컬 주소만 예외로 켤 수 있다")
        void httpsOnly() {
            PushEndpoint local = PushEndpoint.of("http://localhost:18081/dev/push/inbox/box");

            assertThat(주소_규칙.allows(PushEndpoint.of("http://fcm.googleapis.com/fcm/send/a"))).isFalse();
            assertThat(주소_규칙.allows(local)).isFalse();
            assertThat(new EndpointRules(List.of(), true).allows(local)).isTrue();
        }
    }

    @Nested
    @DisplayName("해지하면")
    class Unsubscribe {

        @Test
        @DisplayName("그 기기로는 더 받지 않는다")
        void removed() {
            PushRecipient recipient = 기기와(크롬("a"), 크롬("b"));

            assertThat(recipient.unsubscribe(크롬("a"), 아침)).isTrue();
            assertThat(recipient.devices().stream().map(PushDevice::endpoint)).containsExactly(크롬("b"));
        }

        @Test
        @DisplayName("없는 기기를 해지해도 아무 일도 없다")
        void idempotent() {
            PushRecipient recipient = 기기와(크롬("a"));

            assertThat(recipient.unsubscribe(크롬("z"), 아침)).isFalse();
            assertThat(recipient.devices().count()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("알림 서비스가 구독이 없어졌다고 하면")
    class Gone {

        @Test
        @DisplayName("그 기기를 지운다")
        void forgotten() {
            PushRecipient recipient = 기기와(크롬("a"), 크롬("b"));

            assertThat(recipient.forgetGone(List.of(크롬("a")), 아침.plusSeconds(300), 아침.plusSeconds(301))).containsExactly(크롬("a"));
            assertThat(recipient.devices().count()).isEqualTo(1);
        }

        @Test
        @DisplayName("보낸 뒤에 다시 구독한 기기는 남긴다")
        void resubscribedMeanwhile() {
            PushRecipient recipient = 기기와(크롬("a"));
            recipient.subscribe(크롬("a"), 키(), 아침.plusSeconds(600), 기기_규칙);

            assertThat(recipient.forgetGone(List.of(크롬("a")), 아침.plusSeconds(300), 아침.plusSeconds(700))).isEmpty();
            assertThat(recipient.devices().count()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("알림이 닿는지는")
    class Reachability {

        @Test
        @DisplayName("기기가 없으면 아무 알림도 닿지 않는다 — 동의하지 않은 사람에게는 보내지 않는다")
        void noDevice() {
            assertThat(처음().reach(NotificationKind.WEEKLY_MYSTERY)).isEqualTo(Reach.NO_DEVICE);
        }

        @Test
        @DisplayName("처음에는 세 종류 모두 켜져 있다")
        void allOnByDefault() {
            PushRecipient recipient = 기기와(크롬("a"));

            assertThat(List.of(NotificationKind.values())).allSatisfy(kind -> assertThat(recipient.reach(kind)).isEqualTo(Reach.REACHABLE));
        }

        @Test
        @DisplayName("끈 종류는 닿지 않고 나머지는 그대로 닿는다")
        void kindOff() {
            PushRecipient recipient = 기기와(크롬("a"));
            recipient.changePreferences(new NotificationPreferences(true, false, true), 아침);

            assertThat(recipient.reach(NotificationKind.STREAK_GUARD)).isEqualTo(Reach.KIND_OFF);
            assertThat(recipient.reach(NotificationKind.SEASON_START)).isEqualTo(Reach.REACHABLE);
        }

        @Test
        @DisplayName("설정은 세 종류를 모두 정해서 보내야 한다")
        void allThreeRequired() {
            assertThatThrownBy(() -> NotificationPreferences.of(true, null, false)).isInstanceOfSatisfying(TerritoryException.class,
                rejected -> assertThat(rejected.code()).isEqualTo("INVALID_PUSH_PREFERENCES"));
        }
    }

    @Nested
    @DisplayName("익명 탐험가가 계정에 병합되면")
    class Merge {

        @Test
        @DisplayName("익명 쪽 기기가 계정으로 옮겨 오고 익명 쪽에는 남지 않는다")
        void movesDevices() {
            PushRecipient account = 기기와(크롬("a"));
            PushRecipient anonymous = PushRecipient.start(다른_탐험가, 아침);
            anonymous.subscribe(크롬("phone"), 키(), 아침, 기기_규칙);

            assertThat(account.absorb(anonymous, 아침, 기기_규칙)).isEqualTo(1);
            assertThat(account.devices().stream().map(PushDevice::endpoint)).containsExactlyInAnyOrder(크롬("a"), 크롬("phone"));
            assertThat(anonymous.devices().isEmpty()).isTrue();
            assertThat(anonymous.devices().removed()).containsExactly(크롬("phone"));
        }

        @Test
        @DisplayName("같은 브라우저면 한 기기로 남고, 계정의 알림 설정은 그대로다")
        void sameBrowser() {
            PushRecipient account = 기기와(크롬("a"));
            account.changePreferences(new NotificationPreferences(false, true, true), 아침);
            PushRecipient anonymous = PushRecipient.start(다른_탐험가, 아침);
            anonymous.subscribe(크롬("a"), 키(), 아침.plusSeconds(5), 기기_규칙);

            account.absorb(anonymous, 아침, 기기_규칙);

            assertThat(account.devices().count()).isEqualTo(1);
            assertThat(account.preferences().mystery()).isFalse();
        }
    }
}
