package com.kobi.territory.catalog.domain.region;

import com.kobi.territory.common.model.RegionCode;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * 일급 컬렉션: 시·군·구 경계 전체(regions.geojson). 좌표 → 지역 판정(점-다각형)을 한다. 경계 자료는 화면 지도용으로 단순화돼 있어 해안·경계선
 * 근처 점이 살짝 밖으로 나올 수 있다 — 그래서 "안에 든 지역"이 없으면 허용 거리 안의 가장 가까운 지역을 따로 묻는다.
 */
public final class RegionBoundaries {

    private final List<RegionBoundary> boundaries;

    private RegionBoundaries(List<RegionBoundary> boundaries) {
        this.boundaries = List.copyOf(boundaries);
    }

    public static RegionBoundaries of(List<RegionBoundary> boundaries) {
        return new RegionBoundaries(boundaries);
    }

    /** 경계 자료 없음(1단계 테스트 등). 좌표로는 아무 지역도 찾지 못한다. */
    public static RegionBoundaries none() {
        return new RegionBoundaries(List.of());
    }

    /** 점을 안에 품은 지역(이 목록에 든 것만 — 폐지 지역을 거르려면 호출자가 넘긴다). */
    public Optional<RegionCode> containing(GeoPoint point, Predicate<RegionCode> eligible) {
        return boundaries.stream().filter(boundary -> eligible.test(boundary.code()) && boundary.contains(point))
            .map(RegionBoundary::code).findFirst();
    }

    /** 허용 거리(km) 안에서 가장 가까운 지역. 같은 거리면 코드가 앞선 지역. */
    public Optional<RegionCode> nearestWithin(GeoPoint point, double toleranceKilometers,
                                              Predicate<RegionCode> eligible) {
        return boundaries.stream().filter(boundary -> eligible.test(boundary.code()))
            .map(boundary -> new Distance(boundary.code(), boundary.kilometersFrom(point)))
            .filter(distance -> distance.kilometers() <= toleranceKilometers)
            .min(Comparator.comparingDouble(Distance::kilometers).thenComparing(distance -> distance.code().value()))
            .map(Distance::code);
    }

    /** 경계마다 그 지역이 카탈로그에 있어야 한다(기동 시 검증). */
    public void requireKnownIn(Regions regions) {
        boundaries.forEach(boundary -> regions.find(boundary.code())
            .orElseThrow(() -> new IllegalStateException("경계 자료의 모르는 지역: " + boundary.code())));
    }

    public int size() {
        return boundaries.size();
    }

    private record Distance(RegionCode code, double kilometers) {}
}
