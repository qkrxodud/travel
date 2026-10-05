package com.kobi.territory.catalog.infra.repository;

import com.kobi.territory.catalog.domain.lineup.SeasonLineup;
import com.kobi.territory.catalog.domain.lineup.SeasonLineupRepository;
import com.kobi.territory.catalog.infra.entity.SeasonLineupJpaEntity;
import com.kobi.territory.catalog.infra.entity.SeasonLineupRegionJpaEntity;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

/**
 * 회차 지역 목록 저장소 어댑터(season_lineup + season_lineup_region). 루트 행은 버전으로 낙관적 잠금, 지역 행은 그 회차 것을 지우고 다시 넣는다
 * (후보·확정본이 통째로 바뀌는 단위라). 행 ↔ 도메인 변환은 엔티티가 한다.
 */
@Repository
class JpaSeasonLineupRepository implements SeasonLineupRepository {

    private final SeasonLineupJpaRepository rows;
    private final SeasonLineupRegionJpaRepository regionRows;
    private final EntityManager entityManager;

    JpaSeasonLineupRepository(SeasonLineupJpaRepository rows, SeasonLineupRegionJpaRepository regionRows, EntityManager entityManager) {
        this.rows = rows;
        this.regionRows = regionRows;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<SeasonLineup> find(String roundId) {
        return rows.findById(roundId).map(row -> row.toDomain(regionRows.findByRoundId(roundId)));
    }

    @Override
    public List<SeasonLineup> findAll(Collection<String> roundIds) {
        if (roundIds.isEmpty()) return List.of();
        Map<String, List<SeasonLineupRegionJpaEntity>> regionsByRound = regionRows.findByRoundIdIn(roundIds).stream()
            .collect(Collectors.groupingBy(SeasonLineupRegionJpaEntity::roundId));
        return rows.findAllById(roundIds).stream()
            .map(row -> row.toDomain(regionsByRound.getOrDefault(row.roundId(), List.of()))).toList();
    }

    @Override
    public void save(SeasonLineup lineup) {
        if (lineup.isNew()) {
            entityManager.persist(SeasonLineupJpaEntity.from(lineup));
        } else {
            SeasonLineupJpaEntity row = rows.findById(lineup.roundId())
                .orElseThrow(() -> new ObjectOptimisticLockingFailureException(SeasonLineupJpaEntity.class, lineup.roundId()));
            if (row.version() != lineup.version()) {
                throw new ObjectOptimisticLockingFailureException(SeasonLineupJpaEntity.class, lineup.roundId());
            }
            row.apply(lineup);
        }
        regionRows.deleteByRound(lineup.roundId());
        List<SeasonLineupRegionJpaEntity> regions = new ArrayList<>();
        regions.addAll(SeasonLineupRegionJpaEntity.rowsOf(lineup.roundId(), SeasonLineupRegionJpaEntity.CANDIDATE, lineup.candidate()));
        regions.addAll(SeasonLineupRegionJpaEntity.rowsOf(lineup.roundId(), SeasonLineupRegionJpaEntity.CONFIRMED, lineup.confirmed()));
        regions.forEach(entityManager::persist);
        entityManager.flush();
    }
}
