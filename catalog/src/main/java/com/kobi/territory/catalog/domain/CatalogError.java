package com.kobi.territory.catalog.domain;

import com.kobi.territory.common.error.ErrorKind;
import com.kobi.territory.common.error.TerritoryException;

/** 아이템 정의 오류 코드(운영 폼 POST /admin/items 응답). code 문자열은 API 계약이다. */
public enum CatalogError {
    INVALID_ITEM_DEFINITION(ErrorKind.INVALID, "%s"),
    UNKNOWN_ITEM_REFERENCE(ErrorKind.INVALID, "%s"),
    ITEM_ALREADY_EXISTS(ErrorKind.CONFLICT, "이미 있는 아이템 id 입니다: %s");

    private final ErrorKind kind;
    private final String template;

    CatalogError(ErrorKind kind, String template) {
        this.kind = kind;
        this.template = template;
    }

    public TerritoryException exception(Object... args) {
        return new TerritoryException(name(), kind, String.format(template, args));
    }
}
