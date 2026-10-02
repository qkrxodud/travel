package com.kobi.territory.catalog.domain;

import com.kobi.territory.common.model.RegionCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 일급 컬렉션: 지역 참조 데이터. 코드 유일, 현행(폐지 안 된) 지역 조회, 시·도별 현행 지역 수. */
public final class Regions {

    private final List<Region> items;
    private final Map<RegionCode, Region> byCode;

    private Regions(List<Region> items) {
        Map<RegionCode, Region> map = new LinkedHashMap<>();
        for (Region region : items) {
            if (map.put(region.code(), region) != null) throw new IllegalStateException("지역 코드 중복: " + region.code());
        }
        this.items = List.copyOf(items);
        this.byCode = map;
    }

    public static Regions of(List<Region> regions) {
        return new Regions(regions);
    }

    /** 폐지된 지역도 찾는다(과거 방문 표시용). */
    public Optional<Region> find(RegionCode code) {
        return Optional.ofNullable(byCode.get(code));
    }

    /** 현행 지역(retiredAt 없음), 원본 순서. */
    public List<Region> active() {
        return items.stream().filter(Region::active).toList();
    }

    /** 시·도 코드 → 현행 지역 수. */
    public Map<String, Integer> activeCountByProvince() {
        Map<String, Integer> out = new LinkedHashMap<>();
        active().forEach(region -> out.merge(region.provinceCode(), 1, Integer::sum));
        return out;
    }

    public List<Region> all() {
        return items;
    }

    public int size() {
        return items.size();
    }
}
