package com.kobi.territory.progression.domain.collectionbook;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 계절 회차 하나의 진행(season_progress 행, 9단계). marks = 회차 기간 안에 처리돼 센 방문(지역, 멤버), completedAt = 처음 완성된 시각
 * (취소해도 유지), completedMembers = 완성 시점 멤버(보상 수령자). 회차가 끝나면 그대로 남는다(닫힌 기록). 다음 상태를 계산하므로 class.
 */
public final class SeasonProgress {

    private final String roundId;
    private final Set<SeasonMark> marks;
    private final Instant completedAt;
    private final Set<ExplorerId> completedMembers;

    private SeasonProgress(String roundId, Set<SeasonMark> marks, Instant completedAt, Set<ExplorerId> completedMembers) {
        this.roundId = Objects.requireNonNull(roundId, "roundId");
        this.marks = Set.copyOf(marks);
        this.completedAt = completedAt;
        this.completedMembers = Set.copyOf(completedMembers);
    }

    public static SeasonProgress restore(String roundId, Set<SeasonMark> marks, Instant completedAt, Set<ExplorerId> completedMembers) {
        return new SeasonProgress(roundId, marks, completedAt, completedMembers);
    }

    static SeasonProgress empty(String roundId) {
        return new SeasonProgress(roundId, Set.of(), null, Set.of());
    }

    public boolean completed() {
        return completedAt != null;
    }

    /** 이 탐험가가 완성 보상의 수령자(완성 시점 멤버)인지. */
    public boolean rewardedTo(ExplorerId explorer) {
        return completed() && completedMembers.contains(explorer);
    }

    /** 모은 지역(센 방문의 지역, 중복 제거). */
    public Set<RegionCode> collected() {
        return marks.stream().map(SeasonMark::region).collect(Collectors.toUnmodifiableSet());
    }

    public int have() {
        return collected().size();
    }

    SeasonProgress with(SeasonMark mark) {
        Set<SeasonMark> next = new LinkedHashSet<>(marks);
        next.add(mark);
        return new SeasonProgress(roundId, next, completedAt, completedMembers);
    }

    SeasonProgress without(SeasonMark mark) {
        Set<SeasonMark> next = new LinkedHashSet<>(marks);
        next.remove(mark);
        return new SeasonProgress(roundId, next, completedAt, completedMembers);
    }

    SeasonProgress withoutAll(Collection<SeasonMark> gone) {
        Set<SeasonMark> next = new LinkedHashSet<>(marks);
        next.removeAll(gone);
        return new SeasonProgress(roundId, next, completedAt, completedMembers);
    }

    /** 병합(공유 지도 재귀속): from 의 표시를 into 의 것으로. */
    SeasonProgress reassigned(ExplorerId from, ExplorerId into) {
        Set<SeasonMark> next = marks.stream().map(mark -> mark.member().equals(from) ? new SeasonMark(mark.region(), into) : mark)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        return new SeasonProgress(roundId, next, completedAt, completedMembers);
    }

    SeasonProgress completedAt(Instant at, Set<ExplorerId> members) {
        return new SeasonProgress(roundId, marks, at, members);
    }

    /** 재계산용: 완성 기록(시각·수령자)만 남긴 진행. */
    SeasonProgress withoutMarks() {
        return new SeasonProgress(roundId, Set.of(), completedAt, completedMembers);
    }

    public String roundId() { return roundId; }
    public Instant completedAt() { return completedAt; }
    public Set<ExplorerId> completedMembers() { return completedMembers; }

    /** 센 방문(정렬된 사본 — 같은 집합이면 같은 저장 표현). */
    public Set<SeasonMark> marks() {
        Set<SeasonMark> sorted = new TreeSet<>(Comparator.comparing(SeasonMark::key));
        sorted.addAll(marks);
        return sorted;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SeasonProgress progress && roundId.equals(progress.roundId) && marks.equals(progress.marks)
            && Objects.equals(completedAt, progress.completedAt) && completedMembers.equals(progress.completedMembers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roundId, marks, completedAt, completedMembers);
    }

    @Override
    public String toString() {
        return "SeasonProgress[" + roundId + ", have=" + have() + ", completedAt=" + completedAt + "]";
    }
}
