package com.kobi.territory.catalog.domain.lineup;

/**
 * 회차 후보 고르기 정책(설정값 territory.tourapi.lineup.*).
 *
 * @param size              회차 지역 수(10)
 * @param marginDays        축제를 찾는 날짜 범위의 앞뒤 여유 일수(회차 첫날 − 여유 ~ 마지막 날 + 여유)
 * @param evidencePerRegion 지역마다 남기는 근거 축제 수 상한
 */
public record LineupPolicy(int size, int marginDays, int evidencePerRegion) {

    public LineupPolicy {
        if (size < 1) throw new IllegalArgumentException("회차 지역 수는 1 이상");
        if (marginDays < 0) throw new IllegalArgumentException("여유 일수는 0 이상");
        if (evidencePerRegion < 1) throw new IllegalArgumentException("근거 축제 수 상한은 1 이상");
    }
}
