package com.kobi.territory.exploration.api.web;

import com.kobi.territory.common.identity.CurrentExplorer;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.api.web.ExplorationDtos.ExplorerResponse;
import com.kobi.territory.exploration.application.ExplorerService;
import com.kobi.territory.exploration.application.MapAccess;
import com.kobi.territory.exploration.domain.map.MapSelector;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 익명 탐험가 발급(1~3단계). 이후 요청은 발급 응답의 비밀 accessToken 을 X-Explorer-Token 헤더로 보내 인증한다(결정 2). */
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
        var registration = explorers.registerAnonymous();
        return new ExplorerResponse(registration.explorer().id().value(), registration.personalMap().id().value(),
            registration.explorer().anonymous(), registration.explorer().createdAt(), registration.accessToken().value(), null);
    }

    @GetMapping("/me")
    public ExplorerResponse me(@CurrentExplorer ExplorerId explorerId) {
        var explorer = mapAccess.requireExplorer(explorerId);
        var map = mapAccess.resolve(explorerId, MapSelector.PERSONAL).map();
        return new ExplorerResponse(explorer.id().value(), map.id().value(), explorer.anonymous(), explorer.createdAt(), null,
            explorer.handle() == null ? null : explorer.handle().value());
    }
}
