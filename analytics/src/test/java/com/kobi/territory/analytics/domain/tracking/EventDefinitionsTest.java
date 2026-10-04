package com.kobi.territory.analytics.domain.tracking;

import static com.kobi.territory.analytics.domain.Fixtures.EXPLORER;
import static com.kobi.territory.analytics.domain.Fixtures.EXPLORER_ID;
import static com.kobi.territory.analytics.domain.Fixtures.INGEST;
import static com.kobi.territory.analytics.domain.Fixtures.NOW;
import static com.kobi.territory.analytics.domain.Fixtures.VISITOR;
import static com.kobi.territory.analytics.domain.Fixtures.탐험가로;
import static com.kobi.territory.analytics.domain.Fixtures.휴대폰으로;
import static com.kobi.territory.analytics.domain.Fixtures.화면;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("받을 수 있는 이벤트")
class EventDefinitionsTest {

    private final EventDefinitions definitions = EventDefinitions.standard();

    private TrackedEvent 읽는다(SubmittedEvent submitted) {
        return definitions.require(submitted.name()).client(submitted, 휴대폰으로(VISITOR), INGEST);
    }

    private RejectionReason 거절_이유(SubmittedEvent submitted) {
        try {
            읽는다(submitted);
            throw new AssertionError("받아들여졌다: " + submitted);
        } catch (InvalidTrackedEvent invalid) {
            return invalid.reason();
        }
    }

    @Nested
    @DisplayName("화면 이벤트")
    class ClientEvents {

        @Test
        @DisplayName("정해진 이름과 필드면 받고, 갈래 값(탭 이름)을 따로 적는다")
        void accepted() {
            TrackedEvent event = 읽는다(화면(EventDefinitions.TAB_VIEW, "tab", "quests"));

            assertThat(event.name()).isEqualTo("tab_view");
            assertThat(event.source()).isEqualTo(EventSource.CLIENT);
            assertThat(event.label()).isEqualTo("quests");
            assertThat(event.properties().view()).containsExactly(Map.entry("tab", "quests"));
        }

        @Test
        @DisplayName("필드가 없는 이벤트(체크인 창 열기)는 빈 필드로 적힌다")
        void noFields() {
            assertThat(읽는다(화면(EventDefinitions.CHECKIN_OPEN)).properties().isEmpty()).isTrue();
        }

        @Test
        @DisplayName("정해진 목록에 없는 탭 이름은 받지 않는다")
        void unknownTab() {
            assertThat(거절_이유(화면(EventDefinitions.TAB_VIEW, "tab", "shop"))).isEqualTo(RejectionReason.INVALID_FIELD);
        }

        @Test
        @DisplayName("꼭 있어야 하는 필드가 없으면 받지 않는다")
        void missingField() {
            assertThat(거절_이유(화면(EventDefinitions.ERROR_TOAST))).isEqualTo(RejectionReason.MISSING_FIELD);
        }

        @Test
        @DisplayName("정의되지 않은 필드가 하나라도 있으면 받지 않는다")
        void unknownField() {
            assertThat(거절_이유(화면(EventDefinitions.TAB_VIEW, "tab", "map", "color", "red")))
                .isEqualTo(RejectionReason.UNKNOWN_FIELD);
        }

        @Test
        @DisplayName("오류 토스트는 서버 오류 코드 형식만 받는다 — 문장은 받지 않는다")
        void errorCodeOnly() {
            assertThat(읽는다(화면(EventDefinitions.ERROR_TOAST, "code", "DAILY_CAP_EXCEEDED")).label()).isEqualTo("DAILY_CAP_EXCEEDED");
            assertThat(거절_이유(화면(EventDefinitions.ERROR_TOAST, "code", "하루 상한을 넘었어요")))
                .isEqualTo(RejectionReason.INVALID_FIELD);
        }

        @Test
        @DisplayName("온보딩 단계는 0~1000 사이의 정수만 받는다")
        void smallNumber() {
            assertThat(읽는다(화면(EventDefinitions.ONBOARDING_STEP, "step", 3)).label()).isEqualTo("3");
            assertThat(거절_이유(화면(EventDefinitions.ONBOARDING_STEP, "step", 2.5))).isEqualTo(RejectionReason.INVALID_FIELD);
            assertThat(거절_이유(화면(EventDefinitions.ONBOARDING_STEP, "step", 5000))).isEqualTo(RejectionReason.INVALID_FIELD);
            assertThat(거절_이유(화면(EventDefinitions.ONBOARDING_STEP, "step", "3"))).isEqualTo(RejectionReason.INVALID_FIELD);
        }

        @Test
        @DisplayName("가입·체크인처럼 서버가 확실히 아는 사실은 화면이 보내도 받지 않는다")
        void serverOnly() {
            assertThat(거절_이유(화면(EventDefinitions.CHECK_IN, "rarity", "common"))).isEqualTo(RejectionReason.SERVER_ONLY_EVENT);
            assertThat(거절_이유(화면(EventDefinitions.EXPLORER_CREATED))).isEqualTo(RejectionReason.SERVER_ONLY_EVENT);
        }
    }

    @Nested
    @DisplayName("알림")
    class Notifications {

