package com.kobi.territory.exploration.infra.repository;

import com.kobi.territory.exploration.infra.entity.HandleReservationJpaEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface HandleReservationJpaRepository extends JpaRepository<HandleReservationJpaEntity, HandleReservationJpaEntity.Key> {

    @Query("select reservation from HandleReservationJpaEntity reservation where reservation.key.explorerId = :explorerId")
    List<HandleReservationJpaEntity> findByExplorerId(@Param("explorerId") String explorerId);

    @Query("select count(reservation) > 0 from HandleReservationJpaEntity reservation where reservation.key.handle = :handle "
        + "and reservation.key.explorerId <> :requester and reservation.reservedUntil > :now")
    boolean reservedByOther(@Param("handle") String handle, @Param("requester") String requester, @Param("now") Instant now);
}
