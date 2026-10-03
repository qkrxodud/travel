package com.kobi.territory.api.security;

import com.kobi.territory.common.error.ErrorKind;
import com.kobi.territory.common.error.TerritoryException;
import com.kobi.territory.common.identity.CurrentExplorer;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.api.query.AccountCredentials;
import com.kobi.territory.exploration.api.query.ExplorerCredentials;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 요청 → 탐험가(4단계 인증 공존). 순서: <b>로그인 세션의 계정 → 그 탐험가</b>, 없으면 {@code X-Explorer-Token}(익명 토큰).
 * 세션이 인증돼 있는데 계정을 못 찾으면 401 ACCOUNT_NOT_FOUND(토큰으로 넘어가지 않는다 — 로그인 사용자가 남의 익명 토큰으로 바뀌지 않게).
 * 토큰이 있는데 모르면 401 EXPLORER_TOKEN_INVALID. 둘 다 없으면 빈 값.
 */
@Component
public class ExplorerAuthentication {

    public static final String TOKEN_REQUIRED = "EXPLORER_TOKEN_REQUIRED";
    public static final String TOKEN_INVALID = "EXPLORER_TOKEN_INVALID";
    public static final String ACCOUNT_NOT_FOUND = "ACCOUNT_NOT_FOUND";

    private final AccountCredentials accounts;
    private final ExplorerCredentials tokens;

    public ExplorerAuthentication(AccountCredentials accounts, ExplorerCredentials tokens) {
        this.accounts = accounts;
        this.tokens = tokens;
    }

    public Optional<ExplorerId> resolve(HttpServletRequest request) {
        Optional<SessionAccount> session = SessionAccount.of(SecurityContextHolder.getContext().getAuthentication());
        if (session.isPresent()) {
            return Optional.of(accounts.explorerIdByAccount(session.get().provider(), session.get().subject()).map(ExplorerId::of)
                .orElseThrow(() -> new TerritoryException(ACCOUNT_NOT_FOUND, ErrorKind.UNAUTHENTICATED,
                    "로그인 계정을 찾을 수 없어요. 다시 로그인해 주세요.")));
        }
        return anonymous(request);
    }

    /** 익명 토큰만 본다(로그인 때 "지금 기기의 익명 탐험가" 찾기). 헤더가 없으면 빈 값, 모르는 토큰이면 401. */
    public Optional<ExplorerId> anonymous(HttpServletRequest request) {
        String token = request.getHeader(CurrentExplorer.HEADER);
        if (token == null || token.isBlank()) return Optional.empty();
        return Optional.of(tokens.explorerIdByToken(token).map(ExplorerId::of).orElseThrow(() -> new TerritoryException(
            TOKEN_INVALID, ErrorKind.UNAUTHENTICATED, "접근 토큰을 알 수 없어요. 다시 발급해 주세요(POST /explorers).")));
    }

    /** 반드시 있어야 하는 경우(@CurrentExplorer) — 없으면 401 EXPLORER_TOKEN_REQUIRED. */
    public ExplorerId require(HttpServletRequest request) {
        return resolve(request).orElseThrow(() -> new TerritoryException(TOKEN_REQUIRED, ErrorKind.UNAUTHENTICATED,
            "로그인하거나 " + CurrentExplorer.HEADER + " 헤더로 접근 토큰을 보내 주세요(POST /explorers 로 발급)."));
    }
}
