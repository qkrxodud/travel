package com.kobi.territory.catalog.domain.lineup;

import com.kobi.territory.common.model.RegionCode;
import java.util.List;
import java.util.Objects;

/**
 * 회차 지역 하나와 그 출처·근거. TourAPI 지역은 근거 축제가 하나 이상, AI 추정 지역은 근거가 없다.
 *
 * @param evidence 근거(축제 이른 순 → 관광지 이름 순)
 */
public record LineupRegion(RegionCode code, LineupProvenance provenance, List<LineupEvidence> evidence) {

    public LineupRegion {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(provenance, "provenance");
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        if (provenance == LineupProvenance.TOURAPI && evidence.isEmpty()) {
            throw new IllegalArgumentException("TourAPI 출처 지역은 근거 축제가 있어야 한다: " + code);
        }
        if (provenance == LineupProvenance.AI_ESTIMATE && !evidence.isEmpty()) {
            throw new IllegalArgumentException("AI 추정 지역에는 근거 축제가 없다: " + code);
        }
    }

    public static LineupRegion aiEstimate(RegionCode code) {
        return new LineupRegion(code, LineupProvenance.AI_ESTIMATE, List.of());
    }
}
