package com.kobi.territory.analytics.domain.tracking;

import static com.kobi.territory.analytics.domain.tracking.FieldSpec.optional;
import static com.kobi.territory.analytics.domain.tracking.FieldSpec.optionalOneOf;
import static com.kobi.territory.analytics.domain.tracking.FieldSpec.required;
import static com.kobi.territory.analytics.domain.tracking.FieldSpec.requiredOneOf;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 받을 수 있는 이벤트의 허용 목록(계약 {@code _workspace/10_contracts.md} §1). 이 목록에 없는 이름·필드는 받지 않는다.
 * 화면 이벤트는 "본 것·누른 것"만, 가입·체크인·합류처럼 확실한 사실은 서버 사실로만 센다(화면이 보내도 거절).
 */
public final class EventDefinitions {

    // ---- 화면 이벤트(POST /events) ----
    public static final String APP_OPEN = "app_open";
    public static final String TAB_VIEW = "tab_view";
    public static final String CHECKIN_OPEN = "checkin_open";
    public static final String CHECKIN_SAVE = "checkin_save";
    public static final String CHECKIN_CANCEL = "checkin_cancel";
    public static final String SHARE_CLICK = "share_click";
    public static final String LINK_COPY = "link_copy";
    public static final String ONBOARDING_STEP = "onboarding_step";
    public static final String PUSH_PROMPT = "push_prompt";
    public static final String ERROR_TOAST = "error_toast";
    /** 12단계: 알림을 눌러 앱이 열렸다(화면이 열린 주소의 push=종류 로 보낸다). */
    public static final String PUSH_OPEN = "push_open";

    // ---- 서버 사실(공개 이벤트 구독) ----
    public static final String EXPLORER_CREATED = "explorer_created";
    public static final String CHECK_IN = "check_in";
    public static final String FIRST_CHECK_IN = "first_check_in";
    public static final String CHECK_IN_CANCELLED = "check_in_cancelled";
    public static final String SHARED_MAP_CREATED = "shared_map_created";
    public static final String SHARED_MAP_JOINED = "shared_map_joined";
    public static final String ACCOUNT_LINKED = "account_linked";
    public static final String EXPLORER_MERGED = "explorer_merged";
    public static final String QUEST_CLAIMED = "quest_claimed";
    public static final String STREAK_MILESTONE = "streak_milestone";
    public static final String PROVINCE_CONQUERED = "province_conquered";
    public static final String MYSTERY_FOUND = "mystery_found";
    public static final String REVISIT_STAMPED = "revisit_stamped";
    public static final String WISH_FULFILLED = "wish_fulfilled";
    /** 12단계: 알림을 한 사람의 기기에 보냈다(알림 컨텍스트의 PushSent). */
    public static final String PUSH_SENT = "push_sent";

    // ---- 공개 페이지 요청(요청 필터) ----
    public static final String PROFILE_VIEW = "profile_view";
    public static final String CARD_VIEW = "card_view";
    public static final String COMPARE_CARD_VIEW = "compare_card_view";

    private static final EventDefinitions STANDARD = new EventDefinitions(List.of(
        client(APP_OPEN, "entry", false, requiredOneOf("entry", "direct", "invite", "profile", "card", "push", "other")),
        client(TAB_VIEW, "tab", true, requiredOneOf("tab", "map", "bag", "sets", "quests", "rank", "profile")),
        client(CHECKIN_OPEN, null, true),
        client(CHECKIN_SAVE, null, false),
        client(CHECKIN_CANCEL, null, false),
        client(SHARE_CLICK, "target", true, required("target", FieldType.TOKEN)),
        client(LINK_COPY, "target", true, required("target", FieldType.TOKEN)),
        client(ONBOARDING_STEP, "step", false, required("step", FieldType.SMALL_NUMBER),
            optionalOneOf("action", "view", "done", "skip")),
        client(PUSH_PROMPT, "result", false, requiredOneOf("result", "shown", "granted", "denied", "dismissed")),
        client(ERROR_TOAST, "code", false, required("code", FieldType.ERROR_CODE)),
        client(PUSH_OPEN, "kind", false, requiredOneOf("kind", "mystery", "streak", "season")),

        server(EXPLORER_CREATED, null, false),
        server(CHECK_IN, "rarity", true, requiredOneOf("rarity", "common", "rare", "legend"),
            optional("province", FieldType.AREA_CODE), optional("shared", FieldType.FLAG)),
        server(FIRST_CHECK_IN, null, false),
        server(CHECK_IN_CANCELLED, null, false),
        server(SHARED_MAP_CREATED, null, true),
        server(SHARED_MAP_JOINED, "via", true, requiredOneOf("via", "invite_code", "profile_link", "unknown"),
            optional("rejoined", FieldType.FLAG)),
        server(ACCOUNT_LINKED, null, true),
        server(EXPLORER_MERGED, null, false),
        server(QUEST_CLAIMED, "quest", true, required("quest", FieldType.TOKEN)),
        server(STREAK_MILESTONE, "months", false, required("months", FieldType.SMALL_NUMBER)),
        server(PROVINCE_CONQUERED, "province", false, required("province", FieldType.AREA_CODE)),
        server(MYSTERY_FOUND, null, false),
        server(REVISIT_STAMPED, null, true),
        server(WISH_FULFILLED, null, true),
        server(PUSH_SENT, "kind", false, requiredOneOf("kind", "mystery", "streak", "season"), optional("devices", FieldType.SMALL_NUMBER)),

        new EventDefinition(PROFILE_VIEW, EventSource.REQUEST, List.of(), null, false),
        new EventDefinition(CARD_VIEW, EventSource.REQUEST, List.of(required("kind", FieldType.TOKEN)), "kind", false),
        new EventDefinition(COMPARE_CARD_VIEW, EventSource.REQUEST, List.of(), null, false)));

    private final Map<String, EventDefinition> byName;

    private EventDefinitions(List<EventDefinition> definitions) {
        this.byName = definitions.stream()
            .collect(Collectors.toMap(EventDefinition::name, Function.identity(), (first, second) -> {
                throw new IllegalArgumentException("이벤트 이름 중복: " + first.name());
            }, LinkedHashMap::new));
    }

    public static EventDefinitions standard() {
        return STANDARD;
    }

    public Optional<EventDefinition> find(String name) {
        return Optional.ofNullable(name).map(byName::get);
    }

    /** 이름이 정해진 정의(서버 사실·요청 — 코드가 이름을 고르므로 없으면 프로그래밍 오류). */
    public EventDefinition require(String name) {
        return find(name).orElseThrow(() -> new IllegalArgumentException("정의되지 않은 이벤트: " + name));
    }

    /** 기능별 사용률에 넣는 이벤트 이름(정의 순서). */
    public List<String> featureNames() {
        return byName.values().stream().filter(EventDefinition::feature).map(EventDefinition::name).toList();
    }

    /** 출처별 정의(계약 문서·화면 상수와 대조용). */
    public List<EventDefinition> from(EventSource source) {
        return byName.values().stream().filter(definition -> definition.source() == source).toList();
    }

    private static EventDefinition client(String name, String labelField, boolean feature, FieldSpec... fields) {
        return new EventDefinition(name, EventSource.CLIENT, List.of(fields), labelField, feature);
    }

    private static EventDefinition server(String name, String labelField, boolean feature, FieldSpec... fields) {
        return new EventDefinition(name, EventSource.SERVER, List.of(fields), labelField, feature);
    }
}