        @Test
        @DisplayName("알림을 눌러 열었다는 화면 이벤트는 알림 종류를 갈래로 적는다")
        void pushOpen() {
            TrackedEvent event = 읽는다(화면(EventDefinitions.PUSH_OPEN, "kind", "streak"));

            assertThat(event.label()).isEqualTo("streak");
        }

        @Test
        @DisplayName("모르는 알림 종류로 열었다는 이벤트는 받지 않는다")
        void unknownPushKind() {
            assertThat(거절_이유(화면(EventDefinitions.PUSH_OPEN, "kind", "promo"))).isEqualTo(RejectionReason.INVALID_FIELD);
        }

        @Test
        @DisplayName("알림으로 들어온 첫 화면은 들어온 길을 알림으로 적는다")
        void appOpenFromPush() {
            assertThat(읽는다(화면(EventDefinitions.APP_OPEN, "entry", "push")).label()).isEqualTo("push");
        }

        @Test
        @DisplayName("알림을 보냈다는 사실은 서버만 적는다 — 화면이 보내면 받지 않는다")
        void pushSentIsServerFact() {
            ServerFact fact = new ServerFact(EventDefinitions.PUSH_SENT, EXPLORER_ID, NOW, Map.of("kind", "mystery", "devices", 2),
                "PushSent|…");

            assertThat(definitions.require(fact.name()).server(fact, EXPLORER, "지문", INGEST).label()).isEqualTo("mystery");
            assertThat(거절_이유(화면(EventDefinitions.PUSH_SENT, "kind", "mystery"))).isEqualTo(RejectionReason.SERVER_ONLY_EVENT);
        }
    }

    @Nested
    @DisplayName("개인정보")
    class PersonalData {

        @ParameterizedTest(name = "{0} 필드가 오면 그 이벤트는 통째로 버린다")
        @ValueSource(strings = {"memo", "handle", "email", "ip", "userAgent", "user_agent", "lat", "lng", "explorerId", "token",
            "Address", "nickname"})
        @DisplayName("메모·handle·이메일·IP·User-Agent·위치·탐험가 id 같은 필드가 오면 그 이벤트는 통째로 버린다")
        void personalFieldDropsEvent(String key) {
            assertThat(거절_이유(화면(EventDefinitions.TAB_VIEW, "tab", "map", key, "x"))).isEqualTo(RejectionReason.PERSONAL_DATA);
        }

        @Test
        @DisplayName("화면 이벤트에는 탐험가 id 가 아니라 서버 비밀값을 섞은 해시만 남는다")
        void onlyHash() {
            TrackedEvent event = definitions.require(EventDefinitions.APP_OPEN)
                .client(화면(EventDefinitions.APP_OPEN, "entry", "direct"), 탐험가로(VISITOR, EXPLORER), INGEST);

            assertThat(event.explorerHash()).isEqualTo(EXPLORER);
            assertThat(event.toString()).doesNotContain(EXPLORER_ID);
        }
    }

    @Nested
    @DisplayName("서버 사실")
    class ServerFacts {

        @Test
        @DisplayName("주인공 탐험가는 해시로, 같은 사실은 같은 지문으로 적힌다")
        void hashedAndFingerprinted() {
            ServerFact fact = new ServerFact(EventDefinitions.CHECK_IN, EXPLORER_ID, NOW, Map.of("rarity", "rare"), "RegionVisited|…");

            TrackedEvent event = definitions.require(fact.name()).server(fact, EXPLORER, "지문", INGEST);

            assertThat(event.actor().value()).isEqualTo(EXPLORER.value());
            assertThat(event.label()).isEqualTo("rare");
            assertThat(event.dedupKey()).isEqualTo("지문");
            assertThat(event.toString()).doesNotContain(EXPLORER_ID);
        }

        @Test
        @DisplayName("서버 사실도 같은 필드 규칙을 지난다 — 개인정보가 실수로 섞이면 적지 않는다")
        void serverFactsAreValidatedToo() {
            ServerFact fact = new ServerFact(EventDefinitions.QUEST_CLAIMED, EXPLORER_ID, NOW, Map.of("quest", "m3", "memo", "비밀"),
                "QuestCompleted|…");

            assertThatThrownBy(() -> definitions.require(fact.name()).server(fact, EXPLORER, "지문", INGEST))
                .isInstanceOfSatisfying(InvalidTrackedEvent.class,
                    invalid -> assertThat(invalid.reason()).isEqualTo(RejectionReason.PERSONAL_DATA));
        }
    }

    @Nested
    @DisplayName("목록")
    class Catalog {

        @Test
        @DisplayName("기능별 사용률에는 사용자가 고르는 기능만 들어간다")
        void features() {
            assertThat(definitions.featureNames()).contains("tab_view", "checkin_open", "check_in", "shared_map_joined", "account_linked")
                .doesNotContain("app_open", "error_toast", "explorer_created", "first_check_in", "card_view");
        }

        @Test
        @DisplayName("화면 이벤트 열한 가지, 서버 사실 열다섯 가지, 공개 페이지 열람 세 가지를 받는다")
        void sizes() {
            assertThat(definitions.from(EventSource.CLIENT)).hasSize(11);
            assertThat(definitions.from(EventSource.SERVER)).hasSize(15);
            assertThat(definitions.from(EventSource.REQUEST)).hasSize(3);
        }
    }
}
