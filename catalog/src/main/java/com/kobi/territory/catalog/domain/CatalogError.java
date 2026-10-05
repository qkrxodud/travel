package com.kobi.territory.catalog.domain;

import com.kobi.territory.common.error.ErrorKind;
import com.kobi.territory.common.error.TerritoryException;

/** 카탈로그 오류 코드 — 아이템 정의(운영 폼 POST /admin/items), 계절 회차 지역 목록(13s단계 /admin/seasons/**). code 문자열은 API 계약이다. */
public enum CatalogError {
    INVALID_ITEM_DEFINITION(ErrorKind.INVALID, "%s"),
    UNKNOWN_ITEM_REFERENCE(ErrorKind.INVALID, "%s"),
    ITEM_ALREADY_EXISTS(ErrorKind.CONFLICT, "이미 있는 아이템 id 입니다: %s"),
    SEASON_ROUND_NOT_FOUND(ErrorKind.NOT_FOUND, "모르는 계절 회차입니다: %s"),
    SEASON_ROUND_LOCKED(ErrorKind.CONFLICT, "이미 열린(또는 지난) 회차라 지역 목록이 고정됐습니다 — 갱신은 다음 회차부터: %s"),
    SEASON_CANDIDATE_MISSING(ErrorKind.CONFLICT, "확정할 후보가 없습니다 — 먼저 갱신(refresh)하세요: %s"),
    SEASON_LINEUP_BUSY(ErrorKind.CONFLICT, "같은 회차를 다른 요청이 고치는 중입니다 — 잠시 뒤 다시 시도하세요: %s");

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
