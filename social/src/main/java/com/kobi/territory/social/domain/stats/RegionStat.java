package com.kobi.territory.social.domain.stats;

import java.time.Instant;
import java.util.Objects;

/** 지역별 방문자 비율(일 1회 배치, region_stats): 그 지역을 활성으로 가진 탐험가 수 / 모집단(활성 지역 1곳 이상인 활성 탐험가 수). */
public record RegionStat(String regionCode, int visitorCount, int population, Instant computedAt) {
    public RegionStat {
        Objects.requireNonNull(regionCode, "regionCode");
        Objects.requireNonNull(computedAt, "computedAt");
        if (visitorCount < 0 || population < visitorCount) {
            // 방문자는 모집단의 일부여야 한다 — 넘친 원천은 RankSnapshot 이 RegionOverflow 로 드러내고 맞춘 뒤 만든다(QA r2 P3-B)
            throw new IllegalArgumentException("visitor=" + visitorCount + " population=" + population);
        }
    }

    /** 방문자 비율(%) — 소수 첫째 자리 반올림, 전체가 0이면 0. */
    public double visitorPercent() {
        return population == 0 ? 0 : Math.round(1000.0 * visitorCount / population) / 10.0;
    }
}
