package com.kobi.territory.progression.infra.repository;

import com.kobi.territory.progression.infra.entity.ExplorerRegionJpaEntity;
import com.kobi.territory.progression.infra.entity.ProvinceTallyRow;
import com.kobi.territory.progression.infra.entity.RegionVisitorRow;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ExplorerRegionJpaRepository extends JpaRepository<ExplorerRegionJpaEntity, ExplorerRegionJpaEntity.Key> {

    List<ExplorerRegionJpaEntity> findByExplorerId(String explorerId);

    /** 5단계: 주어진 탐험가들의 시·도별 활성 지역 수. */
    @Query("select r.explorerId as explorerId, r.provinceCode as provinceCode, count(r) as regionCount "
        + "from ExplorerRegionJpaEntity r where r.activeMapCount > 0 and r.explorerId in :explorerIds "
        + "group by r.explorerId, r.provinceCode")
    List<ProvinceTallyRow> provinceTallies(@Param("explorerIds") Collection<String> explorerIds);

    /** 5단계: 모든 탐험가의 시·도별 활성 지역 수. */
    @Query("select r.explorerId as explorerId, r.provinceCode as provinceCode, count(r) as regionCount "
        + "from ExplorerRegionJpaEntity r where r.activeMapCount > 0 group by r.explorerId, r.provinceCode")
    List<ProvinceTallyRow> allProvinceTallies();

    /** 5단계: 지역별 활성 탐험가 수(주어진 탐험가 중에서만). */
    @Query("select r.regionCode as regionCode, count(r) as visitorCount "
        + "from ExplorerRegionJpaEntity r where r.activeMapCount > 0 and r.explorerId in :explorerIds group by r.regionCode")
    List<RegionVisitorRow> regionVisitors(@Param("explorerIds") Collection<String> explorerIds);

    /** 5단계: 탐험가의 활성 지역 코드(코드 순). */
    @Query("select r.regionCode from ExplorerRegionJpaEntity r where r.explorerId = :explorerId and r.activeMapCount > 0 "
        + "order by r.regionCode")
    List<String> activeRegionCodes(@Param("explorerId") String explorerId);
}
