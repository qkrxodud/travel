package com.kobi.territory.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kobi.territory.common.event.DomainEvent;
import com.kobi.territory.common.event.EventSubscriber;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * outbox 릴레이(@Scheduled) — 구독자별 전달·트랜잭션 분리(리더 결정 D5, QA P1-1·P1-2 수정).
 * <ul>
 *   <li>구독자 = 소비 애그리거트 하나({@link EventSubscriber}). 미발행 행을 id 순으로 읽어 받는 구독자마다 따로 전달한다.</li>
 *   <li>구독자 처리 + DELIVERED 기록을 그 구독자 전용 트랜잭션(REQUIRES_NEW)에서 함께 커밋한다.</li>
 *   <li><b>순서 단위(lane) = (aggregateId, 구독자)</b>. 같은 단위 안에서 앞 이벤트가 DELIVERED 가 아니면(재시도 대기·FAILED)
 *       뒤 이벤트를 보내지 않는다(head-of-line). 순서를 깨고 건너뛰지 않는다 — 그래서 취소가 체크인을 앞지르지 않는다.
 *       다른 단위는 계속 진행한다(독성 이벤트가 전체를 막지 않음).</li>
 *   <li>낙관적 락 충돌(동시 사용자 커맨드와 경합)은 재시도 상한에 세지 않고 지수 백오프 + 지터로 계속 재시도한다.
 *       그 밖의 실패는 백오프로 재시도, max-attempts(5)에 닿으면 FAILED — 그 단위는 {@link OutboxRedelivery}로 풀 때까지 멈춘다.</li>
 *   <li>모든 구독자가 DELIVERED 가 되면 published_at 을 찍고 프로세스 안 리스너(@EventListener)에도 알린다.
 *       FAILED 가 남은 행은 미발행으로 남아 재전달을 기다린다.</li>
 * </ul>
 * 최소 1회 전달이므로 구독자는 멱등해야 한다. 다중 인스턴스(SKIP LOCKED)는 하지 않는다.
 */
