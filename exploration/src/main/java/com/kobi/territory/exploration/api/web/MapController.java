package com.kobi.territory.exploration.api.web;

import com.kobi.territory.common.identity.CurrentExplorer;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.api.web.ExplorationDtos.VisitResponse;
import com.kobi.territory.catalog.api.query.RegionCatalog;
import com.kobi.territory.exploration.api.web.MapDtos.CreateMapRequest;
import com.kobi.territory.exploration.api.web.MapDtos.DisputeRequest;
import com.kobi.territory.exploration.api.web.MapDtos.InviteCodeResponse;
import com.kobi.territory.exploration.api.web.MapDtos.JoinMapRequest;
import com.kobi.territory.exploration.api.web.MapDtos.LeaveResponse;
import com.kobi.territory.exploration.api.web.MapDtos.MapDetailResponse;
import com.kobi.territory.exploration.api.web.MapDtos.MapSummaryResponse;
import com.kobi.territory.exploration.api.web.MapDtos.SettingsRequest;
import com.kobi.territory.exploration.api.web.MapDtos.TransferOwnerRequest;
import com.kobi.territory.exploration.application.CheckInService;
import com.kobi.territory.exploration.application.ExplorationSettings;
import com.kobi.territory.exploration.application.MapService;
import com.kobi.territory.exploration.domain.map.MapId;
import com.kobi.territory.exploration.domain.map.MapSettings;
import com.kobi.territory.exploration.domain.map.MapVisibility;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공유 지도(3단계). 만들기·합류·탈퇴(7일 유예)·지도장 넘기기·초대코드 재발급·설정·방문 이의.
 * 체크인·영토 조회는 기존 /visits·/territory 에 mapId 를 넘겨 공유 지도에서 그대로 쓴다.
 */
@RestController
@RequestMapping("/maps")
public class MapController {

    private final MapService maps;
    private final CheckInService checkIns;
    private final RegionCatalog catalog;
    private final ExplorationSettings settings;

    public MapController(MapService maps, CheckInService checkIns, RegionCatalog catalog, ExplorationSettings settings) {
        this.maps = maps;
        this.checkIns = checkIns;
        this.catalog = catalog;
        this.settings = settings;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MapDetailResponse create(@CurrentExplorer ExplorerId explorerId, @Valid @RequestBody CreateMapRequest req) {
        var map = maps.create(explorerId, req.name(), req.countryCode());
        return detail(explorerId, map.id(), false);
    }

    @GetMapping
    public List<MapSummaryResponse> mine(@CurrentExplorer ExplorerId explorerId) {
        return maps.mapsOf(explorerId).stream().map(map -> MapSummaryResponse.of(map, explorerId)).toList();
    }

    @GetMapping("/{mapId}")
    public MapDetailResponse get(@CurrentExplorer ExplorerId explorerId, @PathVariable("mapId") String mapId) {
        return detail(explorerId, MapId.of(mapId), false);
    }

    @PostMapping("/join")
    public MapDetailResponse join(@CurrentExplorer ExplorerId explorerId, @Valid @RequestBody JoinMapRequest req) {
        var joined = maps.join(explorerId, req.inviteCode());
        return detail(explorerId, joined.map().id(), joined.rejoined());
    }

    @PostMapping("/{mapId}/leave")
    public LeaveResponse leave(@CurrentExplorer ExplorerId explorerId, @PathVariable("mapId") String mapId) {
        var left = maps.leave(explorerId, MapId.of(mapId));
        return new LeaveResponse(left.mapId().value(), left.hiddenRegions(), left.restoreUntil(),
            "내 영토 " + left.hiddenRegions() + "곳이 지도에서 사라졌어요. " + settings.leaveGraceDays()
                + "일 안에 초대코드로 돌아오면 복구돼요(선점은 돌아오지 않아요). 그사이 자리가 차면 다시 합류할 수 없어요.");
    }

    @PostMapping("/{mapId}/transfer-owner")
    public MapDetailResponse transferOwner(@CurrentExplorer ExplorerId explorerId, @PathVariable("mapId") String mapId,
                                           @Valid @RequestBody TransferOwnerRequest req) {
        maps.transferOwner(explorerId, MapId.of(mapId), ExplorerId.of(req.explorerId()));
        return detail(explorerId, MapId.of(mapId), false);
    }

    @PostMapping("/{mapId}/invite-code")
    public InviteCodeResponse regenerateInviteCode(@CurrentExplorer ExplorerId explorerId, @PathVariable("mapId") String mapId) {
        var map = maps.regenerateInviteCode(explorerId, MapId.of(mapId));
        return new InviteCodeResponse(map.id().value(), map.inviteCode().value());
    }

    @PutMapping("/{mapId}/settings")
    public MapDetailResponse changeSettings(@CurrentExplorer ExplorerId explorerId, @PathVariable("mapId") String mapId,
                                            @Valid @RequestBody SettingsRequest req) {
        maps.changeSettings(explorerId, MapId.of(mapId), new MapSettings(req.photoRequired(), req.dailyCheckInCap(),
            MapVisibility.parseOrPrivate(req.visibility())));
        return detail(explorerId, MapId.of(mapId), false);
    }

    @PutMapping("/{mapId}/visits/{code}/{memberId}/dispute")
    public VisitResponse dispute(@CurrentExplorer ExplorerId explorerId, @PathVariable("mapId") String mapId,
                                 @PathVariable("code") String code, @PathVariable("memberId") String memberId,
                                 @Valid @RequestBody DisputeRequest req) {
        var visit = checkIns.dispute(explorerId, MapId.of(mapId), RegionCode.of(code), ExplorerId.of(memberId), req.disputed());
        return VisitResponse.of(visit, catalog);
    }

    private MapDetailResponse detail(ExplorerId explorerId, MapId mapId, boolean rejoined) {
        return MapDetailResponse.of(maps.view(explorerId, mapId), explorerId, settings.leaveGraceDays(), rejoined);
    }
}
