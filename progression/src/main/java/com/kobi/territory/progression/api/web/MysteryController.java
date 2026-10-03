package com.kobi.territory.progression.api.web;

import com.kobi.territory.catalog.api.query.RegionCatalog;
import com.kobi.territory.catalog.api.query.RegionView;
import com.kobi.territory.common.identity.CurrentExplorer;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.progression.api.web.ProgressionDtos.MysteryRegionResponse;
import com.kobi.territory.progression.api.web.ProgressionDtos.MysteryWeekResponse;
import com.kobi.territory.progression.application.ProgressService;
import com.kobi.territory.progression.application.ProgressService.ThisWeekMystery;
import com.kobi.territory.progression.application.ProgressionCatalog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 이번 주 미스터리 지역(8단계). 지역 선택은 카탈로그(전체 사용자 공통, 주차로 결정적), "받았는지"는 내 진행 장부.
 * 보너스 XP 는 체크인 이벤트로 비동기 반영된다(체크인 직후 몇 백 ms 늦을 수 있음).
 */
@RestController
public class MysteryController {

    private final ProgressService progressService;
    private final ProgressionCatalog catalog;
    private final RegionCatalog regions;

    public MysteryController(ProgressService progressService, ProgressionCatalog catalog, RegionCatalog regions) {
        this.progressService = progressService;
        this.catalog = catalog;
        this.regions = regions;
    }

    @GetMapping("/mystery/this-week")
    public MysteryWeekResponse thisWeek(@CurrentExplorer ExplorerId explorerId) {
        ThisWeekMystery mystery = progressService.mysteryThisWeek(explorerId);
        RegionView region = regions.findRegion(RegionCode.of(mystery.week().regionCode())).orElseThrow();
        return new MysteryWeekResponse(mystery.week().weekId(), mystery.week().startsAt(), mystery.week().endsAt(),
            mystery.remainingSeconds(), new MysteryRegionResponse(region.code(), region.name(), region.provinceCode(),
                region.provinceName(), region.rarity()),
            catalog.mysteryBonus(), mystery.received(), mystery.receivedAt(), mystery.foundCount(), mystery.revealed());
    }
}
