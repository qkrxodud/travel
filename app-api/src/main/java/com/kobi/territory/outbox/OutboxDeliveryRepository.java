package com.kobi.territory.outbox;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxDeliveryRepository extends JpaRepository<OutboxDeliveryEntity, OutboxDeliveryEntity.Key> {

    List<OutboxDeliveryEntity> findByEventId(Long eventId);

    List<OutboxDeliveryEntity> findByStatus(OutboxDeliveryEntity.Status status);
}
