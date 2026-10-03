package com.kobi.territory.sharing.domain.showcase;

import com.kobi.territory.common.model.Rarity;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 일급 컬렉션: 지역·시·도 정의(정복률·시·도 정복·전설 수의 분모). */
public final class RegionAtlas {

    private final Map<String, RegionInfo> regionsByCode = new LinkedHashMap<>();
    private final List<ProvinceInfo> provinces;

    private RegionAtlas(Collection<RegionInfo> regions, List<ProvinceInfo> provinces) {
        regions.forEach(region -> regionsByCode.put(region.code(), region));
        this.provinces = List.copyOf(provinces);
    }

    public static RegionAtlas of(Collection<RegionInfo> regions, List<ProvinceInfo> provinces) {
        return new RegionAtlas(regions, provinces);
    }

    public Optional<RegionInfo> region(String code) {
        return Optional.ofNullable(regionsByCode.get(code));
    }

    public int regionCount() {
        return regionsByCode.size();
    }

    public int provinceCount() {
        return provinces.size();
    }

    public int legendCount() {
        return (int) regionsByCode.values().stream().filter(region -> region.rarity() == Rarity.LEGEND).count();
    }

    /** 시·도 표시 순서. */
    public List<ProvinceInfo> provincesInOrder() {
        return provinces;
    }
}
