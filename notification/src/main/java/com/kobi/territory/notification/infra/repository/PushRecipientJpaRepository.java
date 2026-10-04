package com.kobi.territory.notification.infra.repository;

import com.kobi.territory.notification.infra.entity.PushRecipientJpaEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PushRecipientJpaRepository extends JpaRepository<PushRecipientJpaEntity, String> {

    /** 루트 행 배타 잠금(SELECT … FOR UPDATE, 대기). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select recipient from PushRecipientJpaEntity recipient where recipient.explorerId = :explorerId")
    Optional<PushRecipientJpaEntity> lockById(@Param("explorerId") String explorerId);

    /** 없을 때만 루트 행을 넣는다(모두 켜짐, MySQL·H2 공통 문법). @return 넣은 행 수 */
    @Modifying
    @Query(value = "INSERT INTO push_recipient (explorer_id, mystery_enabled, streak_enabled, season_enabled, created_at, updated_at)"
        + " SELECT :explorerId, TRUE, TRUE, TRUE, :at, :at FROM DUAL"
        + " WHERE NOT EXISTS (SELECT 1 FROM push_recipient WHERE explorer_id = :explorerId)", nativeQuery = true)
    int insertIfAbsent(@Param("explorerId") String explorerId, @Param("at") Instant at);

    /** 미스터리를 켜 두고 기기가 있는 사람(탐험가 id 순, after 다음부터). */
    @Query("select recipient.explorerId from PushRecipientJpaEntity recipient where recipient.mysteryEnabled = true"
        + " and recipient.explorerId > :after"
        + " and exists (select 1 from PushDeviceJpaEntity device where device.explorerId = recipient.explorerId)"
        + " order by recipient.explorerId")
    List<String> reachableForMystery(@Param("after") String after, Pageable page);

    @Query("select recipient.explorerId from PushRecipientJpaEntity recipient where recipient.streakEnabled = true"
        + " and recipient.explorerId > :after"
        + " and exists (select 1 from PushDeviceJpaEntity device where device.explorerId = recipient.explorerId)"
        + " order by recipient.explorerId")
    List<String> reachableForStreak(@Param("after") String after, Pageable page);

    @Query("select recipient.explorerId from PushRecipientJpaEntity recipient where recipient.seasonEnabled = true"
        + " and recipient.explorerId > :after"
        + " and exists (select 1 from PushDeviceJpaEntity device where device.explorerId = recipient.explorerId)"
        + " order by recipient.explorerId")
    List<String> reachableForSeason(@Param("after") String after, Pageable page);
}
