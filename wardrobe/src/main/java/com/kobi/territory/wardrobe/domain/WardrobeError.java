package com.kobi.territory.wardrobe.domain;

import com.kobi.territory.common.error.ErrorKind;
import com.kobi.territory.common.error.TerritoryException;

/** 꾸미기 컨텍스트 오류 코드. code 문자열은 API 계약이다. */
public enum WardrobeError {
    ITEM_NOT_FOUND(ErrorKind.NOT_FOUND, "모르는 아이템입니다: %s"),
    ITEM_NOT_OWNED(ErrorKind.RULE_VIOLATION, "가방에 없는 아이템이에요: %s"),
    SLOT_MISMATCH(ErrorKind.RULE_VIOLATION, "%s 은(는) %s 슬롯 아이템이 아니에요."),
    TOO_MANY_PROPS(ErrorKind.RULE_VIOLATION, "장식은 %d개까지 둘 수 있어요."),
    DUPLICATE_PROP(ErrorKind.INVALID, "같은 장식을 두 번 둘 수 없어요: %s");

    private final ErrorKind kind;
    private final String template;

    WardrobeError(ErrorKind kind, String template) {
        this.kind = kind;
        this.template = template;
    }

    public TerritoryException exception(Object... args) {
        return new TerritoryException(name(), kind, String.format(template, args));
    }
}
