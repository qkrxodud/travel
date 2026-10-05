package com.kobi.territory.catalog.domain.lineup;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 회차 지역 하나의 근거 한 건 — 그 지역의 계절 축제(행사) 또는 계절 관광지(실제 세계 데이터의 출처 규칙: 기관·근거 항목 이름·조회 시각).
 *
 * @param kind      근거 종류 — 축제(기간 있음) | 관광지(기간 없음)
 * @param contentId 한국관광공사 TourAPI 콘텐츠 id
 * @param startDate 축제 첫날(관광지는 null)
 * @param endDate   축제 마지막 날(관광지는 null)
 * @param fetchedAt TourAPI 에서 읽은 시각
 */
public record LineupEvidence(EvidenceKind kind, String contentId, String title, LocalDate startDate, LocalDate endDate,
                             Instant fetchedAt) {

    public LineupEvidence {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(contentId, "contentId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(fetchedAt, "fetchedAt");
        if (kind == EvidenceKind.FESTIVAL && (startDate == null || endDate == null)) {
            throw new IllegalArgumentException("축제 근거는 기간이 있어야 한다: " + contentId);
        }
    }

    /** 근거 종류. 이름은 API 계약 값(evidenceKind)이다. */
    public enum EvidenceKind { FESTIVAL, ATTRACTION }
}
