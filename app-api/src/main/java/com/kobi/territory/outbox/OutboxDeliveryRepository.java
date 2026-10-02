package com.kobi.territory.outbox;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxDeliveryRepository extends JpaRepository<OutboxDeliveryEntity, OutboxDeliveryEntity.Key> {

    List<OutboxDeliveryEntity> findByEventId(Long eventId);

    /** 한 페이지의 전달 기록을 한 번에(R2-2 — 행마다 조회하지 않게). */
    List<OutboxDeliveryEntity> findByEventIdIn(Collection<Long> eventIds);

    List<OutboxDeliveryEntity> findByStatus(OutboxDeliveryEntity.Status status);

    long countByStatus(OutboxDeliveryEntity.Status status);
}
