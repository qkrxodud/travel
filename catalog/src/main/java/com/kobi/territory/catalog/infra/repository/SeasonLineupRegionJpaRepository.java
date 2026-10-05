package com.kobi.territory.catalog.infra.repository;

import com.kobi.territory.catalog.infra.entity.SeasonLineupRegionJpaEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeasonLineupRegionJpaRepository extends JpaRepository<SeasonLineupRegionJpaEntity, SeasonLineupRegionJpaEntity.Key> {

    List<SeasonLineupRegionJpaEntity> findByRoundId(String roundId);

    List<SeasonLineupRegionJpaEntity> findByRoundIdIn(Collection<String> roundIds);

    /** 회차의 지역 행을 모두 지운다 — 먼저 밀어 쓰고(루트 변경 포함) 영속성 컨텍스트를 비운다(지운 행이 남아 다시 넣기와 엉키지 않게). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM SeasonLineupRegionJpaEntity row WHERE row.roundId = :roundId")
    void deleteByRound(@Param("roundId") String roundId);
}
