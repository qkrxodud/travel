package com.kobi.territory.sharing.application;

import com.kobi.territory.sharing.domain.showcase.YearRecap;
import java.util.Objects;

/**
 * 내 연간 리캡(GET /me/recap) — 고른 지도에서 내가 칠한 곳 기준.
 *
 * @param mapId         기준 지도(요청에서 생략하면 개인 지도)
 * @param recap         카드 PNG 와 같은 계산({@code PublicVisits.recap})
 * @param setsCompleted 그 지도 도감에서 완성한 세트 수(연도 무관 누적 — GET /collection 의 완성 수와 같다)
 */
public record MyRecap(String mapId, YearRecap recap, int setsCompleted) {
    public MyRecap {
        Objects.requireNonNull(mapId, "mapId");
        Objects.requireNonNull(recap, "recap");
    }
}
