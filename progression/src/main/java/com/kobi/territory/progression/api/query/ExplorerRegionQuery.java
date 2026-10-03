package com.kobi.territory.progression.api.query;

import java.util.Collection;
import java.util.List;

/**
 * 탐험가 단위 지역(explorer_region) 공개 Query(5단계 — 소셜의 친구 랭킹·영토 비교·상위 % 배치·콜드 스타트). 여러 지도에서 같은 지역을
 * 칠해도 1(§5 전체·친구 랭킹 집계), 활성(지금 방문이 살아 있는) 지역만 센다. 메모·날짜는 싣지 않는다.
 */
public interface ExplorerRegionQuery {

    /** 주어진 탐험가들의 시·도별 활성 지역 수(지역이 없는 탐험가는 결과에 없다 — 0곳). */
    List<ProvinceTallyView> provinceTalliesOf(Collection<String> explorerIds);

    /** 모든 탐험가의 시·도별 활성 지역 수(일 1회 상위 % 배치). */
    List<ProvinceTallyView> provinceTallies();

    /**
     * 지역별 활성 탐험가 수 — 주어진 탐험가(활성 탐험가)만 센다(일 1회 지역별 방문자 비율 배치). 병합돼 비활성인 탐험가의 explorer_region 은
     * 복구용으로 남아 있어 거르지 않으면 같은 사람이 두 번 세진다(QA P2-4).
     */
    List<RegionVisitorsView> regionVisitorsAmong(Collection<String> explorerIds);

    /** 탐험가의 활성 지역 코드(KR-xxxxx, 코드 순) — 영토 비교(VS). */
    List<String> activeRegionCodesOf(String explorerId);
}
