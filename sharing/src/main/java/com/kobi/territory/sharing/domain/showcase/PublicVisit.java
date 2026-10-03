package com.kobi.territory.sharing.domain.showcase;

import java.util.Objects;

/**
 * 공개해도 되는 방문 한 건: 지역 + 월 단위 시기 + 몇 번째 영토인지. 메모·사진·정확한 날짜는 없다(§7).
 *
 * @param nth 칠한 순서(처리 시각 기준 1부터)
 */
public record PublicVisit(RegionInfo region, VisitMonth month, int nth) {
    public PublicVisit {
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(month, "month");
        if (nth < 1) throw new IllegalArgumentException("nth=" + nth);
    }
}
