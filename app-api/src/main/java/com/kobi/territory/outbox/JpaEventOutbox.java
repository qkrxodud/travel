package com.kobi.territory.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kobi.territory.common.event.DomainEvent;
import com.kobi.territory.common.event.EventOutbox;
import java.time.Clock;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** EventOutbox(common 포트)의 JPA 구현. 반드시 호출자 트랜잭션 안에서 적재한다(MANDATORY). */
@Component
public class JpaEventOutbox implements EventOutbox {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public JpaEventOutbox(OutboxEventRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String aggregateType, String aggregateId, DomainEvent event) {
        try {
            repository.save(new OutboxEventEntity(aggregateType, aggregateId, event.getClass().getName(),
                objectMapper.writeValueAsString(event), clock.instant()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("이벤트 직렬화 실패: " + event, exception);
        }
    }
}
