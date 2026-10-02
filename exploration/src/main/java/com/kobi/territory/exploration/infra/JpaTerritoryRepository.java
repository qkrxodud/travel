package com.kobi.territory.exploration.infra;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.domain.ExplorationError;
import com.kobi.territory.exploration.domain.MapId;
import com.kobi.territory.exploration.domain.Memo;
import com.kobi.territory.exploration.domain.PhotoRef;
import com.kobi.territory.exploration.domain.RegionDirectory;
import com.kobi.territory.exploration.domain.Territory;
import com.kobi.territory.exploration.domain.TerritoryRepository;
import com.kobi.territory.exploration.domain.Verification;
import com.kobi.territory.exploration.domain.Visit;
import com.kobi.territory.exploration.domain.VisitDate;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/**
 * Territory ↔ territory(루트 행)·visit 테이블 변환.
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
        territories.save(new TerritoryJpaEntity(mapId.value(), createdAt));
    }

    @Override
    public boolean lock(MapId mapId) {
        return territories.lockByMapId(mapId.value()).isPresent();
    }

    @Override
    public Optional<MapId> lockPersonal(ExplorerId owner) {
        return territories.lockPersonal(owner.value()).stream().findFirst().map(territoryEntity -> MapId.of(territoryEntity.getMapId()));
    }

    @Override
    public Territory load(MapId mapId) {
        return Territory.restore(mapId, visits.findByMapId(mapId.value()).stream().map(this::toDomain).toList());
    }

    @Override
    public void save(Territory territory) {
        String mapId = territory.mapId().value();
        Map<String, VisitJpaEntity> existing = new HashMap<>();
        visits.findByMapId(mapId).forEach(visitEntity -> existing.put(key(visitEntity.getRegionCode(), visitEntity.getCheckedInBy()), visitEntity));
        for (Visit visit : territory.visits()) {
            VisitJpaEntity row = existing.remove(key(visit.regionCode().value(), visit.checkedInBy().value()));
            String photo = visit.photo() == null ? null : visit.photo().url();
            if (row == null) {
                visits.save(new VisitJpaEntity(mapId, visit.regionCode().value(), visit.checkedInBy().value(),
                    visit.verification().name(), visit.visitDate().value(), visit.memo().value(), photo, visit.visitedAt()));
            } else {
                row.update(visit.visitDate().value(), visit.memo().value(), photo);
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

    private Visit toDomain(VisitJpaEntity entity) {
        return new Visit(regions.require(RegionCode.of(entity.getRegionCode())), ExplorerId.of(entity.getCheckedInBy()),
            VisitDate.of(entity.getVisitDate()), Memo.of(entity.getMemo()), PhotoRef.ofNullable(entity.getPhotoUrl()),
            Verification.valueOf(entity.getVerification()), entity.getVisitedAt());
    }

    private static String key(String regionCode, String explorerId) {
        return regionCode + "|" + explorerId;
    }
}
