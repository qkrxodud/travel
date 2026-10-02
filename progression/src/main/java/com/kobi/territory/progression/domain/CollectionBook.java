package com.kobi.territory.progression.domain;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import java.time.Instant;
import java.util.Collection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 도감 애그리거트(mapId — 지도 단위). 세트별 진행·완성. 설계 용어 Collection(도감) ↔ 코드 CollectionBook
 * (JDK java.util.Collection 과 겹치지 않게 — 명명 규칙).
 *
 * 불변식
 * - 완성은 정의된 지역이 한 지도에 모두 모였을 때 단 한 번(completedAt 은 한 번 정해지면 바뀌지 않는다).
 * - 완성 후 지역을 취소해도 완성 기록 유지(보상 회수 없음). 진행(collected)에서는 빠진다.
 * - 지도에 그 지역이 다른 멤버 방문으로 남아 있으면(regionStillOnMap) 진행에서도 빼지 않는다(D2).
 */
public final class CollectionBook {

    private final String mapId;
    private final Map<String, SetProgress> progress;

    private CollectionBook(String mapId, Collection<SetProgress> rows) {
        this.mapId = Objects.requireNonNull(mapId, "mapId");
        this.progress = new LinkedHashMap<>();
        rows.forEach(row -> progress.put(row.setId(), row));
    }

    public static CollectionBook empty(String mapId) {
        return new CollectionBook(mapId, List.of());
    }

    public static CollectionBook restore(String mapId, Collection<SetProgress> rows) {
        return new CollectionBook(mapId, rows);
    }

    /** 지역이 칠해짐 → 세트 진행, 처음 완성된 세트를 돌려준다. 같은 지역이 다시 와도 변화 없음(멱등). */
    public List<SetCompletion> applyVisit(RegionCode code, ExplorerId visitor, Instant at, SetCatalog sets) {
        List<SetCompletion> completed = new ArrayList<>();
        for (CollectionSet definition : sets.containing(code)) {
            SetProgress next = of(definition.id()).with(code);
            if (!next.completed() && next.collected().containsAll(definition.regions())) {
                next = next.completedAt(at);
                completed.add(new SetCompletion(mapId, definition.id(), visitor, at));
            }
            progress.put(definition.id(), next);
        }
        return List.copyOf(completed);
    }

    /** 지역 방문 취소. 지도에 그 지역이 남아 있지 않을 때만 진행에서 뺀다. 완성 기록은 유지. */
    public void revokeVisit(RegionCode code, boolean regionStillOnMap, SetCatalog sets) {
        if (regionStillOnMap) return;
        sets.containing(code).forEach(definition ->
            progress.computeIfPresent(definition.id(), (setId, setProgress) -> setProgress.without(code)));
    }

    /** 재계산용: 완성 기록(completedAt)은 남기고 모은 지역만 비운 도감 — 완성은 한 번이고 취소해도 유지되는 불변식 때문. */
    public CollectionBook rebuildBase() {
        return new CollectionBook(mapId, progress.values().stream()
            .map(setProgress -> new SetProgress(setProgress.setId(), java.util.Set.of(), setProgress.completedAt())).toList());
    }

    /** 완성 기록이 있는 세트 id(재계산 복구 규칙용). */
    public List<String> completedSetIds() {
        return progress.values().stream().filter(SetProgress::completed).map(SetProgress::setId).toList();
    }

    public SetProgress of(String setId) {
        return progress.getOrDefault(setId, SetProgress.empty(setId));
    }

    public int completedCount() {
        return (int) progress.values().stream().filter(SetProgress::completed).count();
    }

    public String mapId() { return mapId; }

    public List<SetProgress> rows() { return List.copyOf(progress.values()); }
}
