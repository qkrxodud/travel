package com.kobi.territory.exploration.api.web;

import com.kobi.territory.catalog.api.query.RegionCatalog;
import com.kobi.territory.catalog.api.query.RewardCalculator;
import com.kobi.territory.common.identity.CurrentExplorer;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.api.web.RevisitDtos.StampBookResponse;
import com.kobi.territory.exploration.api.web.RevisitDtos.StampResponse;
import com.kobi.territory.exploration.api.web.RevisitDtos.StampStatusResponse;
import com.kobi.territory.exploration.application.RevisitService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 재방문 도장(9단계) — 이미 칠한 지역에 처음 칠한 해보다 뒤의 해에 "다시 다녀왔어요". 영토·선점·정복률은 바뀌지 않는다. */
@RestController
@RequestMapping("/revisits")
public class RevisitController {

    private final RevisitService revisits;
    private final RegionCatalog catalog;
    private final RewardCalculator rewards;

    public RevisitController(RevisitService revisits, RegionCatalog catalog, RewardCalculator rewards) {
        this.revisits = revisits;
        this.catalog = catalog;
        this.rewards = rewards;
    }

    @PostMapping("/{code}")
    @ResponseStatus(HttpStatus.CREATED)
    public StampResponse stamp(@CurrentExplorer ExplorerId explorerId, @PathVariable("code") String code) {
        return StampResponse.of(revisits.stamp(explorerId, RegionCode.of(code)), catalog, rewards.revisitStamp().amount());
    }

    @GetMapping
    public StampBookResponse stamps(@CurrentExplorer ExplorerId explorerId) {
        return StampBookResponse.of(revisits.view(explorerId), catalog, rewards.revisitStamp().amount());
    }

    @GetMapping("/{code}")
    public StampStatusResponse status(@CurrentExplorer ExplorerId explorerId, @PathVariable("code") String code) {
        return StampStatusResponse.of(revisits.status(explorerId, RegionCode.of(code)), catalog, rewards.revisitStamp().amount());
    }
}
