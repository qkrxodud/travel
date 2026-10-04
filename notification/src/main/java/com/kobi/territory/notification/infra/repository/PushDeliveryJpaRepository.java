package com.kobi.territory.notification.infra.repository;

import com.kobi.territory.notification.infra.entity.PushDeliveryJpaEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PushDeliveryJpaRepository extends JpaRepository<PushDeliveryJpaEntity, Long> {

    @Query("select delivery from PushDeliveryJpaEntity delivery where delivery.explorerId = :explorerId and delivery.deliveryDay = :day")
    List<PushDeliveryJpaEntity> onDay(@Param("explorerId") String explorerId, @Param("day") LocalDate day);

    boolean existsByExplorerIdAndKindAndPeriod(String explorerId, String kind, String period);

    /** 보낼 때가 된 PENDING + 오래 멈춘 SENDING(보낼 시각 순). */
    @Query("select delivery.id from PushDeliveryJpaEntity delivery"
        + " where (delivery.status = 'PENDING' and delivery.nextAttemptAt <= :now)"
        + " or (delivery.status = 'SENDING' and delivery.claimedAt <= :staleBefore)"
        + " order by delivery.nextAttemptAt, delivery.id")
    List<Long> due(@Param("now") Instant now, @Param("staleBefore") Instant staleBefore, Pageable page);

    @Query("select delivery from PushDeliveryJpaEntity delivery where delivery.explorerId = :explorerId order by delivery.id desc")
    List<PushDeliveryJpaEntity> recentOf(@Param("explorerId") String explorerId, Pageable page);
}
