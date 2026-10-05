package com.kobi.territory.catalog.domain.lineup;

import com.kobi.territory.catalog.domain.region.GeoPoint;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * 바깥 기관(한국관광공사 TourAPI 행사정보)이 알려 준 축제 하나. 계절 회차 후보를 고르는 근거다.
 *
 * @param contentId 기관의 콘텐츠 id(근거로 남긴다)
 * @param endDate   마지막 날(포함). 없으면 첫날과 같다
 * @param location  좌표(없으면 null)
 * @param address   주소(없으면 null) — 좌표로 지역을 찾지 못할 때 쓴다
 */
public record Festival(String contentId, String title, LocalDate startDate, LocalDate endDate, GeoPoint location, String address) {

    public Festival {
        Objects.requireNonNull(contentId, "contentId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(startDate, "startDate");
        if (contentId.isBlank()) throw new IllegalArgumentException("축제 콘텐츠 id 가 비었다");
        endDate = endDate == null || endDate.isBefore(startDate) ? startDate : endDate;
        address = address == null || address.isBlank() ? null : address.trim();
    }

    /** 여는 날수(양 끝 포함) — 같은 수의 축제가 열리는 지역끼리 순위를 가를 때 "규모"로 쓴다. */
    public long days() {
        return ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    /** 근거 기록(조회 시각 포함). */
    public LineupEvidence evidence(Instant fetchedAt) {
        return new LineupEvidence(LineupEvidence.EvidenceKind.FESTIVAL, contentId, title, startDate, endDate, fetchedAt);
    }
}
