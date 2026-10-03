package com.kobi.territory.exploration.api.web;

import com.kobi.territory.common.identity.CurrentExplorer;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.api.web.ExplorationDtos.HandleRequest;
import com.kobi.territory.exploration.api.web.ExplorationDtos.HandleResponse;
import com.kobi.territory.exploration.application.AccountService;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 계정 탐험가의 handle 변경(4단계). 로그인(세션) 탐험가만 — 익명 토큰 요청은 401 LOGIN_REQUIRED.
 * 형식 400 HANDLE_INVALID·HANDLE_RESERVED, 중복 409 HANDLE_TAKEN.
 */
@RestController
public class AccountController {

    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PutMapping("/me/handle")
    public HandleResponse changeHandle(@CurrentExplorer ExplorerId explorerId, @RequestBody HandleRequest request) {
        var explorer = accounts.changeHandle(explorerId, request == null ? null : request.handle());
        return new HandleResponse(explorer.id().value(), explorer.handle().value());
    }
}
