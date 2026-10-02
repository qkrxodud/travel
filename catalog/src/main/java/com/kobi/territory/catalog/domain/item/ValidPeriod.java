package com.kobi.territory.catalog.domain.item;

import com.kobi.territory.catalog.domain.CatalogError;
import java.time.LocalDate;

/**
 * 아이템 지급 유효 기간(양 끝 포함, 서버 시간대의 날짜). 비어 있는 끝은 열려 있다(둘 다 비면 상시).
 * 기간 내 체크인 규칙은 양 끝이 모두 있어야 한다({@link ItemDefinition}이 검증).
 */
public record ValidPeriod(LocalDate from, LocalDate to) {

    public static final ValidPeriod ALWAYS = new ValidPeriod(null, null);

    public ValidPeriod {
        if (from != null && to != null && to.isBefore(from)) {
            throw CatalogError.INVALID_ITEM_DEFINITION.exception("유효 기간의 끝(" + to + ")이 시작(" + from + ")보다 앞입니다.");
        }
    }

    public boolean contains(LocalDate day) {
        return (from == null || !day.isBefore(from)) && (to == null || !day.isAfter(to));
    }

    public boolean bounded() {
        return from != null && to != null;
    }
}
