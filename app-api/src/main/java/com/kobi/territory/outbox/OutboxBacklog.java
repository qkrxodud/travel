package com.kobi.territory.outbox;

import com.kobi.territory.common.event.EventBacklog;
import com.kobi.territory.common.event.EventSubscriber;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * EventBacklog(common 포트)의 outbox 구현. 재계산 보류 판단(S3-3)에 쓴다 — 미발행 행 중 해당 구독자(접두사)가 받는 이벤트인데
 * 그 구독자의 전달 기록이 DELIVERED 가 아닌 것이 있으면 "미전달"이다(QA P3-6 — 다른 컨텍스트 구독자의 FAILED 는 무관).
 */
@Component
public class OutboxBacklog implements EventBacklog {

    private final OutboxEventRepository events;
    private final OutboxDeliveryRepository deliveries;
    private final List<EventSubscriber> subscribers;

    public OutboxBacklog(OutboxEventRepository events, OutboxDeliveryRepository deliveries, List<EventSubscriber> subscribers) {
        this.events = events;
        this.deliveries = deliveries;
        this.subscribers = List.copyOf(subscribers);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasUndelivered(Collection<String> aggregateIds, String subscriberPrefix) {
        if (aggregateIds.isEmpty()) return false;
        List<OutboxEventEntity> pending = events.findByAggregateIdInAndPublishedAtIsNull(aggregateIds);
        if (pending.isEmpty()) return false;
        Map<Long, Set<String>> delivered = deliveries.findByEventIdIn(pending.stream().map(OutboxEventEntity::id).toList())
            .stream().filter(OutboxDeliveryEntity::delivered)
            .collect(Collectors.groupingBy(OutboxDeliveryEntity::eventId,
                Collectors.mapping(OutboxDeliveryEntity::subscriber, Collectors.toSet())));
        return pending.stream().anyMatch(row -> subscribersOf(row, subscriberPrefix).stream()
            .anyMatch(subscriberId -> !delivered.getOrDefault(row.id(), Set.of()).contains(subscriberId)));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasAnyPending() {
        return events.existsByPublishedAtIsNull();
    }

    /** 이 행의 이벤트 타입을 받는, 접두사가 맞는 구독자 id. 타입을 모르면(역직렬화 불가 행) 없음. */
    private List<String> subscribersOf(OutboxEventEntity row, String prefix) {
        Class<?> type;
        try {
            type = Class.forName(row.eventType());
        } catch (ClassNotFoundException unknown) {
            return List.of();
        }
        return subscribers.stream().filter(subscriber -> subscriber.id().startsWith(prefix))
            .filter(subscriber -> subscriber.eventTypes().stream().anyMatch(accepted -> accepted.isAssignableFrom(type)))
            .map(EventSubscriber::id).toList();
    }
}
