package com.kobi.territory.catalog.domain.region;

import com.kobi.territory.common.model.RegionCode;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 도메인 서비스: 바깥 자료의 위치(좌표·주소)를 우리 시·군·구(현행 지역)로 옮긴다. 바깥 기관의 지역 코드표에 기대지 않는다 — 우리 경계 자료가
 * 기준이다(13s단계 TourAPI 축제 → 계절 회차 후보).
 * <ol>
 *   <li>좌표가 있으면 그 점을 품은 지역(주소와 어긋나도 좌표를 따른다 — 예: 실제 "단풍산"은 주소 영월군, 좌표는 정선군 안(영월 경계에서 20km))</li>
 *   <li>없으면(단순화된 경계라 해안·경계선 근처 점이 살짝 밖일 수 있다) 허용 거리 안의 가장 가까운 지역</li>
 *   <li>그래도 없거나 좌표가 없으면 주소("경기도 수원시 장안구 …") — 첫 낱말로 시·도를 찾고, 그 시·도 안에서 다음 낱말(또는 이어 붙인 두 낱말 —
 *       "수원시"+"장안구")과 이름이 같은 지역. 시·도에 지역이 하나뿐이면(세종) 그 지역</li>
 * </ol>
 */
public final class RegionLocator {

    private static final int ADDRESS_WORDS_TO_TRY = 3;

    private final Regions regions;
    private final Provinces provinces;
    private final RegionBoundaries boundaries;
    private final double toleranceKilometers;

    public RegionLocator(Regions regions, Provinces provinces, RegionBoundaries boundaries, double toleranceKilometers) {
        this.regions = Objects.requireNonNull(regions, "regions");
        this.provinces = Objects.requireNonNull(provinces, "provinces");
        this.boundaries = Objects.requireNonNull(boundaries, "boundaries");
        if (toleranceKilometers < 0) throw new IllegalArgumentException("허용 거리는 0 이상");
        this.toleranceKilometers = toleranceKilometers;
    }

    /** @param point 좌표(없으면 null), @param address 주소(없으면 null) */
    public Optional<RegionMatch> locate(GeoPoint point, String address) {
        Optional<RegionCode> byAddress = byAddress(address);
        if (point != null) {
            Optional<RegionCode> inside = boundaries.containing(point, regions::isActive);
            if (inside.isPresent()) return inside.map(code -> new RegionMatch(code, RegionMatch.Method.INSIDE_BOUNDARY));
            Optional<RegionCode> near = boundaries.nearestWithin(point, toleranceKilometers, regions::isActive);
            if (near.isPresent()) return near.map(code -> new RegionMatch(code, RegionMatch.Method.NEAR_BOUNDARY));
        }
        return byAddress.map(code -> new RegionMatch(code, RegionMatch.Method.ADDRESS));
    }

    private Optional<RegionCode> byAddress(String address) {
        if (address == null || address.isBlank()) return Optional.empty();
        String[] words = address.trim().split("\\s+");
        Optional<Province> province = provinces.inDisplayOrder().stream().filter(candidate -> candidate.isNamedBy(words[0])).findFirst();
        if (province.isEmpty()) return Optional.empty();
        List<Region> candidates = regions.activeIn(province.get().code());
        if (candidates.size() == 1) return Optional.of(candidates.getFirst().code());
        for (int i = 1; i < words.length && i <= ADDRESS_WORDS_TO_TRY; i++) {
            String word = words[i];
            String joined = i + 1 < words.length ? word + words[i + 1] : null;
            Optional<Region> found = named(candidates, joined).or(() -> named(candidates, word));
            if (found.isPresent()) return found.map(Region::code);
        }
        return Optional.empty();
    }

    private static Optional<Region> named(List<Region> candidates, String name) {
        if (name == null) return Optional.empty();
        return candidates.stream().filter(region -> region.name().equals(name)).findFirst();
    }
}
