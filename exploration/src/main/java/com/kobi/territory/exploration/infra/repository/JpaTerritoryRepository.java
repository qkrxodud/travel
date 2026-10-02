package com.kobi.territory.exploration.infra.repository;

import com.kobi.territory.exploration.infra.entity.TerritoryJpaEntity;
import com.kobi.territory.exploration.infra.entity.VisitJpaEntity;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.domain.ExplorationError;
import com.kobi.territory.exploration.domain.map.MapId;
import com.kobi.territory.exploration.domain.territory.Memo;
import com.kobi.territory.exploration.domain.territory.PhotoRef;
import com.kobi.territory.exploration.domain.territory.RegionDirectory;
import com.kobi.territory.exploration.domain.territory.Territory;
import com.kobi.territory.exploration.domain.territory.TerritoryRepository;
import com.kobi.territory.exploration.domain.territory.Verification;
import com.kobi.territory.exploration.domain.territory.Visit;
import com.kobi.territory.exploration.domain.territory.VisitDate;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/**
 * Territory 저장소 어댑터 — territory(루트 행)·visit 테이블. 행 ↔ 도메인 변환은 엔티티(VisitJpaEntity·TerritoryJpaEntity)가 한다.
 * 커맨드는 territory 행을 PESSIMISTIC_WRITE(SELECT ... FOR UPDATE)로 먼저 잠가 같은 지도의 동시 체크인을 직렬화한다
 * (하루 상한·nth·isFirstInProvince·isFirstClaim 경합 방지). expedition_map 행은 잠그지 않는다(§2-9 분리).
 * save 는 로드 시점과 비교해 추가·수정·삭제(물리 삭제)를 반영한다.
 */
@Repository
class JpaTerritoryRepository implements TerritoryRepository {

    private final VisitJpaRepository visits;
    private final TerritoryJpaRepository territories;
    private final RegionDirectory regions;

    JpaTerritoryRepository(VisitJpaRepository visits, TerritoryJpaRepository territories, RegionDirectory regions) {
        this.visits = visits;
        this.territories = territories;
        this.regions = regions;
    }

    @Override
    public void create(MapId mapId, Instant createdAt) {
        territories.save(TerritoryJpaEntity.create(mapId, createdAt));
    }

    @Override
    public boolean lock(MapId mapId) {
        return territories.lockByMapId(mapId.value()).isPresent();
    }

    @Override
    public Optional<MapId> lockPersonal(ExplorerId owner) {
        return territories.lockPersonal(owner.value()).stream().findFirst().map(TerritoryJpaEntity::mapId);
    }

    @Override
    public Territory load(MapId mapId) {
        return Territory.restore(mapId, visits.findByMapId(mapId.value()).stream()
            .map(visitEntity -> visitEntity.toDomain(regions.require(visitEntity.regionCode())))
            .toList());
    }

    @Override
    public void save(Territory territory) {
        Map<String, VisitJpaEntity> existing = new HashMap<>();
        visits.findByMapId(territory.mapId().value()).forEach(visitEntity -> existing.put(visitEntity.identity(), visitEntity));
        for (Visit visit : territory.visits()) {
            VisitJpaEntity visitEntity = existing.remove(VisitJpaEntity.identity(visit));
            if (visitEntity == null) {
                visits.save(VisitJpaEntity.from(territory.mapId(), visit));
            } else {
                visitEntity.apply(visit);
            }
        }
        List<VisitJpaEntity> removed = List.copyOf(existing.values());
        if (!removed.isEmpty()) {
            visits.deleteAll(removed);
            visits.flush(); // 같은 트랜잭션의 재체크인이 UNIQUE에 걸리지 않게 삭제를 먼저 반영
        }
        flushTranslatingDuplicate();
    }

    /**
     * 추가한 방문을 즉시 반영하고, 동시 요청이 visit UNIQUE(지도, 지역, 멤버)에 걸리면 도메인 오류 DUPLICATE_VISIT 로
     * 번역한다(QA N3 — 제약 이름은 이 테이블을 아는 infra 의 지식이다). 그 밖의 무결성 위반은 그대로 던진다.
     */
    private void flushTranslatingDuplicate() {
        try {
            visits.flush();
        } catch (DataIntegrityViolationException exception) {
            throw translate(exception);
        }
    }

    static RuntimeException translate(DataIntegrityViolationException exception) {
        String cause = String.valueOf(exception.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT);
        return cause.contains(VISIT_UNIQUE) ? ExplorationError.DUPLICATE_VISIT.exception("(동시 요청)") : exception;
    }

    /** V1 visit 테이블 UNIQUE 제약 이름. */
    static final String VISIT_UNIQUE = "uq_visit_map_region_member";
}
