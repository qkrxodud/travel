package com.kobi.territory.common.event;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * outbox 릴레이의 구독자(리더 결정 D5, QA P1-1 수정). **소비 대상 애그리거트 하나당 구독자 하나**다 — 그 애그리거트가 받는
 * 이벤트 타입(예: RegionVisited·VisitCancelled·SetCompleted)을 모두 이 구독자 안에서 타입별로 나눠 처리한다.
 * 릴레이는 (aggregateId, 구독자) 단위로 id 순서를 지키므로, 같은 애그리거트로 가는 체크인·취소가 서로 앞지르지 않는다.
 * <p>
 * 전달은 최소 1회이므로 {@link #handle}은 멱등해야 한다. id 는 전달 기록(outbox_delivery.subscriber)의 키라 바꾸면 안 된다.
 */
public interface EventSubscriber {

    /** 안정된 구독자 이름(예: "progression.progress"). */
    String id();

    /** 이 구독자가 받는 이벤트 타입들. */
    Set<Class<? extends DomainEvent>> eventTypes();

    void handle(DomainEvent event);

    default boolean accepts(DomainEvent event) {
        return eventTypes().stream().anyMatch(type -> type.isInstance(event));
    }

    static Builder named(String id) {
        return new Builder(id);
    }

    /** 이벤트 타입별 처리기를 모아 구독자 하나를 만든다. */
    final class Builder {
        private final String id;
        private final Map<Class<? extends DomainEvent>, Consumer<DomainEvent>> handlers = new LinkedHashMap<>();

        private Builder(String id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public <E extends DomainEvent> Builder on(Class<E> type, Consumer<? super E> handler) {
            Objects.requireNonNull(handler, "handler");
            handlers.put(type, event -> handler.accept(type.cast(event)));
            return this;
        }

        public EventSubscriber build() {
            Map<Class<? extends DomainEvent>, Consumer<DomainEvent>> table = Map.copyOf(handlers);
            String name = id;
            return new EventSubscriber() {
                @Override public String id() { return name; }
                @Override public Set<Class<? extends DomainEvent>> eventTypes() { return table.keySet(); }

                @Override
                public void handle(DomainEvent event) {
                    table.entrySet().stream().filter(entry -> entry.getKey().isInstance(event)).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException(name + " 가 받지 않는 이벤트: " + event.getClass()))
                        .getValue().accept(event);
                }

                @Override public String toString() { return name + table.keySet().stream().map(Class::getSimpleName).toList(); }
            };
        }
    }
}
