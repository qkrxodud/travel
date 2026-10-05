package com.kobi.territory.progression.domain.collectionbook;

import com.kobi.territory.common.model.RegionCode;
import java.util.Optional;
import java.util.Set;

/**
 * 포트(정책): 계절 회차의 확정 지역 목록(13s단계 — 카탈로그 공개 Query 를 application 이 이어 붙인다). 비어 있으면 계절 정의의 기본 목록을 쓴다.
 * 확정은 회차가 열리기 전에만 되므로 열린 회차·닫힌 회차의 목록은 바뀌지 않는다 — 진행 중 회차의 진행도·재계산이 흔들리지 않는다.
 */
@FunctionalInterface
public interface RoundLineups {

    /** 회차 id({계절}-{연도})의 지역 목록. 없으면 빈 값(기본 목록). */
    Optional<Set<RegionCode>> regionsOf(String roundId);

    /** 회차별 목록 없음 — 모든 회차가 계절 정의의 기본 목록(13s단계 이전 범위·테스트). */
    static RoundLineups defaults() {
        return roundId -> Optional.empty();
    }
}
