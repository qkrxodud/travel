package com.kobi.territory.analytics.domain.metrics;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 집계 결과 저장 포트(원본을 지워도 남는다). 같은 날을 다시 계산하면 통째로 바꾼다. */
public interface MetricsStore {

    void replaceDaily(DailySnapshot snapshot);

    void replaceCohort(CohortSnapshot snapshot);

    /** 구간 안 저장된 하루 지표(날짜 순). */
    List<DailySnapshot> daily(DayRange range);

    /** 구간 안 저장된 코호트(날짜 순). */
    List<CohortSnapshot> cohorts(DayRange range);

    /** 구간 안에 하루 지표가 저장된 날들. */
    Set<LocalDate> computedDays(DayRange range);

    /** 구간 안에 코호트가 저장된 날들. */
    Set<LocalDate> computedCohortDays(DayRange range);

    /** 마지막으로 하루 지표를 계산한 시각. */
    Optional<Instant> lastComputedAt();
}
