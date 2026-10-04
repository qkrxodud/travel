package com.kobi.territory.analytics.domain.tracking;

import static com.kobi.territory.analytics.domain.Fixtures.INGEST;
import static com.kobi.territory.analytics.domain.Fixtures.NOW;
import static com.kobi.territory.analytics.domain.Fixtures.VISITOR;
import static com.kobi.territory.analytics.domain.Fixtures.봇으로;
import static com.kobi.territory.analytics.domain.Fixtures.휴대폰으로;
import static com.kobi.territory.analytics.domain.Fixtures.화면;
import static com.kobi.territory.analytics.domain.Fixtures.화면_시각;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.analytics.domain.actor.DeviceType;
import com.kobi.territory.analytics.domain.actor.EntryPoint;
import com.kobi.territory.analytics.domain.actor.VisitorSighting;
import com.kobi.territory.common.error.TerritoryException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("화면 이벤트 묶음")
class EventBatchTest {

    private final EventDefinitions definitions = EventDefinitions.standard();

    private BatchOutcome 받는다(List<SubmittedEvent> events) {
        return EventBatch.of(events, INGEST).accept(definitions, 휴대폰으로(VISITOR));
    }

    @Nested
    @DisplayName("묶음 크기")
    class Size {

        @Test
        @DisplayName("한 번에 50개까지 받는다")
        void upToLimit() {
            assertThat(받는다(Collections.nCopies(50, 화면(EventDefinitions.CHECKIN_OPEN))).accepted()).hasSize(50);
        }

        @Test
        @DisplayName("50개를 넘으면 묶음을 통째로 받지 않는다")
        void overLimit() {
            assertThatThrownBy(() -> EventBatch.of(Collections.nCopies(51, 화면(EventDefinitions.CHECKIN_OPEN)), INGEST))
                .isInstanceOfSatisfying(TerritoryException.class,
                    rejected -> assertThat(rejected.code()).isEqualTo("EVENT_BATCH_TOO_LARGE"));
        }
    }

    @Nested
    @DisplayName("묶음 안에 규칙에 맞지 않는 이벤트가 섞였을 때")
    class Mixed {

        @Test
        @DisplayName("그 이벤트만 이유와 함께 버리고 나머지는 적는다")
        void dropOnlyInvalid() {
            BatchOutcome outcome = 받는다(List.of(
                화면(EventDefinitions.APP_OPEN, "entry", "card"),
                화면("buy_item"),
                화면(EventDefinitions.CHECKIN_SAVE, "memo", "남산 산책"),
                화면(EventDefinitions.TAB_VIEW, "tab", "bag")));

            assertThat(outcome.accepted()).extracting(TrackedEvent::name).containsExactly("app_open", "tab_view");
            assertThat(outcome.rejected()).containsExactly(
                new Rejection(1, "buy_item", RejectionReason.UNKNOWN_EVENT),
                new Rejection(2, "checkin_save", RejectionReason.PERSONAL_DATA));
        }
    }

    @Nested
    @DisplayName("봇이 보냈을 때")
    class Bots {

        @Test
        @DisplayName("아무것도 적지 않고 오류로 돌려주지도 않는다")
        void ignored() {
            BatchOutcome outcome = EventBatch.of(List.of(화면(EventDefinitions.APP_OPEN, "entry", "direct")), INGEST)
                .accept(definitions, 봇으로(VISITOR));

            assertThat(outcome.ignoredBot()).isTrue();
            assertThat(outcome.accepted()).isEmpty();
            assertThat(outcome.rejected()).isEmpty();
        }

        @Test
        @DisplayName("방문으로도 세지 않는다")
        void notAVisit() {
            assertThat(EventBatch.of(List.of(화면(EventDefinitions.APP_OPEN, "entry", "direct")), INGEST)
                .sighting(definitions, VISITOR, null, DeviceType.BOT, NOW)).isEmpty();
        }
    }

