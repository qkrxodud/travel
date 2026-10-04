package com.kobi.territory.wardrobe.domain.inventory;

import com.kobi.territory.wardrobe.domain.item.ItemSpec;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 인벤토리 재계산(일관성 원칙 3) 도메인 서비스 — 결과가 이벤트 누적과 같도록 다시 만든다.
 * <ol>
 *   <li>출발점: 재생할 지도(지금 멤버인 지도)의 흔적·근거만 비운다. 탈퇴한 지도의 근거·보상 아이템은 유지.</li>
 *   <li>재생: 그 지도들의 본인 방문을 처리 시각 순으로 체크인(세대 포함) — 지역·이슈 아이템과 근거·흔적이 다시 생긴다.</li>
 *   <li>보상: 지금 멤버인 지도에서 완성된 테마의 보상(세트 배경) — 완성 시점 멤버든 나중 합류 멤버든(결정 1·P3-1 안전망).</li>
 *   <li>업적 보상(8단계): 진행 기록에 남은 시·도 정복·연속 탐험 마일스톤의 한정 아이템 — 회수 없는 보상이라 빠졌으면 채운다.</li>
 *   <li>계절 회차 배경·재방문 색 변형(9단계): 완성 시점 수령자였던 회차의 배경과 도장 지역 표시 — 회수 없는 보상이라 빠졌으면 채운다.</li>
 *   <li>예전에도 있던 아이템은 처음 얻은 시각·즐겨찾기를 유지한다.</li>
 * </ol>
 */
public final class InventoryReplay {

    private InventoryReplay() {}

    /**
     * @param visits       재생할 지도들의 방문 이력(다른 멤버 것도 섞여 있다 — 본인 것만 처리 시각 순으로 재생한다.
     *                     지급 아이템은 그 방문의 처리 날짜 기준으로 카탈로그가 판정해 둔 것)
     * @param themeRewards 지금 멤버인 지도의 완성 테마 보상
     */
    public static Inventory replay(Inventory current, Set<String> replayableMaps, List<ReplayVisit> visits,
                                   List<ItemSpec> themeRewards, Instant at) {
        return replay(current, replayableMaps, visits, themeRewards, List.of(), at);
    }

    /** @param achievementRewards 진행 기록의 시·도 정복·연속 탐험 마일스톤 보상(8단계, 받은 시각으로 판정해 둔 것) */
    public static Inventory replay(Inventory current, Set<String> replayableMaps, List<ReplayVisit> visits,
                                   List<ItemSpec> themeRewards, List<ItemSpec> achievementRewards, Instant at) {
        return replay(current, replayableMaps, visits, themeRewards, achievementRewards, List.of(), at);
    }

    /**
     * @param achievementRewards 진행 기록의 시·도 정복·연속 탐험 마일스톤 보상 + 9단계 계절 회차 배경(받은 시각으로 판정해 둔 것)
     * @param revisits           탐험이 기록한 재방문 도장 지역(9단계 — 2회차 색 변형, 빠진 것만 채운다)
     */
    public static Inventory replay(Inventory current, Set<String> replayableMaps, List<ReplayVisit> visits,
                                   List<ItemSpec> themeRewards, List<ItemSpec> achievementRewards, List<RevisitMark> revisits,
                                   Instant at) {
        Inventory rebuilt = current.rebuildBase(replayableMaps, at);
        rebuilt.adoptRevisits(revisits);
        visits.stream()
            .filter(visit -> visit.visitor().equals(current.explorerId()) && replayableMaps.contains(visit.grant().mapId()))
            .map(ReplayVisit::grant)
            .sorted(Comparator.comparing(CheckInGrant::at))
            .forEach(rebuilt::applyCheckIn);
        rebuilt.grantRewards(themeRewards, at);
        rebuilt.grantRewards(achievementRewards, at);
        rebuilt.adoptHistory(current);
        return rebuilt;
    }
}
