package com.kobi.territory.progression.domain.progress;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import java.util.Collection;
import java.util.List;

/**
 * 탐험가 단위 지역(explorer_region)의 읽기 전용 집계 포트(5단계 — 소셜의 친구 랭킹·VS·상위 % 배치). 애그리거트를 통째로 불러오지 않고
 * 저장소가 집계만 돌려준다. 모두 "활성"(active_map_count &gt; 0) 지역만 센다 — 탈퇴로 남은 지도 활성은 그대로 세고(§5 탈퇴는 줄이지 않음),
 * 취소로 활성이 끝난 지역은 빠진다.
 */
public interface ExploredRegionStatistics {

    /** 주어진 탐험가들의 시·도별 활성 지역 수(행이 없는 탐험가는 결과에 없다). */
    List<ProvinceTally> provinceTallies(Collection<ExplorerId> explorerIds);

    /** 모든 탐험가의 시·도별 활성 지역 수(상위 % 배치). */
    List<ProvinceTally> allProvinceTallies();

    /** 지역별 활성 탐험가 수 — explorerIds 중에서만 센다(지역별 방문자 비율 배치, 병합 비활성 탐험가 제외 — QA P2-4). */
    List<RegionVisitorTally> regionVisitors(Collection<ExplorerId> explorerIds);

    /** 탐험가의 활성 지역 코드(VS 비교, 코드 순). */
    List<RegionCode> activeRegionCodes(ExplorerId explorerId);
}
