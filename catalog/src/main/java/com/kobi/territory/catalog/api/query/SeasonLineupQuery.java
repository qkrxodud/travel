package com.kobi.territory.catalog.api.query;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 공개 Query(13s단계): 계절 회차의 지역 목록 — 확정본이 있으면 그것(TourAPI 근거), 없으면 계절 정의의 기본 목록(AI 추정). 회차가 열린 뒤에는
 * 바뀌지 않는다(확정은 회차 시작 전까지만 된다). 진행(progression)의 계절 달력과 {@code GET /seasons/current} 의 근거 표시가 이것을 쓴다.
 */
public interface SeasonLineupQuery {

    /** 회차 id({계절}-{연도})의 지역 목록. 모르는 계절·형식이면 빈 값. */
    Optional<SeasonLineupView> lineupOf(String roundId);

    /**
     * @param provenance  목록 전체 출처 요약 "tourapi" | "ai-estimate" | "mixed"
     * @param source      근거 기관 표기(근거 지역이 있으면 "한국관광공사 TourAPI", 없으면 null)
     * @param confirmedAt 확정 시각(기본 목록이면 null)
     * @param regions     순위 순(확정본) 또는 정의 순서(기본 목록)
     */
    record SeasonLineupView(String roundId, String provenance, String source, Instant confirmedAt, List<LineupRegionView> regions) {
        public SeasonLineupView {
            Objects.requireNonNull(roundId, "roundId");
            regions = List.copyOf(regions);
        }

        /** 지역 코드(KR-xxxxx), 목록 순서. */
        public List<String> regionCodes() {
            return regions.stream().map(LineupRegionView::code).toList();
        }
    }

    /**
     * @param provenance "tourapi" | "ai-estimate"
     * @param evidence   근거(TourAPI 지역만 — 축제 이른 순, 그다음 관광지)
     */
    record LineupRegionView(String code, String provenance, List<EvidenceView> evidence) {
        public LineupRegionView {
            evidence = List.copyOf(evidence);
        }
    }

    /**
     * @param evidenceKind "FESTIVAL"(축제 — 기간 있음) | "ATTRACTION"(관광지 — 기간 null)
     * @param contentId    TourAPI 콘텐츠 id
     * @param fetchedAt    TourAPI 조회 시각
     */
    record EvidenceView(String contentId, String title, LocalDate startDate, LocalDate endDate, Instant fetchedAt, String evidenceKind) {}
}
