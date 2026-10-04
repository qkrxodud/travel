package com.kobi.territory.analytics.application;

import com.kobi.territory.analytics.domain.tracking.EventDefinitions;
import com.kobi.territory.analytics.domain.tracking.ServerFact;
import com.kobi.territory.common.event.DomainEvent;
import com.kobi.territory.common.event.EventSubscriber;
import com.kobi.territory.exploration.api.event.ExplorerMerged;
import com.kobi.territory.exploration.api.event.HandleChanged;
import com.kobi.territory.exploration.api.event.MapCreated;
import com.kobi.territory.exploration.api.event.MemberJoined;
import com.kobi.territory.exploration.api.event.RegionVisited;
import com.kobi.territory.exploration.api.event.RevisitStamped;
import com.kobi.territory.exploration.api.event.VisitCancelled;
import com.kobi.territory.exploration.api.event.WishFulfilled;
import com.kobi.territory.progression.api.event.MysteryBonusEarned;
import com.kobi.territory.progression.api.event.ProvinceConquered;
import com.kobi.territory.progression.api.event.QuestCompleted;
import com.kobi.territory.progression.api.event.StreakMilestoneReached;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 분석의 outbox 구독 — 소비 대상 하나(원본 이벤트 기록 + 탐험가 여정)에 구독자 하나 {@code analytics.events}. id 는
 * outbox_delivery.subscriber 키라 바꾸지 않는다. 다른 컨텍스트의 공개 이벤트를 분석 사실({@link ServerFact})로 옮기기만 한다 —
 * 게임 규칙은 건드리지 않고 아무것도 내보내지 않는다(관찰자). 탐험가 id 는 적을 때 해시로 바뀌고, handle·메모 같은 값은 옮기지 않는다.
 * <p>
 * 보호권(연속 탐험 동결) 사용·지급은 공개 이벤트가 없어 아직 받지 않는다(진행 컨텍스트에 이벤트가 생기면 여기에 더한다).
 */
@Configuration
public class AnalyticsSubscriptions {

    public static final String SUBSCRIBER = "analytics.events";

    @Bean
    EventSubscriber analyticsEventsSubscriber(ServerFactRecorder recorder) {
        return EventSubscriber.named(SUBSCRIBER)
            .on(MapCreated.class, event -> {
                if ("PERSONAL".equals(event.kind())) {
                    recorder.recordExplorerCreated(fact(EventDefinitions.EXPLORER_CREATED, event.ownerId(), event, Map.of()));
                } else {
                    recorder.record(fact(EventDefinitions.SHARED_MAP_CREATED, event.ownerId(), event, Map.of()));
                }
            })
            .on(RegionVisited.class, event -> recorder.recordCheckIn(fact(EventDefinitions.CHECK_IN, event.explorerId(), event,
                fields("rarity", event.rarity() == null ? null : event.rarity().name().toLowerCase(Locale.ROOT),
                    "province", event.provinceCode()))))
            .on(VisitCancelled.class, event -> recorder.record(fact(EventDefinitions.CHECK_IN_CANCELLED, event.explorerId(), event,
                Map.of())))
            .on(MemberJoined.class, event -> {
                // 초대(초대코드·프로필 링크) 합류만 — 지도 생성의 지도장·병합 대체 합류는 초대가 아니다(invitedBy 없음)
                if (event.invitedBy() == null) return;
                recorder.recordInvitedJoin(fact(EventDefinitions.SHARED_MAP_JOINED, event.explorerId(), event,
                    fields("via", via(event), "rejoined", event.rejoined())));
            })
            .on(HandleChanged.class, event -> {
                // 처음 받는 handle = 계정 연결(로그인으로 익명 탐험가를 계정에 잇거나 새 계정 탐험가). handle 값 자체는 적지 않는다
                if (event.previousHandle() != null) return;
                recorder.record(fact(EventDefinitions.ACCOUNT_LINKED, event.explorerId(), event, Map.of()));
            })
            .on(ExplorerMerged.class, event -> recorder.record(fact(EventDefinitions.EXPLORER_MERGED, event.intoExplorerId(), event,
                Map.of())))
            .on(QuestCompleted.class, event -> recorder.record(fact(EventDefinitions.QUEST_CLAIMED, event.explorerId(), event,
                fields("quest", event.questId()))))
            .on(StreakMilestoneReached.class, event -> recorder.record(fact(EventDefinitions.STREAK_MILESTONE, event.explorerId(),
                event, fields("months", event.months()))))
            .on(ProvinceConquered.class, event -> recorder.record(fact(EventDefinitions.PROVINCE_CONQUERED, event.explorerId(),
                event, fields("province", event.provinceCode()))))
            .on(MysteryBonusEarned.class, event -> recorder.record(fact(EventDefinitions.MYSTERY_FOUND, event.explorerId(), event,
                Map.of())))
            .on(RevisitStamped.class, event -> recorder.record(fact(EventDefinitions.REVISIT_STAMPED, event.explorerId(), event,
                Map.of())))
            .on(WishFulfilled.class, event -> recorder.record(fact(EventDefinitions.WISH_FULFILLED, event.explorerId(), event,
                Map.of())))
            .build();
    }

    /** 합류 경로. 경로가 실리기 전(10단계 이전) 이벤트는 unknown. */
    private static String via(MemberJoined event) {
        if (event.joinedVia() == null) return "unknown";
        return event.joinedVia().toLowerCase(Locale.ROOT);
    }

    /** 같은 공개 이벤트면 같은 열쇠(재전달 멱등) — 이벤트 종류 + 내용 전체. */
    private static ServerFact fact(String name, String explorerId, DomainEvent event, Map<String, Object> fields) {
        return new ServerFact(name, explorerId, event.occurredAt(), fields, event.getClass().getSimpleName() + "|" + event);
    }

    /** 키·값 짝으로 필드를 만든다(값이 null 이면 넣지 않는다). */
    private static Map<String, Object> fields(Object... keyValues) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            if (keyValues[i + 1] != null) fields.put((String) keyValues[i], keyValues[i + 1]);
        }
        return fields;
    }
}
