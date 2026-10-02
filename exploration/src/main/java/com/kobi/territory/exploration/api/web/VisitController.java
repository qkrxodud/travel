package com.kobi.territory.exploration.api.web;

import com.kobi.territory.catalog.api.query.RegionCatalog;
import com.kobi.territory.common.identity.CurrentExplorer;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.api.web.ExplorationDtos.CheckInRequest;
import com.kobi.territory.exploration.api.web.ExplorationDtos.CheckInResponse;
import com.kobi.territory.exploration.api.web.ExplorationDtos.EditVisitRequest;
import com.kobi.territory.exploration.api.web.ExplorationDtos.PreviewResponse;
import com.kobi.territory.exploration.api.web.ExplorationDtos.VisitResponse;
import com.kobi.territory.exploration.application.CheckInCommand;
import com.kobi.territory.exploration.application.CheckInService;
import com.kobi.territory.exploration.application.EditVisitCommand;
import com.kobi.territory.exploration.domain.territory.VisitFacts;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 체크인·수정·취소·미리보기. mapId 를 생략하면 요청한 탐험가의 개인 지도. */
@RestController
@RequestMapping("/visits")
public class VisitController {

    private final CheckInService checkIns;
    private final RegionCatalog catalog;

    public VisitController(CheckInService checkIns, RegionCatalog catalog) {
        this.checkIns = checkIns;
        this.catalog = catalog;
    }

    @GetMapping("/preview")
    public PreviewResponse preview(@CurrentExplorer ExplorerId explorerId, @RequestParam("region") String region,
                                   @RequestParam(value = "mapId", required = false) String mapId) {
        return PreviewResponse.of(checkIns.preview(explorerId, mapId, RegionCode.of(region)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CheckInResponse checkIn(@CurrentExplorer ExplorerId explorerId, @Valid @RequestBody CheckInRequest req) {
        CheckInService.CheckInOutcome outcome = checkIns.checkIn(new CheckInCommand(explorerId, req.mapId(), RegionCode.of(req.regionCode()),
            req.visitDate(), req.memo(), req.photoUrl()));
        PreviewResponse preview = PreviewResponse.of(outcome.preview());
        VisitFacts facts = outcome.result().facts();
        return new CheckInResponse(outcome.result().mapId().value(), VisitResponse.of(outcome.view(), catalog),
            facts.nth(), facts.firstInProvince(), facts.firstClaim(), preview.xp(), preview.items());
    }

    @PatchMapping("/{code}")
    public VisitResponse edit(@CurrentExplorer ExplorerId explorerId, @PathVariable("code") String code,
                              @RequestParam(value = "mapId", required = false) String mapId,
                              @RequestBody EditVisitRequest req) {
        CheckInService.EditOutcome edited = checkIns.edit(new EditVisitCommand(explorerId, mapId, RegionCode.of(code), req.visitDate(),
            req.memo(), req.photoUrl()));
        return VisitResponse.of(edited.view(), catalog);
    }

    @DeleteMapping("/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@CurrentExplorer ExplorerId explorerId, @PathVariable("code") String code,
                       @RequestParam(value = "mapId", required = false) String mapId) {
        checkIns.cancel(explorerId, mapId, RegionCode.of(code));
    }
}
