package com.kobi.territory.exploration.api.query;

import java.util.Optional;

/**
 * 탐험가 인증(3단계 결정 2): 비밀 접근 토큰 → explorerId. 토큰은 해시로만 저장한다. app-api 의 @CurrentExplorer 리졸버가 쓴다.
 * 4단계 구글 로그인 때 세션으로 대체된다.
 */
public interface ExplorerCredentials {

    /** 토큰이 가리키는 탐험가 id. 모르는 토큰이면 빈 값. */
    Optional<String> explorerIdByToken(String accessToken);
}
