package com.kobi.territory.catalog.domain.lineup;

import java.util.List;
import java.util.Objects;

/**
 * 후보 고르기 결과.
 *
 * @param themedFestivals    기간·테마에 맞은 축제 수(중복 제외)
 * @param unlocatedFestivals 우리 지역을 찾지 못해 뺀 축제·관광지 수
 * @param warnings           운영자에게 보일 경고(부족분 채움·지역 못 찾음·읽다 만 쪽)
 */
public record LineupSelection(LineupRegions regions, int themedFestivals, int unlocatedFestivals, List<String> warnings) {

    public LineupSelection {
        Objects.requireNonNull(regions, "regions");
        warnings = List.copyOf(warnings);
    }

    /** TourAPI 근거가 있는 지역 수. */
    public int evidencedRegions() {
        return regions.count(LineupProvenance.TOURAPI);
    }
}
