package com.kobi.territory.exploration.application;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.api.TerritoryQuery;
import com.kobi.territory.exploration.domain.ConquestRate;
import com.kobi.territory.exploration.domain.ExpeditionMap;
import com.kobi.territory.exploration.domain.ExpeditionMapRepository;
import com.kobi.territory.exploration.domain.ExplorationError;
import com.kobi.territory.exploration.domain.MapId;
import com.kobi.territory.exploration.domain.MapSelector;
import com.kobi.territory.exploration.domain.Territory;
import com.kobi.territory.exploration.domain.TerritoryRepository;
import com.kobi.territory.exploration.domain.Visit;
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

    public TerritoryQueryService(MapAccess mapAccess, TerritoryRepository territories, ExpeditionMapRepository maps,
                                 CatalogRegionDirectory regions) {
        this.mapAccess = mapAccess;
        this.territories = territories;
        this.maps = maps;
        this.regions = regions;
    }

    public TerritoryOverview overview(ExplorerId explorerId, String mapId) {
        MapMembership mm = mapAccess.resolve(explorerId, MapSelector.of(mapId));
        Territory territory = territories.load(mm.map().id());
        return new TerritoryOverview(mm.map(), territory.conquest(regions.provinceTotals()), territory.visitsRecentFirst());
    }

    @Override
    public List<String> claimedRegionCodes(String mapId) {
        return territories.load(MapId.of(mapId)).claimedRegions().stream().map(r -> r.code().value()).toList();
    }

    @Override
    public String personalMapId(String explorerId) {
        return maps.findPersonalMap(ExplorerId.of(explorerId))
            .orElseThrow(ExplorationError.EXPLORER_NOT_FOUND::exception).id().value();
    }

    /** @param visits 방문일 최근 순(같으면 처리 시각 최근 순) */
    public record TerritoryOverview(ExpeditionMap map, ConquestRate conquest, List<Visit> visits) {}
}
