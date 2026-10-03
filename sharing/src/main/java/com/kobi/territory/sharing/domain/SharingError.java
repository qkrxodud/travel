package com.kobi.territory.sharing.domain;

import com.kobi.territory.common.error.ErrorKind;
import com.kobi.territory.common.error.TerritoryException;

/** 공유 컨텍스트 오류 코드. code 문자열은 API 계약이다. */
public enum SharingError {
    /** 공개 프로필 없음 — handle 이 없거나, 비공개(PRIVATE·FRIENDS)라 존재 여부도 숨긴다. */
    PROFILE_NOT_FOUND(ErrorKind.NOT_FOUND, "프로필을 찾을 수 없어요."),
    CARD_KIND_NOT_FOUND(ErrorKind.NOT_FOUND, "모르는 카드 종류예요: %s"),
    INVALID_VISIBILITY(ErrorKind.INVALID, "공개 범위는 PUBLIC·FRIENDS·PRIVATE 중 하나예요: %s"),
    /** 리캡 연도가 달력 범위(1~9999) 밖. */
    INVALID_YEAR(ErrorKind.INVALID, "리캡 연도가 올바르지 않아요: %s");

    private final ErrorKind kind;
    private final String template;

    SharingError(ErrorKind kind, String template) {
        this.kind = kind;
        this.template = template;
    }

    public TerritoryException exception(Object... args) {
        return new TerritoryException(name(), kind, String.format(template, args));
    }
}