    @Nested
    @DisplayName("이벤트 시각")
    class Time {

        @Test
        @DisplayName("화면이 잰 시각이 하루 안이면 그 시각으로 적는다(오프라인으로 모았다 보낸 이벤트)")
        void trustRecentClientTime() {
            Instant twoHoursAgo = NOW.minus(Duration.ofHours(2));

            assertThat(받는다(List.of(화면_시각(EventDefinitions.CHECKIN_OPEN, twoHoursAgo))).accepted().get(0).occurredAt())
                .isEqualTo(twoHoursAgo);
        }

        @Test
        @DisplayName("너무 옛날이거나 미래인 화면 시각은 믿지 않고 받은 시각으로 적는다")
        void distrustSkewedClientTime() {
            List<TrackedEvent> accepted = 받는다(List.of(
                화면_시각(EventDefinitions.CHECKIN_OPEN, NOW.minus(Duration.ofDays(3))),
                화면_시각(EventDefinitions.CHECKIN_OPEN, NOW.plus(Duration.ofMinutes(10))))).accepted();

            assertThat(accepted).extracting(TrackedEvent::occurredAt).containsOnly(NOW);
        }

        @Test
        @DisplayName("하루는 서울 날짜로 센다 — 세계시 15시 30분은 서울의 다음 날 0시 30분이다")
        void seoulDay() {
            Instant afterSeoulMidnight = Instant.parse("2026-10-04T15:30:00Z");
            ClientContext late = new ClientContext(VISITOR, null, 휴대폰으로(VISITOR).actor(), DeviceType.MOBILE, null,
                afterSeoulMidnight);

            TrackedEvent event = EventBatch.of(List.of(화면(EventDefinitions.CHECKIN_OPEN)), INGEST).accept(definitions, late)
                .accepted().get(0);

            assertThat(event.day()).isEqualTo(LocalDate.of(2026, 10, 5));
        }
    }

    @Nested
    @DisplayName("방문")
    class Visit {

        @Test
        @DisplayName("첫 화면 이벤트가 알려 준 들어온 길과 가장 이른 시각으로 방문을 본다")
        void sighting() {
            Instant earlier = NOW.minus(Duration.ofMinutes(5));
            VisitorSighting sighting = EventBatch.of(List.of(
                    화면(EventDefinitions.TAB_VIEW, "tab", "map"),
                    화면_시각(EventDefinitions.APP_OPEN, earlier, "entry", "profile")), INGEST)
                .sighting(definitions, VISITOR, null, DeviceType.MOBILE, NOW).orElseThrow();

            assertThat(sighting.entry()).isEqualTo(EntryPoint.PROFILE);
            assertThat(sighting.seenAt()).isEqualTo(earlier);
        }

        @Test
        @DisplayName("빈 묶음이나 모두 받지 않은 묶음은 방문으로 세지 않는다 — 새 방문·첫 화면이 늘지 않는다")
        void notAVisitWithoutAcceptedEvents() {
            assertThat(EventBatch.of(List.of(), INGEST).sighting(definitions, VISITOR, null, DeviceType.MOBILE, NOW)).isEmpty();
            assertThat(EventBatch.of(List.of(화면("buy_item"), 화면(EventDefinitions.CHECK_IN, "rarity", "common"),
                    화면(EventDefinitions.TAB_VIEW, "tab", "map", "memo", "x")), INGEST)
                .sighting(definitions, VISITOR, null, DeviceType.MOBILE, NOW)).isEmpty();
        }

        @Test
        @DisplayName("첫 화면 이벤트가 없으면 들어온 길은 모른다")
        void noEntry() {
            assertThat(EventBatch.of(List.of(화면(EventDefinitions.TAB_VIEW, "tab", "map")), INGEST)
                .sighting(definitions, VISITOR, null, DeviceType.MOBILE, NOW).orElseThrow().entry()).isNull();
        }
    }
}
