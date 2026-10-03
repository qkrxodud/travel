package com.kobi.territory.sharing.domain.showcase;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 공개 정보를 만드는 데 쓰는 방문 사실(탐험 Query 의 방문 이력에서 온 값). 메모·사진은 처음부터 없다(탐험이 싣지 않는다).
 *
 * @param visitDate 사용자가 적은 방문일 — 공개할 때는 {@link VisitMonth} 로 둥글린다
 * @param visitedAt 처리 시각(칠한 순서)
 */
public record VisitFact(String regionCode, LocalDate visitDate, Instant visitedAt) {
    public VisitFact {
        Objects.requireNonNull(regionCode, "regionCode");
        Objects.requireNonNull(visitDate, "visitDate");
        Objects.requireNonNull(visitedAt, "visitedAt");
    }
}
