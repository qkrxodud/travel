package com.kobi.territory.progression.infra.repository;

import com.kobi.territory.progression.infra.entity.FreezeHeldRow;
import com.kobi.territory.progression.infra.entity.StreakFreezeJpaEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface StreakFreezeJpaRepository extends JpaRepository<StreakFreezeJpaEntity, Long> {

    List<StreakFreezeJpaEntity> findByExplorerIdOrderByIdAsc(String explorerId);

    /** 탐험가별 가진 보호권 수(장부 합계, 12단계). 줄이 없는 탐험가는 결과에 없다. */
    @Query("select freeze.explorerId as explorerId, sum(freeze.amount) as held from StreakFreezeJpaEntity freeze"
        + " where freeze.explorerId in :explorerIds group by freeze.explorerId")
    List<FreezeHeldRow> heldBy(@Param("explorerIds") Collection<String> explorerIds);
}
