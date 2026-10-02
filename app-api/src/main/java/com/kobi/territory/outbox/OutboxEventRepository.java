package com.kobi.territory.outbox;

import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {

    List<OutboxEventEntity> findByPublishedAtIsNullOrderByIdAsc(Limit limit);

    List<OutboxEventEntity> findByAggregateIdOrderByIdAsc(String aggregateId);
}
