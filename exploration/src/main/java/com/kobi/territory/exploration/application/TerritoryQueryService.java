package com.kobi.territory.exploration.application;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.api.query.MapVisitView;
import com.kobi.territory.exploration.api.query.ProfileMapView;
import com.kobi.territory.exploration.api.query.TerritoryQuery;
import com.kobi.territory.exploration.domain.territory.ConquestRate;
import com.kobi.territory.exploration.domain.map.ExpeditionMap;
import com.kobi.territory.exploration.domain.map.ExpeditionMapRepository;
import com.kobi.territory.exploration.api.event.RegionVisited;
import com.kobi.territory.exploration.domain.ExplorationError;
import com.kobi.territory.exploration.domain.explorer.ExplorerRepository;
import com.kobi.territory.exploration.domain.map.MapId;
import com.kobi.territory.exploration.domain.map.MapSelector;
import com.kobi.territory.exploration.domain.territory.Territory;
import com.kobi.territory.exploration.domain.territory.TerritoryRepository;
import com.kobi.territory.exploration.domain.territory.Visit;
import com.kobi.territory.exploration.domain.territory.VisitView;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 영토 조회. 정복률은 Territory를 로드한 뒤 메모리에서 계산한다(250건). */
@Service
@Transactional(readOnly = true)
public class TerritoryQueryService implements TerritoryQuery {

    private final MapAccess mapAccess;
    private final TerritoryRepository territories;
    private final ExpeditionMapRepository maps;
    private final CatalogRegionDirectory regions;
    private final ExplorerRepository explorers;

    public TerritoryQueryService(MapAccess mapAccess, TerritoryRepository territories, ExpeditionMapRepository maps,
                                 CatalogRegionDirectory regions, ExplorerRepository explorers) {
        this.mapAccess = mapAccess;
        this.territories = territories;
        this.maps = maps;
        this.regions = regions;
        this.explorers = explorers;
    }

    public TerritoryOverview overview(ExplorerId explorerId, String mapId) {
        MapMembership mm = mapAccess.resolve(explorerId, MapSelector.of(mapId));
        Territory territory = territories.load(mm.map().id());
        return new TerritoryOverview(mm.map(), territory.conquest(regions.provinceTotals()), territory.viewedBy(explorerId),
            territory.claims());
    }

    @Override
    public List<String> claimedRegionCodes(String mapId) {
        return territories.load(MapId.of(mapId)).claimedRegions().stream().map(region -> region.code().value()).toList();
    }

    @Override
    public String personalMapId(String explorerId) {
        return maps.findPersonalMap(ExplorerId.of(explorerId))
            .orElseThrow(ExplorationError.EXPLORER_NOT_FOUND::exception).id().value();
    }

    @Override
    public String resolveMapId(String explorerId, String mapIdOrNull) {
        return mapAccess.resolve(ExplorerId.of(explorerId), MapSelector.of(mapIdOrNull)).map().id().value();
    }

    @Override
    public List<String> mapIdsOf(String explorerId) {
        return maps.mapIdsOf(ExplorerId.of(explorerId)).stream().map(MapId::value).toList();
    }

    /** 활성 탐험가 id(병합돼 비활성인 탐험가 제외 — 재계산 배치 대상). */
    @Override
    public List<String> explorerIds() {
        return explorers.activeIds().stream().map(ExplorerId::value).toList();
    }

    @Override
    public List<RegionVisited> visitHistory(String mapId) {
        List<ExplorerId> memberIds = maps.findById(MapId.of(mapId)).map(ExpeditionMap::memberIds).orElse(List.of());
        return territories.load(MapId.of(mapId)).history().stream()
            .map(result -> CheckInService.regionVisited(result, memberIds)).toList();
    }

    @Override
    public List<String> memberIdsOf(String mapId) {
        return maps.findById(MapId.of(mapId)).map(map -> map.memberIds().stream().map(ExplorerId::value).toList())
            .orElse(List.of());
    }

    @Override
    public boolean visitsRegionAnywhere(String explorerId, String regionCode) {
        return territories.hasVisibleVisit(ExplorerId.of(explorerId), RegionCode.of(regionCode));
    }

    /**
     * @param visits 요청한 탐험가가 보는 방문(방문일 최근 순 — 다른 멤버 메모·사진은 비워짐)
     * @param claims 지역마다 선점 방문
     */
    public record TerritoryOverview(ExpeditionMap map, ConquestRate conquest, List<VisitView> visits, List<Visit> claims) {}

    @Override
    public List<ProfileMapView> profileMapsOf(String explorerId) {
        ExplorerId owner = ExplorerId.of(explorerId);
        return maps.mapsOf(owner).stream().filter(map -> map.openToProfileOf(owner))
            .map(map -> new ProfileMapView(map.id().value(), map.name(), map.memberCount(), ExpeditionMap.MAX_MEMBERS))
            .toList();
    }

    @Override
    public List<MapVisitView> mapVisits(String mapId) {
        Territory territory = territories.load(MapId.of(mapId));
        return territory.visits().stream()
            .map(visit -> new MapVisitView(visit.checkedInBy().value(), visit.regionCode().value(), visit.region().rarity(),
                territory.claimOrderOf(visit), visit.disputed()))
            .toList();
    }
}
