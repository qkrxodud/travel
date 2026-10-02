package com.kobi.territory.common.identity;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 요청의 X-Explorer-Token 헤더(비밀 접근 토큰, 3단계 결정 2)로 인증한 탐험가의 ExplorerId 를 받는다.
 * 헤더가 없으면 401 EXPLORER_TOKEN_REQUIRED, 모르는 토큰이면 401 EXPLORER_TOKEN_INVALID.
 * explorerId 자체는 공개 식별자라 인증에 쓰지 않는다(4단계 구글 로그인 때 세션으로 대체).
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentExplorer {
    String HEADER = "X-Explorer-Token";
}
