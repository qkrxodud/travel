package com.kobi.territory.catalog.domain.definition;

import java.util.List;
import java.util.Objects;

/**
 * 뱃지 획득 조건(badges.json의 condition). 판정은 진행 도메인이 한다 — 카탈로그는 선언만 보관한다.
 *
 * @param min       REGION_COUNT·LEGEND_COUNT·SETS_COMPLETED·STREAK_MONTHS·MYSTERY_FOUND(이번 주 미스터리 보너스를 받은 주 수, 8단계)·
 *                  REVISIT_STAMPS(재방문 도장 수)·WISHES_FULFILLED(다녀온 가고 싶은 곳 수, 9단계) 의 하한
 * @param provinces PROVINCES_COMPLETE 대상 시·도 코드
 * @param groups    PROVINCE_GROUPS_TOUCHED: 그룹마다 1곳 이상
 * @param ratio     CONQUEST_RATIO 하한(0~1)
 */
public record BadgeCondition(Type type, int min, List<String> provinces, List<List<String>> groups, double ratio) {

    public enum Type {
        REGION_COUNT, PROVINCES_COMPLETE, PROVINCE_GROUPS_TOUCHED, LEGEND_COUNT, ALL_PROVINCES_TOUCHED,
        SETS_COMPLETED, STREAK_MONTHS, CONQUEST_RATIO, MYSTERY_FOUND, REVISIT_STAMPS, WISHES_FULFILLED
    }

    public BadgeCondition {
        Objects.requireNonNull(type, "type");
        provinces = provinces == null ? List.of() : List.copyOf(provinces);
        groups = groups == null ? List.of() : groups.stream().map(List::copyOf).toList();
        switch (type) {
            case PROVINCES_COMPLETE -> require(!provinces.isEmpty(), "provinces");
            case PROVINCE_GROUPS_TOUCHED -> require(!groups.isEmpty(), "groups");
            case CONQUEST_RATIO -> require(ratio > 0 && ratio <= 1, "ratio");
            case ALL_PROVINCES_TOUCHED -> { }
            default -> require(min >= 1, "min");
        }
    }

    private static void require(boolean ok, String field) {
        if (!ok) throw new IllegalStateException("뱃지 조건 값 오류: " + field);
    }

    /** 조건이 참조하는 시·도 코드 전부(정합성 검증용). */
    public List<String> referencedProvinces() {
        return java.util.stream.Stream.concat(provinces.stream(), groups.stream().flatMap(List::stream)).toList();
    }
}
