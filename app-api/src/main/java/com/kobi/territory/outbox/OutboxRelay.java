package com.kobi.territory.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kobi.territory.common.event.DomainEvent;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * outbox 릴레이(@Scheduled). 미발행 행을 id 순으로 읽어 ApplicationEventPublisher로 발행하고 published_at을 찍는다.
 * 발행과 기록이 한 트랜잭션이라 구독자 실패 시 다음 주기에 다시 보낸다 → 최소 1회 전달(구독자 멱등 필수).
 * Kafka는 5단계까지 붙이지 않는다.
 */
@Component
@ConditionalOnProperty(name = "territory.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH = 100;

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher publisher;
    private final TransactionTemplate tx;
    private final Clock clock;

    public OutboxRelay(OutboxEventRepository repository, ObjectMapper objectMapper, ApplicationEventPublisher publisher,
                       TransactionTemplate tx, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.publisher = publisher;
        this.tx = tx;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${territory.outbox.relay.delay-ms:1000}")
    public void relay() {
        for (OutboxEventEntity row : repository.findByPublishedAtIsNullOrderByIdAsc(Limit.of(BATCH))) {
            try {
                tx.executeWithoutResult(status -> {
                    OutboxEventEntity fresh = repository.findById(row.getId()).orElseThrow();
                    if (fresh.getPublishedAt() != null) return;
                    publisher.publishEvent(deserialize(fresh));
                    fresh.markPublished(clock.instant());
                });
            } catch (RuntimeException e) {
                log.warn("outbox {} ({}) 발행 실패 — 다음 주기에 재시도: {}", row.getId(), row.eventName(), e.toString());
                return; // 순서 보장을 위해 이번 주기는 여기서 멈춘다
            }
        }
    }

    private DomainEvent deserialize(OutboxEventEntity row) {
        try {
            Class<?> type = Class.forName(row.getEventType());
            return (DomainEvent) objectMapper.readValue(row.getPayload(), type);
        } catch (Exception e) {
            throw new IllegalStateException("outbox 역직렬화 실패: " + row.getEventType(), e);
        }
    }
}
