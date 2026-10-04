package com.kobi.territory.wardrobe.domain.inventory;

import com.kobi.territory.common.model.RegionCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 일급 컬렉션: 재방문 도장을 받은 지역(9단계). 아이템 정의를 늘리지 않고 보유 아이템의 변형 속성으로 — 지역 특산물은 그 지역에 도장이 있으면
 * 2회차 색 변형(variant 2)으로 그린다. 도장은 지우지 않으므로 표시도 지우지 않는다(아이템이 회수됐다 다시 와도 변형 유지).
 * 복원 이후 새로 생긴 표시를 기억한다(저장소가 그것만 넣는다).
 */
public final class RevisitMarks {

    /** 기본 색. */
    public static final int BASE_VARIANT = 1;
    /** 재방문 2회차 색 변형. */
    public static final int REVISIT_VARIANT = 2;

    private final Map<RegionCode, RevisitMark> byRegion = new LinkedHashMap<>();
    private final List<RevisitMark> added = new ArrayList<>();

    private RevisitMarks(Collection<RevisitMark> restored) {
        restored.forEach(mark -> byRegion.putIfAbsent(mark.region(), mark));
    }

    public static RevisitMarks empty() {
        return new RevisitMarks(List.of());
    }

    public static RevisitMarks of(Collection<RevisitMark> restored) {
        return new RevisitMarks(restored);
    }

    /** 그 지역에 도장이 있음을 표시한다. 이미 있으면 그대로(멱등). @return 새로 표시했는지 */
    boolean mark(RegionCode region, Instant at) {
        if (byRegion.containsKey(region)) return false;
        RevisitMark mark = new RevisitMark(region, at);
        byRegion.put(region, mark);
        added.add(mark);
        return true;
    }

    /** 다른 표시들을 합친다(병합·재계산). @return 새로 표시한 수 */
    int adopt(Collection<RevisitMark> marks) {
        return (int) marks.stream().filter(mark -> mark(mark.region(), mark.markedAt())).count();
    }

    public boolean revisited(RegionCode region) {
        return region != null && byRegion.containsKey(region);
    }

    /** 지역 특산물의 색 변형 번호 — 도장이 있는 지역이면 2, 아니면 1(지역 아이템이 아니면 null 을 넘긴다 → 1). */
    public int variantOf(RegionCode itemRegion) {
        return revisited(itemRegion) ? REVISIT_VARIANT : BASE_VARIANT;
    }

    public List<RevisitMark> all() {
        return List.copyOf(byRegion.values());
    }

    /** 복원 이후 새로 생긴 표시(저장소가 insert). */
    public List<RevisitMark> added() {
        return List.copyOf(added);
    }
}
