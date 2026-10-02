package com.kobi.territory.wardrobe.domain.inventory;

import com.kobi.territory.common.model.RegionCode;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 일급 컬렉션: 탐험가의 방문 흔적((지역, 지도)별 세대·활성). 지역 아이템 지급·회수를 탐험가 단위로 판단한다.
 * 저장소가 바뀐 흔적만 쓰도록 복원 이후 바뀐 키를 기억한다({@link #changed()}).
 */
public final class VisitTraces {

    private final Map<String, VisitTrace> byKey = new LinkedHashMap<>();
    private final Set<String> changed = new LinkedHashSet<>();

    private VisitTraces(Collection<VisitTrace> restored) {
        restored.forEach(trace -> {
            if (byKey.put(keyOf(trace.region(), trace.mapId()), trace) != null) {
                throw new IllegalStateException("방문 흔적 중복: " + trace);
            }
        });
    }

    public static VisitTraces empty() {
        return new VisitTraces(List.of());
    }

    public static VisitTraces of(Collection<VisitTrace> restored) {
        return new VisitTraces(restored);
    }

    /**
     * 지도 mapId 에서 이 지역 체크인(세대 k)이 왔다. @return 반영했는지(옛 세대·이미 취소된 같은 세대면 false — 지급하지 않는다)
     */
    boolean visit(RegionCode region, String mapId, long generation) {
        String key = keyOf(region, mapId);
        Optional<VisitTrace> current = Optional.ofNullable(byKey.get(key));
        if (current.isPresent() && !current.get().acceptsVisit(generation)) return false;
        put(key, current.map(trace -> trace.activeAt(generation)).orElseGet(() -> VisitTrace.visited(region, mapId, generation)));
        return true;
    }

    /**
     * 지도 mapId 의 이 지역 체크인(세대 k)이 취소됐다. 모르는 방문이거나 옛 세대면 no-op.
     * @return 반영했는지(반영했으면 그 방문은 더 이상 아이템의 근거가 아니다)
     */
    boolean cancel(RegionCode region, String mapId, long generation) {
        String key = keyOf(region, mapId);
        VisitTrace current = byKey.get(key);
        if (current == null || !current.acceptsCancel(generation)) return false;
        put(key, current.cancelledAt(generation));
        return true;
    }

    public boolean activeAnywhere(RegionCode region) {
        return byKey.values().stream().anyMatch(trace -> trace.active() && trace.region().equals(region));
    }

    public Optional<VisitTrace> find(RegionCode region, String mapId) {
        return Optional.ofNullable(byKey.get(keyOf(region, mapId)));
    }

    /**
     * 재계산 출발점: 다시 재생할 지도의 <b>활성</b> 흔적을 뺀 사본. 재생할 수 없는 지도(탈퇴)의 흔적과 취소된 흔적(세대 기록 —
     * 옛 이벤트 무시용, 지금 보이는 방문 이력에서는 다시 만들 수 없다)은 유지한다. 재생되는 방문은 그보다 새 세대라 다시 활성이 된다.
     */
    VisitTraces rebuildBase(Set<String> replayableMaps) {
        return new VisitTraces(byKey.values().stream()
            .filter(trace -> !trace.active() || !replayableMaps.contains(trace.mapId())).toList());
    }

    /** 전부(재계산 저장). */
    public List<VisitTrace> all() {
        return List.copyOf(byKey.values());
    }

    /** 복원 이후 바뀐 흔적(저장소가 이것만 쓴다). */
    public List<VisitTrace> changed() {
        return changed.stream().map(byKey::get).toList();
    }

    private void put(String key, VisitTrace trace) {
        byKey.put(key, trace);
        changed.add(key);
    }

    private static String keyOf(RegionCode region, String mapId) {
        return region.value() + "@" + mapId;
    }
}
