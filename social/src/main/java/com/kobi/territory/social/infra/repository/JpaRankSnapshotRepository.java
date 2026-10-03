package com.kobi.territory.social.infra.repository;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.social.domain.stats.ProvinceStat;
import com.kobi.territory.social.domain.stats.RankPercentile;
import com.kobi.territory.social.domain.stats.RankSnapshot;
import com.kobi.territory.social.domain.stats.RankSnapshotRepository;
import com.kobi.territory.social.domain.stats.RegionStat;
import com.kobi.territory.social.infra.entity.ProvinceStatJpaEntity;
import com.kobi.territory.social.infra.entity.RankPercentileJpaEntity;
import com.kobi.territory.social.infra.entity.RegionStatJpaEntity;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 집계 스냅숏 저장소 어댑터 — replace 는 세 테이블을 지우고 다시 넣는다(재계산 경로처럼 비교하지 않는다, 배치 insert). */
@Repository
class JpaRankSnapshotRepository implements RankSnapshotRepository {

    private final RankPercentileJpaRepository percentileRows;
    private final RegionStatJpaRepository regionRows;
    private final ProvinceStatJpaRepository provinceRows;
    private final EntityManager entityManager;

    private static final int FLUSH_EVERY = 500;

    JpaRankSnapshotRepository(RankPercentileJpaRepository percentileRows, RegionStatJpaRepository regionRows,
                              ProvinceStatJpaRepository provinceRows, EntityManager entityManager) {
        this.entityManager = entityManager;
        this.percentileRows = percentileRows;
        this.regionRows = regionRows;
        this.provinceRows = provinceRows;
    }

    @Override
    public void replace(RankSnapshot snapshot) {
        percentileRows.deleteAllInBatch();
        regionRows.deleteAllInBatch();
        provinceRows.deleteAllInBatch();
        insertAll(snapshot.percentiles().stream().map(RankPercentileJpaEntity::from).toList());
        insertAll(snapshot.regionStats().stream().map(RegionStatJpaEntity::from).toList());
        insertAll(snapshot.provinceStats().stream().map(ProvinceStatJpaEntity::from).toList());
    }

    /**
     * 지운 뒤라 전부 새 행 — merge(행마다 SELECT) 대신 persist, FLUSH_EVERY 마다 flush·clear 해 JDBC 배치(hibernate.jdbc.batch_size)로
     * 보내고 영속성 컨텍스트가 커지지 않게 한다(QA P3-7).
     */
    private void insertAll(List<?> entities) {
        for (int i = 0; i < entities.size(); i++) {
            entityManager.persist(entities.get(i));
            if ((i + 1) % FLUSH_EVERY == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }
        entityManager.flush();
    }

    @Override
    public Optional<RankPercentile> findPercentile(ExplorerId explorerId) {
        return percentileRows.findById(explorerId.value()).map(RankPercentileJpaEntity::toDomain);
    }

    @Override
    public List<RegionStat> regionStats() {
        return regionRows.findAll().stream().map(RegionStatJpaEntity::toDomain).toList();
    }

    @Override
    public List<ProvinceStat> provinceStats() {
        return provinceRows.findAll().stream().map(ProvinceStatJpaEntity::toDomain).toList();
    }
}
