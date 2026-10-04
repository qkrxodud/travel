package com.kobi.territory.exploration.infra.repository;

import com.kobi.territory.exploration.infra.entity.VisitJpaEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

interface VisitJpaRepository extends JpaRepository<VisitJpaEntity, Long> {

    List<VisitJpaEntity> findByMapId(String mapId);

    /** 이 탐험가의 보이는(숨기지 않은) 방문이 어느 지도에든 이 지역에 있는지. */
    boolean existsByCheckedInByAndRegionCodeAndHiddenAtIsNull(String checkedInBy, String regionCode);

    /** 이 탐험가의 보이는 방문 중 이 지역의 가장 이른 처리 시각(어느 지도든, 9단계 재방문 도장). 없으면 null. */
    @Query("select min(visit.visitedAt) from VisitJpaEntity visit where visit.checkedInBy = :checkedInBy"
        + " and visit.regionCode = :regionCode and visit.hiddenAt is null")
    Instant firstVisibleVisitAt(@Param("checkedInBy") String checkedInBy, @Param("regionCode") String regionCode);

    /** 이 탐험가의 보이는 방문이 있는 지역 중 regionCodes 에 든 것. */
    @Query("select distinct visit.regionCode from VisitJpaEntity visit where visit.checkedInBy = :checkedInBy"
        + " and visit.regionCode in :regionCodes and visit.hiddenAt is null")
    List<String> visibleRegionCodesAmong(@Param("checkedInBy") String checkedInBy, @Param("regionCodes") Collection<String> regionCodes);
}
