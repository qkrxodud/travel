package com.kobi.territory.analytics.domain.tracking;

import com.kobi.territory.analytics.domain.actor.ActorKey;
import com.kobi.territory.analytics.domain.actor.Country;
import com.kobi.territory.analytics.domain.actor.DeviceType;
import com.kobi.territory.analytics.domain.actor.ExplorerHash;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 받을 수 있는 이벤트 하나의 정의.
 *
 * @param source     누가 적는가(화면·서버 사실·요청)
 * @param fields     받을 수 있는 필드(이 밖의 필드는 거절)
 * @param labelField 집계 갈래로 쓸 필드(탭 이름·오류 코드 등, 없으면 null)
 * @param feature    기능별 사용률에 넣는 "기능" 이벤트인지
 */
public record EventDefinition(String name, EventSource source, List<FieldSpec> fields, String labelField, boolean feature) {

    public EventDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(source, "source");
        fields = List.copyOf(fields);
        if (labelField != null && fields.stream().noneMatch(spec -> spec.key().equals(labelField))) {
            throw new IllegalArgumentException(name + " 의 갈래 필드 " + labelField + " 가 필드 목록에 없다");
        }
    }

    /** 화면 이벤트 하나를 이 정의대로 읽는다. 규칙에 맞지 않으면 {@link InvalidTrackedEvent}. */
    TrackedEvent client(SubmittedEvent submitted, ClientContext context, IngestPolicy policy) {
        if (source != EventSource.CLIENT) throw new InvalidTrackedEvent(RejectionReason.SERVER_ONLY_EVENT, name);
        EventProperties properties = read(submitted.fields());
        Instant occurredAt = policy.occurredAt(submitted.at(), context.receivedAt());
        return new TrackedEvent(name, source, occurredAt, policy.dayOf(occurredAt), context.actor(), context.explorerHash(),
            context.visitorId(), context.device(), context.country(), label(properties), properties, null);
    }

    /** 화면 이벤트로 받을 수 있는지(적지는 않는다 — 방문으로 셀지 판단용). */
    boolean acceptsFromClient(SubmittedEvent submitted) {
        if (source != EventSource.CLIENT) return false;
        try {
            read(submitted.fields());
            return true;
        } catch (InvalidTrackedEvent invalid) {
            return false;
        }
    }

    /** 서버 사실을 적을 한 줄로. 서버 사실도 같은 필드 규칙을 지난다(개인정보가 실수로 섞이지 않게). */
    public TrackedEvent server(ServerFact fact, ExplorerHash explorerHash, String dedupKey, IngestPolicy policy) {
        EventProperties properties = read(fact.fields());
        return new TrackedEvent(name, source, fact.occurredAt(), policy.dayOf(fact.occurredAt()),
            ActorKey.ofExplorer(explorerHash), explorerHash, null, DeviceType.UNKNOWN,
            null, label(properties), properties, dedupKey);
    }

    /** 공개 페이지 요청(누구인지 모름 — 사람 수 지표에는 안 들어가고 열람 수로만 센다). */
    public TrackedEvent request(Map<String, Object> fields, DeviceType device, Country country, Instant at, IngestPolicy policy) {
        EventProperties properties = read(fields);
        return new TrackedEvent(name, source, at, policy.dayOf(at), null, null, null, device, country, label(properties), properties,
            null);
    }

    EventProperties read(Map<String, Object> raw) {
        raw.keySet().stream().filter(PersonalDataGuard::personal).findFirst().ifPresent(key -> {
            throw new InvalidTrackedEvent(RejectionReason.PERSONAL_DATA, name + "." + key);
        });
        raw.keySet().stream().filter(key -> fields.stream().noneMatch(spec -> spec.key().equals(key))).findFirst()
            .ifPresent(key -> {
                throw new InvalidTrackedEvent(RejectionReason.UNKNOWN_FIELD, name + "." + key);
            });
        Map<String, String> values = new LinkedHashMap<>();
        for (FieldSpec spec : fields) {
            Object value = raw.get(spec.key());
            if (value == null) {
                if (spec.required()) throw new InvalidTrackedEvent(RejectionReason.MISSING_FIELD, name + "." + spec.key());
                continue;
            }
            values.put(spec.key(), spec.normalize(value)
                .orElseThrow(() -> new InvalidTrackedEvent(RejectionReason.INVALID_FIELD, name + "." + spec.key())));
        }
        return EventProperties.of(values);
    }

    private String label(EventProperties properties) {
        return Optional.ofNullable(labelField).flatMap(properties::value).orElse(null);
    }
}
