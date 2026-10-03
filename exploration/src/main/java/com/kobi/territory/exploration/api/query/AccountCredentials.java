package com.kobi.territory.exploration.api.query;

import java.util.Optional;

/**
 * 로그인 세션 인증(4단계): 세션이 기억하는 신원(provider, subject) → explorerId. app-api 의 @CurrentExplorer 리졸버가
 * 세션을 먼저 보고, 없으면 {@link ExplorerCredentials}(익명 토큰)를 쓴다.
 */
public interface AccountCredentials {

    /** 이 신원이 연결된 활성 탐험가 id. 계정이 없으면 빈 값. */
    Optional<String> explorerIdByAccount(String provider, String subject);
}
