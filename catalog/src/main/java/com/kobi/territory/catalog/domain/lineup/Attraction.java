package com.kobi.territory.catalog.domain.lineup;

import com.kobi.territory.catalog.domain.region.GeoPoint;
import java.time.Instant;
import java.util.Objects;

/**
 * 바깥 기관(한국관광공사 TourAPI 키워드 검색, 관광지)이 알려 준 계절 관광지 하나 — 축제가 없는 계절(가을 단풍)의 근거. 기간은 없다.
 *
 * @param location 좌표(없으면 null)
 * @param address  주소(없으면 null) — 좌표로 지역을 찾지 못할 때 쓴다
 */
public record Attraction(String contentId, String title, GeoPoint location, String address) {

    public Attraction {
        Objects.requireNonNull(contentId, "contentId");
        Objects.requireNonNull(title, "title");
        if (contentId.isBlank()) throw new IllegalArgumentException("관광지 콘텐츠 id 가 비었다");
        address = address == null || address.isBlank() ? null : address.trim();
    }

    public LineupEvidence evidence(Instant fetchedAt) {
        return new LineupEvidence(LineupEvidence.EvidenceKind.ATTRACTION, contentId, title, null, null, fetchedAt);
    }
}
