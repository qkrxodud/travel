package com.kobi.territory.common.identity;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 요청의 탐험가 ExplorerId 를 받는다. 4단계부터 로그인 세션의 계정 → 그 탐험가가 먼저, 없으면 X-Explorer-Token 헤더(비밀 접근 토큰,
 * 3단계 결정 2). 헤더가 없으면 401 EXPLORER_TOKEN_REQUIRED, 모르는 토큰이면 401 EXPLORER_TOKEN_INVALID.
 * explorerId 자체는 공개 식별자라 인증에 쓰지 않는다.
 * <p>
 * {@link #required()} = false(5단계): 공개 경로(/u/{handle} 등)에서 "보는 사람이 누구인지"만 알고 싶을 때 — 인증 정보가 없거나
 * 풀리지 않으면 401 대신 null 을 넣는다(익명 방문자로 취급).
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentExplorer {
    String HEADER = "X-Explorer-Token";

    /** false 면 인증이 없거나 풀리지 않을 때 null(공개 경로의 선택적 방문자 식별). */
    boolean required() default true;
}
