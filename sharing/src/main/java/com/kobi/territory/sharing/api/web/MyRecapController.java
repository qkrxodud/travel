package com.kobi.territory.sharing.api.web;

import com.kobi.territory.common.identity.CurrentExplorer;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.sharing.api.web.SharingDtos.RecapResponse;
import com.kobi.territory.sharing.application.RecapService;
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
        return RecapResponse.of(recaps.recapOf(explorerId, year, mapId));
    }
}
