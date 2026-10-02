package com.kobi.territory.progression.domain;

import com.kobi.territory.common.model.RegionCode;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * 세트 하나의 진행(set_progress 행). collected 는 지금 지도에 칠해진 세트 지역, completedAt 은 처음 완성된 시각
 * (완성 후 지역을 취소해도 유지 — 보상 회수 없음).
 */
public record SetProgress(String setId, Set<RegionCode> collected, Instant completedAt) {

    public SetProgress {
        Objects.requireNonNull(setId, "setId");
        collected = Set.copyOf(collected);
    }

    static SetProgress empty(String setId) {
        return new SetProgress(setId, Set.of(), null);
    }

    public boolean completed() {
        return completedAt != null;
    }

    public int have() {
        return collected.size();
    }

    SetProgress with(RegionCode code) {
        Set<RegionCode> next = new TreeSet<>(java.util.Comparator.comparing(RegionCode::value));
        next.addAll(collected);
        next.add(code);
        return new SetProgress(setId, next, completedAt);
    }

    SetProgress without(RegionCode code) {
        Set<RegionCode> next = new TreeSet<>(java.util.Comparator.comparing(RegionCode::value));
        next.addAll(collected);
        next.remove(code);
        return new SetProgress(setId, next, completedAt);
    }

    SetProgress completedAt(Instant at) {
        return new SetProgress(setId, collected, at);
    }
}
