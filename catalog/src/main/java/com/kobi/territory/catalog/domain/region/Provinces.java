package com.kobi.territory.catalog.domain.region;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 일급 컬렉션: 시·도. 표시 순서 정렬, 지역 데이터와의 정합성(소속·지역 수) 검증. */
public final class Provinces {

    private final List<Province> inOrder;
    private final Map<String, Province> byCode;

    private Provinces(List<Province> items) {
        this.inOrder = items.stream().sorted(Comparator.comparingInt(Province::displayOrder)).toList();
        Map<String, Province> map = new LinkedHashMap<>();
        for (Province province : inOrder) {
            if (map.put(province.code(), province) != null) throw new IllegalStateException("시·도 코드 중복: " + province.code());
        }
        this.byCode = map;
    }

    public static Provinces of(List<Province> provinces) {
        return new Provinces(provinces);
    }

    public Optional<Province> find(String code) {
        return Optional.ofNullable(byCode.get(code));
    }

    public Province require(String code) {
        return find(code).orElseThrow(() -> new IllegalStateException("모르는 시·도: " + code));
    }

    public List<Province> inDisplayOrder() {
        return inOrder;
    }

    /** 모든 지역이 알려진 시·도에 속하고, 시·도별 regionCount 가 현행 지역 수와 같아야 한다. */
    public void requireConsistentWith(Regions regions) {
        regions.all().forEach(region -> require(region.provinceCode()));
        Map<String, Integer> counts = regions.activeCountByProvince();
        for (Province province : inOrder) {
            if (counts.getOrDefault(province.code(), 0) != province.regionCount()) {
                throw new IllegalStateException("시·도 지역 수 불일치: " + province.code());
            }
        }
    }
}
