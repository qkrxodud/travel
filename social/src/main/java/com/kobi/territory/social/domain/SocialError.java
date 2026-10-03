package com.kobi.territory.social.domain;

import com.kobi.territory.common.error.ErrorKind;
import com.kobi.territory.common.error.TerritoryException;

/** 소셜 컨텍스트 오류 코드. code 문자열은 API 계약이다. */
public enum SocialError {
    /** 팔로우·언팔로우는 계정(구글 로그인) 연결된 탐험가만 — 익명은 랭킹 탭에서 로그인 유도. */
    LOGIN_REQUIRED(ErrorKind.UNAUTHENTICATED, "로그인해야 친구를 팔로우할 수 있어요."),
    /** handle 이 없거나, 비교할 수 없는(비공개·친구 아님) 탐험가 — 공유의 공개 프로필과 같은 코드로 존재 여부를 숨긴다. */
    PROFILE_NOT_FOUND(ErrorKind.NOT_FOUND, "프로필을 찾을 수 없어요."),
    CANNOT_FOLLOW_SELF(ErrorKind.RULE_VIOLATION, "자기 자신은 팔로우할 수 없어요."),
    ALREADY_FOLLOWING(ErrorKind.CONFLICT, "이미 팔로우하고 있어요."),
    CANNOT_COMPARE_SELF(ErrorKind.RULE_VIOLATION, "자기 자신과는 비교할 수 없어요.");

    private final ErrorKind kind;
    private final String template;

    SocialError(ErrorKind kind, String template) {
        this.kind = kind;
        this.template = template;
    }

    public TerritoryException exception(Object... args) {
        return new TerritoryException(name(), kind, String.format(template, args));
    }
}
