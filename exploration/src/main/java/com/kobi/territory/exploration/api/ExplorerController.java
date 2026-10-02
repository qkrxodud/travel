package com.kobi.territory.exploration.api;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.api.ExplorationDtos.ExplorerResponse;
import com.kobi.territory.exploration.application.ExplorerService;
import com.kobi.territory.exploration.application.MapAccess;
import com.kobi.territory.exploration.domain.MapSelector;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 익명 탐험가 발급(1~3단계). 이후 요청은 X-Explorer-Id 헤더로 식별한다. */
@RestController
@RequestMapping("/explorers")
public class ExplorerController {

    private final ExplorerService explorers;
    private final MapAccess mapAccess;

    public ExplorerController(ExplorerService explorers, MapAccess mapAccess) {
        this.explorers = explorers;
        this.mapAccess = mapAccess;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExplorerResponse register() {
        var r = explorers.registerAnonymous();
        return new ExplorerResponse(r.explorer().id().value(), r.personalMap().id().value(), r.explorer().anonymous(),
            r.explorer().createdAt());
    }

    @GetMapping("/me")
    public ExplorerResponse me(@CurrentExplorer ExplorerId explorerId) {
        var explorer = mapAccess.requireExplorer(explorerId);
        var map = mapAccess.resolve(explorerId, MapSelector.PERSONAL).map();
        return new ExplorerResponse(explorer.id().value(), map.id().value(), explorer.anonymous(), explorer.createdAt());
    }
}
