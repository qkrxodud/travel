package com.kobi.territory.analytics.domain.tracking;

import com.kobi.territory.analytics.domain.AnalyticsError;
import com.kobi.territory.analytics.domain.actor.DeviceType;
import com.kobi.territory.analytics.domain.actor.EntryPoint;
import com.kobi.territory.analytics.domain.actor.ExplorerHash;
import com.kobi.territory.analytics.domain.actor.VisitorId;
import com.kobi.territory.analytics.domain.actor.VisitorSighting;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 화면이 한 번에 보낸 이벤트 묶음. 묶음 크기 상한을 넘으면 통째로 거절하고, 그 안에서는 이벤트마다 따로 판단한다 —
 * 규칙에 맞지 않는 이벤트만 이유와 함께 버리고 나머지는 적는다(화면 실수 하나로 다른 이벤트를 잃지 않게).
 * 봇 요청이면 아무것도 적지 않는다(사람 지표만 본다).
 */
public final class EventBatch {

    private final List<SubmittedEvent> submitted;
    private final IngestPolicy policy;

    private EventBatch(List<SubmittedEvent> submitted, IngestPolicy policy) {
        this.submitted = submitted;
        this.policy = policy;
    }

    public static EventBatch of(List<SubmittedEvent> submitted, IngestPolicy policy) {
        List<SubmittedEvent> events = submitted == null ? List.of() : List.copyOf(submitted);
        if (events.size() > policy.maxBatchEvents()) {
            throw AnalyticsError.EVENT_BATCH_TOO_LARGE.exception(policy.maxBatchEvents() + "개");
        }
        return new EventBatch(events, policy);
    }

    public int size() {
        return submitted.size();
    }

    /**
     * 이 묶음으로 본 방문. 봇이거나 받을 이벤트가 하나도 없으면(빈 묶음·전부 거절) 방문으로 적지 않는다(빈 값 — 새 방문·첫 화면으로 세지 않는다).
     * 들어온 길은 묶음 안 첫 번째 올바른 첫 화면 이벤트에서.
     */
    public Optional<VisitorSighting> sighting(EventDefinitions definitions, VisitorId visitorId, ExplorerHash explorerHash,
                                              DeviceType device, Instant receivedAt) {
        boolean anyAcceptable = submitted.stream().anyMatch(event -> definitions.find(event.name())
            .map(definition -> definition.acceptsFromClient(event)).orElse(false));
        if (!device.human() || !anyAcceptable) return Optional.empty();
        Instant seenAt = submitted.stream().map(event -> policy.occurredAt(event.at(), receivedAt)).min(Instant::compareTo)
            .orElse(receivedAt);
        return Optional.of(new VisitorSighting(visitorId, seenAt, policy.dayOf(seenAt), entryPoint().orElse(null), explorerHash,
            device));
    }

    /** 묶음 안 첫 화면 이벤트가 알려 준 들어온 길. */
    Optional<EntryPoint> entryPoint() {
        return submitted.stream().filter(event -> EventDefinitions.APP_OPEN.equals(event.name()))
            .map(event -> event.fields().get("entry")).filter(String.class::isInstance).map(String.class::cast)
            .map(EntryPoint::fromLabel).flatMap(Optional::stream).findFirst();
    }

    /** 이벤트마다 정의대로 읽어 적을 것과 버릴 것을 나눈다. */
    public BatchOutcome accept(EventDefinitions definitions, ClientContext context) {
        if (!context.device().human()) return new BatchOutcome(List.of(), List.of(), true);
        List<TrackedEvent> accepted = new ArrayList<>();
        List<Rejection> rejected = new ArrayList<>();
        for (int index = 0; index < submitted.size(); index++) {
            SubmittedEvent event = submitted.get(index);
            Optional<EventDefinition> definition = definitions.find(event.name());
            if (definition.isEmpty()) {
                rejected.add(new Rejection(index, event.name(), RejectionReason.UNKNOWN_EVENT));
                continue;
            }
            try {
                accepted.add(definition.get().client(event, context, policy));
            } catch (InvalidTrackedEvent invalid) {
                rejected.add(new Rejection(index, event.name(), invalid.reason()));
            }
        }
        return new BatchOutcome(accepted, rejected, false);
    }
}
