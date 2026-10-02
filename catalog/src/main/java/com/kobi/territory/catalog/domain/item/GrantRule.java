package com.kobi.territory.catalog.domain.item;

import com.kobi.territory.catalog.domain.CatalogError;
import com.kobi.territory.common.model.RegionCode;
import java.util.Objects;

/**
 * 아이템 지급 규칙(리스크 #9 — 규칙 엔진은 만들지 않는다: 지역 방문(기본) + 이슈 아이템 3종 + 수동).
 * <ul>
 *   <li>{@link RegionVisit} 그 지역 체크인(지역 특산물, 취소 시 회수 — 회수 판단은 꾸미기 컨텍스트)</li>
 *   <li>{@link PeriodCheckIn} 유효 기간 안의 체크인 아무 곳(이슈 아이템, 회수 없음)</li>
 *   <li>{@link ProvinceCheckIn} 특정 시·도 체크인(이슈 아이템, 회수 없음)</li>
 *   <li>{@link ThemeComplete} 도감 테마(세트) 완성 — 세트 배경 등, 완성 시점 지도 멤버 전원</li>
 *   <li>{@link Manual} 자동 지급하지 않는다(운영 수동 지급용 예약)</li>
 * </ul>
 * 저장·공개 표현은 (type, ref) 두 값이다.
 */
public sealed interface GrantRule {

    Type type();

    /** 규칙 대상(지역 코드·시·도 코드·테마 id). 없으면 null. */
    String ref();

    /** 체크인 한 번이 이 규칙을 만족하는지(유효 기간은 {@link ItemDefinition}이 따로 본다). */
    default boolean matchesCheckIn(RegionCode region, String provinceCode) {
        return false;
    }

    default boolean matchesThemeCompletion(String themeId) {
        return false;
    }

    /** 체크인으로 받는 이슈 아이템 규칙(기간 내 체크인·특정 시·도)인지 — 정의가 생긴 뒤의 체크인에만 지급한다. */
    default boolean issue() {
        return false;
    }

    enum Type { REGION_VISIT, PERIOD_CHECK_IN, PROVINCE_CHECK_IN, THEME_COMPLETE, MANUAL }

    /** 저장·입력 값(type, ref)으로 만든다. ref 형식이 틀리면 INVALID_ITEM_DEFINITION. */
    static GrantRule of(Type type, String ref) {
        Objects.requireNonNull(type, "grantRule");
        return switch (type) {
            case REGION_VISIT -> new RegionVisit(RegionCode.of(ref));
            case PERIOD_CHECK_IN -> new PeriodCheckIn();
            case PROVINCE_CHECK_IN -> new ProvinceCheckIn(ref);
            case THEME_COMPLETE -> new ThemeComplete(ref);
            case MANUAL -> new Manual();
        };
    }

    record RegionVisit(RegionCode region) implements GrantRule {
        public RegionVisit {
            Objects.requireNonNull(region, "region");
        }

        @Override public Type type() { return Type.REGION_VISIT; }
        @Override public String ref() { return region.value(); }

        @Override
        public boolean matchesCheckIn(RegionCode visited, String provinceCode) {
            return region.equals(visited);
        }
    }

    record PeriodCheckIn() implements GrantRule {
        @Override public Type type() { return Type.PERIOD_CHECK_IN; }
        @Override public boolean issue() { return true; }
        @Override public String ref() { return null; }

        @Override
        public boolean matchesCheckIn(RegionCode visited, String provinceCode) {
            return true;
        }
    }

    record ProvinceCheckIn(String provinceCode) implements GrantRule {
        public ProvinceCheckIn {
            if (provinceCode == null || !provinceCode.matches("^[A-Z]{2}-\\d{2}$")) {
                throw CatalogError.INVALID_ITEM_DEFINITION.exception("시·도 코드 형식이 올바르지 않습니다(예: KR-11): " + provinceCode);
            }
        }

        @Override public Type type() { return Type.PROVINCE_CHECK_IN; }
        @Override public boolean issue() { return true; }
        @Override public String ref() { return provinceCode; }

        @Override
        public boolean matchesCheckIn(RegionCode visited, String visitedProvince) {
            return provinceCode.equals(visitedProvince);
        }
    }

    record ThemeComplete(String themeId) implements GrantRule {
        public ThemeComplete {
            if (themeId == null || themeId.isBlank()) {
                throw CatalogError.INVALID_ITEM_DEFINITION.exception("세트 완성 규칙에는 세트 id 가 필요합니다.");
            }
        }

        @Override public Type type() { return Type.THEME_COMPLETE; }
        @Override public String ref() { return themeId; }

        @Override
        public boolean matchesThemeCompletion(String completedThemeId) {
            return themeId.equals(completedThemeId);
        }
    }

    record Manual() implements GrantRule {
        @Override public Type type() { return Type.MANUAL; }
        @Override public String ref() { return null; }
    }
}
