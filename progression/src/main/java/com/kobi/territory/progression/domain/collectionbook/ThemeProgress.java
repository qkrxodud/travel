package com.kobi.territory.progression.domain.collectionbook;

import com.kobi.territory.common.model.RegionCode;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * 테마 하나의 진행(set_progress 행). collected 는 지금 지도에 칠해진 테마 지역, completedAt 은 처음 완성된 시각
 * (완성 후 지역을 취소해도 유지 — 보상 회수 없음). 설계 용어 SetProgress.
 * 다음 상태(지역 추가·제거·완성)를 계산하므로 class(class vs record 기준).
 */
public final class ThemeProgress {

    private final String themeId;
    private final Set<RegionCode> collected;
    private final Instant completedAt;

    private ThemeProgress(String themeId, Set<RegionCode> collected, Instant completedAt) {
        this.themeId = Objects.requireNonNull(themeId, "themeId");
        this.collected = Set.copyOf(collected);
        this.completedAt = completedAt;
    }

    /** 저장된 값으로 복원(completedAt 없으면 null). */
    public static ThemeProgress restore(String themeId, Set<RegionCode> collected, Instant completedAt) {
        return new ThemeProgress(themeId, collected, completedAt);
    }

    static ThemeProgress empty(String themeId) {
        return new ThemeProgress(themeId, Set.of(), null);
    }

    public boolean completed() {
        return completedAt != null;
    }

    public int have() {
        return collected.size();
    }

    public boolean holds(RegionCode region) {
        return collected.contains(region);
    }

    ThemeProgress with(RegionCode region) {
        Set<RegionCode> next = sortedCopy();
        next.add(region);
        return new ThemeProgress(themeId, next, completedAt);
    }

    ThemeProgress without(RegionCode region) {
        Set<RegionCode> next = sortedCopy();
        next.remove(region);
        return new ThemeProgress(themeId, next, completedAt);
    }

    ThemeProgress completedAt(Instant at) {
        return new ThemeProgress(themeId, collected, at);
    }

    /** 재계산용: 완성 기록만 남긴 진행. */
    ThemeProgress withoutCollected() {
        return new ThemeProgress(themeId, Set.of(), completedAt);
    }

    public String themeId() { return themeId; }
    public Set<RegionCode> collected() { return collected; }
    public Instant completedAt() { return completedAt; }

    private Set<RegionCode> sortedCopy() {
        Set<RegionCode> copy = new TreeSet<>(Comparator.comparing(RegionCode::value));
        copy.addAll(collected);
        return copy;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ThemeProgress progress && themeId.equals(progress.themeId)
            && collected.equals(progress.collected) && Objects.equals(completedAt, progress.completedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(themeId, collected, completedAt);
    }

    @Override
    public String toString() {
        return "ThemeProgress[" + themeId + ", have=" + collected.size() + ", completedAt=" + completedAt + "]";
    }
}