@Component
@ConditionalOnProperty(name = "territory.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int PAGE = 100;

    private final OutboxEventRepository events;
    private final OutboxDeliveryRepository deliveries;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher publisher;
    private final List<EventSubscriber> subscribers;
    private final TransactionTemplate newTx;
    private final Clock clock;
    /** 재시도 시각(백오프)은 벽시계로 잰다 — 업무 시계(Clock 빈, 테스트에선 멈춘 가변 시계)와 무관한 인프라 타이밍이다. */
    private final Clock retryClock = Clock.systemUTC();
    private final int maxAttempts;
    private final RetryBackoff backoff;

    public OutboxRelay(OutboxEventRepository events, OutboxDeliveryRepository deliveries, ObjectMapper objectMapper,
                       ApplicationEventPublisher publisher, List<EventSubscriber> subscribers,
                       PlatformTransactionManager txManager, Clock clock,
                       @Value("${territory.outbox.relay.max-attempts:5}") int maxAttempts,
                       @Value("${territory.outbox.relay.backoff.initial-ms:500}") long initialBackoffMs,
                       @Value("${territory.outbox.relay.backoff.max-ms:30000}") long maxBackoffMs) {
        this.events = events;
        this.deliveries = deliveries;
        this.objectMapper = objectMapper;
        this.publisher = publisher;
        this.subscribers = List.copyOf(subscribers);
        this.newTx = new TransactionTemplate(txManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.backoff = new RetryBackoff(Duration.ofMillis(initialBackoffMs), Duration.ofMillis(maxBackoffMs),
            ThreadLocalRandom.current());
        log.info("outbox 구독자 {}개: {}", this.subscribers.size(), this.subscribers);
    }

    @Scheduled(fixedDelayString = "${territory.outbox.relay.delay-ms:1000}")
    public synchronized void relay() {
        Set<String> stalledLanes = new HashSet<>(); // 이번 주기에 앞 이벤트가 끝나지 않아 멈춘 (aggregateId|구독자)
        long cursor = 0;
        List<OutboxEventEntity> page;
        do {
            page = events.findByPublishedAtIsNullAndIdGreaterThanOrderByIdAsc(cursor, Limit.of(PAGE));
            for (OutboxEventEntity row : page) {
                cursor = row.id();
                relayRow(row, stalledLanes);
            }
        } while (page.size() == PAGE);
    }

    private void relayRow(OutboxEventEntity row, Set<String> stalledLanes) {
        Optional<DomainEvent> parsed = deserialize(row);
        if (parsed.isEmpty()) {
            markPublished(row.id());
            return;
        }
        DomainEvent event = parsed.get();
        Map<String, OutboxDeliveryEntity> state = deliveries.findByEventId(row.id()).stream()
            .collect(Collectors.toMap(OutboxDeliveryEntity::subscriber, Function.identity()));
        Instant now = retryClock.instant();
        boolean allDelivered = true;
        for (EventSubscriber subscriber : subscribersOf(event)) {
            OutboxDeliveryEntity delivery = state.get(subscriber.id());
            if (delivery != null && delivery.delivered()) continue;
            String lane = row.aggregateId() + "|" + subscriber.id();
            boolean blocked = stalledLanes.contains(lane)
                || (delivery != null && (delivery.failed() || delivery.waitingAt(now)));
            if (blocked || !deliver(row, event, subscriber)) {
                stalledLanes.add(lane);
                allDelivered = false;
            }
        }
        if (allDelivered) {
            markPublished(row.id());
            notifyListeners(event);
        }
    }

    /** @return 이 구독자에게 전달됐는지(DELIVERED) */
    private boolean deliver(OutboxEventEntity row, DomainEvent event, EventSubscriber subscriber) {
        try {
            newTx.executeWithoutResult(status -> {
                subscriber.handle(event);
                OutboxDeliveryEntity delivery = loadOrNew(row.id(), subscriber.id());
                delivery.markDelivered(clock.instant());
                deliveries.save(delivery);
            });
            return true;
        } catch (RuntimeException exception) {
            recordFailure(row, subscriber, exception);
            return false;
        }
    }

    private void recordFailure(OutboxEventEntity row, EventSubscriber subscriber, RuntimeException exception) {
        boolean conflict = isConflict(exception);
        newTx.executeWithoutResult(status -> {
            OutboxDeliveryEntity delivery = loadOrNew(row.id(), subscriber.id());
            Instant now = retryClock.instant();
            if (conflict) {
                delivery.markConflict(exception.toString(), backoff.delay(delivery.conflicts() + 1), now);
                log.info("outbox {} ({}) → {} 낙관적 락 충돌 {}회째, 백오프 후 재시도", row.id(), row.eventName(),
                    subscriber.id(), delivery.conflicts());
            } else if (delivery.markFailure(exception.toString(), maxAttempts,
                backoff.delay(delivery.attempts() + 1), now)) {
                log.error("outbox {} ({}) → {} 전달 {}회 실패, FAILED — 이 순서 단위는 재전달 전까지 멈춘다: {}", row.id(),
                    row.eventName(), subscriber.id(), maxAttempts, exception.toString());
            } else {
                log.warn("outbox {} ({}) → {} 전달 실패 {}회째, 백오프 후 재시도: {}", row.id(), row.eventName(),
                    subscriber.id(), delivery.attempts(), exception.toString());
            }
            deliveries.save(delivery);
        });
    }

    /** 낙관적 락 충돌 등 동시성 경합(재시도하면 풀리는 실패)인지. */
    static boolean isConflict(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConcurrencyFailureException
                || cause instanceof jakarta.persistence.OptimisticLockException
                || cause.getClass().getName().equals("org.hibernate.StaleObjectStateException")) {
                return true;
            }
        }
        return false;
    }

    private OutboxDeliveryEntity loadOrNew(Long eventId, String subscriberId) {
        return deliveries.findById(new OutboxDeliveryEntity.Key(eventId, subscriberId))
            .orElseGet(() -> new OutboxDeliveryEntity(eventId, subscriberId, clock.instant()));
    }

    private List<EventSubscriber> subscribersOf(DomainEvent event) {
        return subscribers.stream().filter(subscriber -> subscriber.accepts(event)).toList();
    }

    private void markPublished(Long id) {
        newTx.executeWithoutResult(status -> events.findById(id).ifPresent(row -> row.markPublished(clock.instant())));
    }

    /** 프로세스 안 리스너(@EventListener — 테스트 수집기 등)에 알린다. 실패해도 전달 결과에 영향 없음. */
    private void notifyListeners(DomainEvent event) {
        try {
            publisher.publishEvent(event);
        } catch (RuntimeException exception) {
            log.warn("in-process 리스너 실패(무시): {}", exception.toString());
        }
    }

    private Optional<DomainEvent> deserialize(OutboxEventEntity row) {
        try {
            Class<?> type = Class.forName(row.eventType());
            return Optional.of((DomainEvent) objectMapper.readValue(row.payload(), type));
        } catch (Exception exception) {
            log.error("outbox {} 역직렬화 실패({}) — 발행 불가로 표시하고 넘어간다", row.id(), row.eventType(), exception);
            return Optional.empty();
        }
    }
}
