package com.kobi.territory.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kobi.territory.common.event.DomainEvent;
import com.kobi.territory.common.event.EventOutbox;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * EventOutbox(common 포트)의 JPA 구현. 반드시 호출자 트랜잭션 안에서 적재한다(MANDATORY).
 * created_at 은 이벤트가 들고 온 발생 시각(occurredAt) — 어댑터가 시계로 시각을 정하지 않는다.
 */
@Component
public class JpaEventOutbox implements EventOutbox {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    public JpaEventOutbox(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String aggregateType, String aggregateId, DomainEvent event) {
        try {
            repository.save(OutboxEventEntity.from(aggregateType, aggregateId, event,
                objectMapper.writeValueAsString(event), event.occurredAt()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("이벤트 직렬화 실패: " + event, exception);
        }
    }
}
