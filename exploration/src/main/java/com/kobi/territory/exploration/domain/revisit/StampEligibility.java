package com.kobi.territory.exploration.domain.revisit;

import com.kobi.territory.common.model.RegionCode;
import java.util.List;
import java.util.Optional;

/**
 * 지금 이 지역에 재방문 도장을 받을 수 있는지(판정 결과 — 도장 버튼 안내와 실제 도장이 같은 판정을 쓴다).
 *
 * @param year         지금(처리 시각) 연도
 * @param firstYear    처음 칠한 해(지금 남은 보이는 방문 중 가장 이른 처리 시각의 연도), 칠하지 않았으면 빈 값
 * @param stampedYears 이 지역에서 받은 도장 연도(오름차순)
 * @param refusal      받을 수 없는 이유, 받을 수 있으면 빈 값
 */
public record StampEligibility(RegionCode region, int year, Optional<Integer> firstYear, List<Integer> stampedYears,
                               Optional<StampRefusal> refusal) {
    public StampEligibility {
        stampedYears = List.copyOf(stampedYears);
    }

    public boolean stampable() {
        return refusal.isEmpty();
    }

    /** 처음 칠한 해·이미 받은 도장 때문에 못 받으면 받을 수 있게 되는 해(같은 해 → 처음 칠한 해 + 1, 이미 받음 → 내년). 그 밖은 빈 값. */
    public Optional<Integer> availableFromYear() {
        return refusal.flatMap(reason -> switch (reason) {
            case SAME_YEAR -> firstYear.map(first -> first + 1);
            case ALREADY_STAMPED -> Optional.of(year + 1);
            default -> Optional.empty();
        });
    }
}
