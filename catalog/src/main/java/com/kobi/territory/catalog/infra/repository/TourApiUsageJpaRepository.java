package com.kobi.territory.catalog.infra.repository;

import com.kobi.territory.catalog.infra.entity.TourApiUsageJpaEntity;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TourApiUsageJpaRepository extends JpaRepository<TourApiUsageJpaEntity, LocalDate> {

    /** 상한보다 작을 때만 하나 늘린다(한 문장 — 동시 요청에도 상한을 넘지 않는다). @return 늘린 행 수(0 또는 1) */
    @Modifying
    @Query(value = "UPDATE tourapi_usage SET calls = calls + 1 WHERE usage_date = :day AND calls < :dailyLimit", nativeQuery = true)
    int increment(@Param("day") LocalDate day, @Param("dailyLimit") int dailyLimit);

    /** 그날 행을 0으로 만든다 — 이미 있으면 유일성 위반(호출자가 무시한다). */
    @Modifying
    @Query(value = "INSERT INTO tourapi_usage (usage_date, calls) VALUES (:day, 0)", nativeQuery = true)
    void insertDay(@Param("day") LocalDate day);
}
