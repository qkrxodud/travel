package com.kobi.territory.social.domain.stats;

import com.kobi.territory.common.model.ExplorerId;
import java.time.Instant;
import java.util.Objects;

/**
 * 전체 유저 중 상위 %(일 1회 배치, rank_percentile). 전체 랭킹은 순위표를 만들지 않고 이 값만 보여 준다(§7 — 참고용).
 *
 * @param rank       지역 수 기준 경쟁 순위(1부터, 같은 지역 수면 같은 순위)
 * @param population 배치 시점 모집단(활성 지역 1곳 이상인 활성 탐험가 수 — 리더 결정 5)
 * @param topPercent 상위 몇 %인지(올림, 1~100)
 */
public record RankPercentile(ExplorerId explorerId, int regionCount, int rank, int population, int topPercent, Instant computedAt) {
    public RankPercentile {
        Objects.requireNonNull(explorerId, "explorerId");
        Objects.requireNonNull(computedAt, "computedAt");
        if (rank < 1 || population < rank || topPercent < 1 || topPercent > 100) {
            throw new IllegalArgumentException("rank=" + rank + " population=" + population + " top=" + topPercent);
        }
    }

    /**
     * 상위 % = ⌈100 × 순위 / 전체⌉(최소 1). 예: 100명 중 1등 1%, 3명 중 2등 67%, 혼자면 100%.
     */
    public static int topPercent(int rank, int population) {
        if (rank < 1 || population < rank) throw new IllegalArgumentException("rank=" + rank + " population=" + population);
        return Math.max(1, (int) Math.ceil(100.0 * rank / population));
    }
}
