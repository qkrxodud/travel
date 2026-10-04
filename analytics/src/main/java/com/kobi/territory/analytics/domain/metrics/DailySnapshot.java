package com.kobi.territory.analytics.domain.metrics;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 하루치 지표(서울 날짜 day 기준). 일 배치가 지난 날을 계산해 저장하고, 오늘은 조회할 때 바로 계산한다.
 *
 * @param newVisitors  그날 처음 본 방문(익명 방문 ID)
 * @param newExplorers 그날 가입한 탐험가
 * @param dau          그날 활동한 사람(탐험가로 이어진 방문은 탐험가 한 명으로)
 * @param wau          day 로 끝나는 7일 동안 활동한 사람
 * @param mau          day 로 끝나는 30일 동안 활동한 사람
 * @param computedAt   계산한 시각
 */
public record DailySnapshot(LocalDate day, int newVisitors, int newExplorers, int dau, int wau, int mau, PageViews pageViews,
                            KFactor kFactor, FeatureUsage featureUsage, ErrorTally errors, Instant computedAt) {

    public DailySnapshot {
        Objects.requireNonNull(day, "day");
        Objects.requireNonNull(pageViews, "pageViews");
        Objects.requireNonNull(kFactor, "kFactor");
        Objects.requireNonNull(featureUsage, "featureUsage");
        Objects.requireNonNull(errors, "errors");
        Objects.requireNonNull(computedAt, "computedAt");
    }
}
