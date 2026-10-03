package com.kobi.territory.sharing.api.web;

import com.kobi.territory.common.identity.CurrentExplorer;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.sharing.api.web.SharingDtos.RecapMonthResponse;
import com.kobi.territory.sharing.api.web.SharingDtos.RecapProvinceResponse;
import com.kobi.territory.sharing.api.web.SharingDtos.RecapRegionResponse;
import com.kobi.territory.sharing.api.web.SharingDtos.RecapResponse;
import com.kobi.territory.sharing.application.MyRecap;
import com.kobi.territory.sharing.application.RecapService;
import com.kobi.territory.sharing.domain.showcase.YearRecap;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 내 연간 리캡 JSON(06 QA P2-1). 인증 = @CurrentExplorer(로그인 세션 → 익명 토큰), 본인 것만. */
@RestController
public class MyRecapController {

    private final RecapService recaps;

    public MyRecapController(RecapService recaps) {
        this.recaps = recaps;
    }

    @GetMapping("/me/recap")
    public RecapResponse recap(@CurrentExplorer ExplorerId explorerId,
                               @RequestParam(value = "year", required = false) Integer year,
                               @RequestParam(value = "mapId", required = false) String mapId) {
        MyRecap myRecap = recaps.recapOf(explorerId, year, mapId);
        YearRecap recap = myRecap.recap();
        return new RecapResponse(recap.year(), myRecap.mapId(), recap.newRegions(), recap.monthCounts(),
            Optional.ofNullable(recap.topProvince())
                .map(top -> new RecapProvinceResponse(top.provinceCode(), top.provinceName(), top.count())).orElse(null),
            Optional.ofNullable(recap.rarest()).map(region -> new RecapRegionResponse(region.code(), region.name(),
                region.provinceCode(), region.provinceName(), region.rarity().name())).orElse(null),
            recap.newProvinces(),
            Optional.ofNullable(recap.busiestMonth())
                .map(busiest -> new RecapMonthResponse(busiest.month(), busiest.count())).orElse(null),
            myRecap.setsCompleted());
    }
}
