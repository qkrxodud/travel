package com.kobi.territory.progression.domain.collectionbook;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * 테마 하나의 진행(set_progress 행). collected 는 지금 지도에 칠해진 테마 지역, completedAt 은 처음 완성된 시각
 * (완성 후 지역을 취소해도 유지 — 보상 회수 없음), completedMembers 는 완성 시점 지도 멤버(보상 수령자 — 결정 1,
 * set_progress.completed_member_ids). 설계 용어 SetProgress.
 * 다음 상태(지역 추가·제거·완성)를 계산하므로 class(class vs record 기준).
 */
public final class ThemeProgress {

    private final String themeId;
    private final Set<RegionCode> collected;
    private final Instant completedAt;
    private final Set<ExplorerId> completedMembers;

    private ThemeProgress(String themeId, Set<RegionCode> collected, Instant completedAt, Set<ExplorerId> completedMembers) {
        this.themeId = Objects.requireNonNull(themeId, "themeId");
        this.collected = Set.copyOf(collected);
        this.completedAt = completedAt;
        this.completedMembers = Set.copyOf(completedMembers);
    }

    /** 저장된 값으로 복원(completedAt 없으면 null). */
    public static ThemeProgress restore(String themeId, Set<RegionCode> collected, Instant completedAt,
                                        Set<ExplorerId> completedMembers) {
        return new ThemeProgress(themeId, collected, completedAt, completedMembers);
    }

    static ThemeProgress empty(String themeId) {
        return new ThemeProgress(themeId, Set.of(), null, Set.of());
    }

    public boolean completed() {
        return completedAt != null;
    }

    /** 이 탐험가가 완성 보상(테마 보너스 XP·칭호)의 수령자인지 — 완성 시점 멤버였는지. */
    public boolean rewardedTo(ExplorerId explorer) {
        return completed() && completedMembers.contains(explorer);
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
        return new ThemeProgress(themeId, next, completedAt, completedMembers);
    }

    ThemeProgress without(RegionCode region) {
        Set<RegionCode> next = sortedCopy();
        next.remove(region);
        return new ThemeProgress(themeId, next, completedAt, completedMembers);
    }

    ThemeProgress completedAt(Instant at, Set<ExplorerId> members) {
        return new ThemeProgress(themeId, collected, at, members);
    }

    /** 재계산용: 완성 기록(시각·수령자)만 남긴 진행. */
    ThemeProgress withoutCollected() {
        return new ThemeProgress(themeId, Set.of(), completedAt, completedMembers);
    }

    public String themeId() { return themeId; }
    public Set<RegionCode> collected() { return collected; }
    public Instant completedAt() { return completedAt; }
    public Set<ExplorerId> completedMembers() { return completedMembers; }

    private Set<RegionCode> sortedCopy() {
        Set<RegionCode> copy = new TreeSet<>(Comparator.comparing(RegionCode::value));
        copy.addAll(collected);
        return copy;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ThemeProgress progress && themeId.equals(progress.themeId)
            && collected.equals(progress.collected) && Objects.equals(completedAt, progress.completedAt)
            && completedMembers.equals(progress.completedMembers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(themeId, collected, completedAt, completedMembers);
    }

    @Override
    public String toString() {
        return "ThemeProgress[" + themeId + ", have=" + collected.size() + ", completedAt=" + completedAt + "]";
    }
}
