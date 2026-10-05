package com.kobi.territory.catalog.domain.lineup;

import com.kobi.territory.common.model.RegionCode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 일급 컬렉션: 회차 지역 목록(순위 순). 지역 중복 없음. 목록 전체의 출처 요약({@link #provenance}): 모두 TourAPI 면 "tourapi", 모두 AI 추정이면
 * "ai-estimate", 섞였으면 "mixed".
 */
public final class LineupRegions {

    public static final String MIXED = "mixed";

    private final List<LineupRegion> regions;

    private LineupRegions(List<LineupRegion> regions) {
        Set<RegionCode> seen = new HashSet<>();
        regions.forEach(region -> {
            if (!seen.add(region.code())) throw new IllegalArgumentException("회차 지역 중복: " + region.code());
        });
        if (regions.isEmpty()) throw new IllegalArgumentException("회차 지역이 비었다");
        this.regions = List.copyOf(regions);
    }

    public static LineupRegions of(List<LineupRegion> regions) {
        return new LineupRegions(regions);
    }

    /** 근거 없는 기본 목록(AI 추정). */
    public static LineupRegions aiEstimate(List<RegionCode> codes) {
        return new LineupRegions(codes.stream().map(LineupRegion::aiEstimate).toList());
    }

    /** 지역 코드(순위 순). */
    public List<RegionCode> codes() {
        return regions.stream().map(LineupRegion::code).toList();
    }

    public boolean contains(RegionCode code) {
        return regions.stream().anyMatch(region -> region.code().equals(code));
    }

    public int count(LineupProvenance provenance) {
        return (int) regions.stream().filter(region -> region.provenance() == provenance).count();
    }

    /** 목록 전체의 출처 요약 — "tourapi" | "ai-estimate" | "mixed". */
    public String provenance() {
        int evidenced = count(LineupProvenance.TOURAPI);
        if (evidenced == regions.size()) return LineupProvenance.TOURAPI.code();
        if (evidenced == 0) return LineupProvenance.AI_ESTIMATE.code();
        return MIXED;
    }

    /** 근거가 있는 지역이 하나라도 있는지(출처 표기 필요). */
    public boolean hasEvidence() {
        return count(LineupProvenance.TOURAPI) > 0;
    }

    public int size() {
        return regions.size();
    }

    public Stream<LineupRegion> stream() {
        return regions.stream();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LineupRegions that && regions.equals(that.regions);
    }

    @Override
    public int hashCode() {
        return regions.hashCode();
    }
}
