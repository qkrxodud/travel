package com.kobi.territory.exploration.infra;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        return territories.lockPersonal(owner.value()).stream().findFirst().map(t -> MapId.of(t.getMapId()));
    }

    @Override
    public Territory load(MapId mapId) {
        return Territory.restore(mapId, visits.findByMapId(mapId.value()).stream().map(this::toDomain).toList());
    }

    @Override
    public void save(Territory territory) {
        String mapId = territory.mapId().value();
        Map<String, VisitJpaEntity> existing = new HashMap<>();
        visits.findByMapId(mapId).forEach(e -> existing.put(key(e.getRegionCode(), e.getCheckedInBy()), e));
        for (Visit v : territory.visits()) {
            VisitJpaEntity row = existing.remove(key(v.regionCode().value(), v.checkedInBy().value()));
            String photo = v.photo() == null ? null : v.photo().url();
            if (row == null) {
                visits.save(new VisitJpaEntity(mapId, v.regionCode().value(), v.checkedInBy().value(),
                    v.verification().name(), v.visitDate().value(), v.memo().value(), photo, v.visitedAt()));
            } else {
                row.update(v.visitDate().value(), v.memo().value(), photo);
            }
        }
        List<VisitJpaEntity> removed = List.copyOf(existing.values());
        if (!removed.isEmpty()) {
            visits.deleteAll(removed);
            visits.flush();
        }
    }

    private Visit toDomain(VisitJpaEntity e) {
        return new Visit(regions.require(RegionCode.of(e.getRegionCode())), ExplorerId.of(e.getCheckedInBy()),
            VisitDate.of(e.getVisitDate()), Memo.of(e.getMemo()), PhotoRef.ofNullable(e.getPhotoUrl()),
            Verification.valueOf(e.getVerification()), e.getVisitedAt());
    }

    private static String key(String regionCode, String explorerId) {
        return regionCode + "|" + explorerId;
    }
}
