package com.kobi.territory.analytics.domain.metrics;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** 코호트 하루치(그날 처음 본 방문의 퍼널 + 그날 가입한 탐험가의 리텐션). 확정될 때까지 일 배치가 다시 계산한다. */
public record CohortSnapshot(LocalDate cohortDay, FunnelCounts funnel, RetentionCounts retention, Instant computedAt) {

    public CohortSnapshot {
        Objects.requireNonNull(cohortDay, "cohortDay");
        Objects.requireNonNull(funnel, "funnel");
        Objects.requireNonNull(retention, "retention");
        Objects.requireNonNull(computedAt, "computedAt");
    }
}
