package com.kobi.territory.outbox;

import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {

    List<OutboxEventEntity> findByPublishedAtIsNullOrderByIdAsc(Limit limit);

    /** 커서 페이지 — 멈춘(FAILED) 행이 쌓여도 그 뒤 행까지 한 주기에 훑는다. */
    List<OutboxEventEntity> findByPublishedAtIsNullAndIdGreaterThanOrderByIdAsc(Long afterId, Limit limit);

    List<OutboxEventEntity> findByAggregateIdOrderByIdAsc(String aggregateId);
}
