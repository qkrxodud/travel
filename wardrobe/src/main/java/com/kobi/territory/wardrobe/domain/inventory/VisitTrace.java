package com.kobi.territory.wardrobe.domain.inventory;

import com.kobi.territory.common.model.RegionCode;
import java.util.Objects;

/**
 * (지역, 지도) 하나에 대해 마지막으로 반영한 방문 세대와 활성 여부(inventory_visit 행). 회수 자체는 아이템의 근거 방문
 * (OwnedItem.basis)으로 판단하고, 이 흔적은 "이 이벤트를 반영할지"를 정한다 — 세대 번호(같은 (지도, 지역, 멤버)의 체크인 회차, 리더 결정 6)로 늦게 다시 온 옛 이벤트를 무시한다.
 * 세대 0 은 "알 수 없음"(세대 필드가 없던 예전 이벤트) — 리더 결정 Q3: 체크인·취소 모두, 이 (지도, 지역)에 회차 기록(세대 ≥ 1)이
 * 있으면 무시하고, 없을 때만 반영한다(기록 세대는 그대로 0).
 * 다음 상태를 계산하므로 class.
 */
public final class VisitTrace {

    /** 세대를 모르는 예전 이벤트(공개 계약: visitGeneration 0). */
    public static final long UNKNOWN_GENERATION = 0;

    private final RegionCode region;
    private final String mapId;
    private final long generation;
    private final boolean active;

    private VisitTrace(RegionCode region, String mapId, long generation, boolean active) {
        this.region = Objects.requireNonNull(region, "region");
        this.mapId = Objects.requireNonNull(mapId, "mapId");
        this.generation = generation;
        this.active = active;
    }

    public static VisitTrace restore(RegionCode region, String mapId, long generation, boolean active) {
        return new VisitTrace(region, mapId, generation, active);
    }

    static VisitTrace visited(RegionCode region, String mapId, long generation) {
        return new VisitTrace(region, mapId, generation, true);
    }

    /**
     * 체크인(세대 k) 이벤트를 이 흔적에 반영할 수 있는지. 더 새 세대면 반영, 같은 세대면 아직 활성일 때만(재전달 — 지급은 멱등),
     * 같은 세대가 이미 취소됐거나 더 옛 세대면 무시한다.
     */
    boolean acceptsVisit(long visitGeneration) {
        if (visitGeneration == UNKNOWN_GENERATION) return generation == UNKNOWN_GENERATION;
        return visitGeneration > generation
            || (visitGeneration == generation && active);
    }

    /** 취소(세대 k) 이벤트를 반영할 수 있는지 — 이 세대 이상이면(같은 세대의 취소는 체크인보다 뒤). */
    boolean acceptsCancel(long cancelGeneration) {
        if (cancelGeneration == UNKNOWN_GENERATION) return generation == UNKNOWN_GENERATION;
        return cancelGeneration >= generation;
    }

    VisitTrace activeAt(long visitGeneration) {
        return new VisitTrace(region, mapId, Math.max(generation, visitGeneration), true);
    }

    VisitTrace cancelledAt(long cancelGeneration) {
        return new VisitTrace(region, mapId, Math.max(generation, cancelGeneration), false);
    }

    public VisitKey key() { return new VisitKey(region, mapId); }
    public RegionCode region() { return region; }
    public String mapId() { return mapId; }
    public long generation() { return generation; }
    public boolean active() { return active; }

    @Override
    public boolean equals(Object other) {
        return other instanceof VisitTrace trace && region.equals(trace.region) && mapId.equals(trace.mapId)
            && generation == trace.generation && active == trace.active;
    }

    @Override
    public int hashCode() {
        return Objects.hash(region, mapId, generation, active);
    }

    @Override
    public String toString() {
        return "VisitTrace[" + region + "@" + mapId + " #" + generation + (active ? " active" : " cancelled") + "]";
    }
}
