package com.kobi.territory.api.security;

import com.kobi.territory.exploration.application.AccountService.LoginOutcome;
import com.kobi.territory.exploration.domain.explorer.LoginPlan;
import java.io.Serializable;

/**
 * 로그인 결과(/dev/login 응답·세션의 병합 안내).
 *
 * @param outcome CREATED(새 탐험가) | LINKED(지금 익명 탐험가를 계정에 연결) | MERGED(익명 기록을 기존 계정으로 병합) | SIGNED_IN(로그인만)
 * @param merge   MERGED 일 때만 — 옮긴 익명 기록 수(movedRegions)와 그중 새 지역 수(newRegions)
 */
public record LoginResponse(String explorerId, String handle, String email, String personalMapId, String outcome,
                            MergeNotice merge) implements Serializable {

    public record MergeNotice(String fromExplorerId, int movedRegions, int newRegions) implements Serializable {}

    static LoginResponse of(LoginOutcome login, String email) {
        MergeNotice merge = login.mergeSummary() == null ? null : new MergeNotice(login.mergedFrom().value(),
            login.mergeSummary().movedRegions(), login.mergeSummary().newRegions());
        return new LoginResponse(login.explorer().id().value(), login.explorer().handle().value(), email,
            login.personalMapId().value(), outcomeName(login.kind()), merge);
    }

    private static String outcomeName(LoginPlan.Kind kind) {
        return switch (kind) {
            case CREATE -> "CREATED";
            case LINK -> "LINKED";
            case MERGE -> "MERGED";
            case SIGN_IN -> "SIGNED_IN";
        };
    }
}
