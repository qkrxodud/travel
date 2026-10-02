package com.kobi.territory.progression.api.web;

import com.kobi.territory.catalog.api.query.ProgressionRules;
import com.kobi.territory.catalog.api.query.RegionCatalog;
import com.kobi.territory.catalog.api.query.RegionView;
import com.kobi.territory.catalog.api.query.RewardCalculator;
import com.kobi.territory.common.identity.CurrentExplorer;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.progression.api.web.ProgressionDtos.CollectionResponse;
import com.kobi.territory.progression.api.web.ProgressionDtos.SetRegionResponse;
import com.kobi.territory.progression.api.web.ProgressionDtos.SetResponse;
import com.kobi.territory.progression.application.CollectionBookService;
import com.kobi.territory.progression.domain.CollectionBook;
import com.kobi.territory.progression.domain.SetProgress;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 도감(지도 기준, 경로는 /collection). mapId 를 생략하면 개인 지도. 완성 기록(completedAt)은 지역을 취소해도 남는다. */
@RestController
public class CollectionBookController {

    private final CollectionBookService collectionBooks;
    private final ProgressionRules rules;
    private final RegionCatalog regions;
    private final RewardCalculator rewards;

    public CollectionBookController(CollectionBookService collectionBooks, ProgressionRules rules, RegionCatalog regions,
                                    RewardCalculator rewards) {
        this.collectionBooks = collectionBooks;
        this.rules = rules;
        this.regions = regions;
        this.rewards = rewards;
    }

    @GetMapping("/collection")
    public CollectionResponse collection(@CurrentExplorer ExplorerId explorerId,
                                         @RequestParam(value = "mapId", required = false) String mapId) {
        CollectionBook collectionBook = collectionBooks.view(explorerId, mapId);
        int bonus = rewards.setComplete().amount();
        List<SetResponse> sets = rules.sets().stream().map(set -> {
            SetProgress setProgress = collectionBook.of(set.id());
            List<SetRegionResponse> members = set.regionCodes().stream().map(code -> new SetRegionResponse(code,
                regions.findRegion(RegionCode.of(code)).map(RegionView::name).orElse(code),
                setProgress.collected().contains(RegionCode.of(code)))).toList();
            return new SetResponse(set.id(), set.name(), set.desc(), set.title(), setProgress.have(),
                set.regionCodes().size(), setProgress.completed(), setProgress.completedAt(), bonus, members);
        }).toList();
        return new CollectionResponse(collectionBook.mapId(), collectionBook.completedCount(), sets.size(), sets);
    }
}
