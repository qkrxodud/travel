package com.kobi.territory.analytics.domain.metrics;

/**
 * 첫 방문 퍼널(코호트 = 그날 처음 본 방문):
 * <ol>
 *   <li>첫 화면 — 그날 처음 본 방문 수</li>
 *   <li>첫 체크인 — 그중 이어진 탐험가가 첫 화면 날부터 정해진 일수(기본 7) 안에 첫 체크인을 한 수</li>
 *   <li>7일 내 재방문 — 그중 첫 체크인 다음 날부터 정해진 일수(기본 7) 안에 다시 활동한 수</li>
 * </ol>
 *
 * @param settled 두 구간이 모두 지나 더는 바뀌지 않는지(아니면 아직 채워지는 중)
 */
public record FunnelCounts(int firstScreen, int firstCheckIn, int revisited, boolean settled) {

    public FunnelCounts {
        if (firstCheckIn > firstScreen || revisited > firstCheckIn || firstScreen < 0 || revisited < 0) {
            throw new IllegalArgumentException("퍼널은 단계마다 줄어든다: " + firstScreen + " → " + firstCheckIn + " → " + revisited);
        }
    }

    /** 첫 화면 → 첫 체크인 전환율. */
    public Double checkInRate() {
        return Ratio.of(firstCheckIn, firstScreen);
    }

    /** 첫 체크인 → 재방문 전환율. */
    public Double revisitRate() {
        return Ratio.of(revisited, firstCheckIn);
    }

    /** 첫 화면 → 재방문 전체 전환율. */
    public Double overallRate() {
        return Ratio.of(revisited, firstScreen);
    }
}
