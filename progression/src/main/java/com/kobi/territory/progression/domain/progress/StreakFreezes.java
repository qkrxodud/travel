package com.kobi.territory.progression.domain.progress;

import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 일급 컬렉션: 보호권 장부(8단계). 보유 수 = 장부 합계. refId 로 같은 받기·쓰기를 두 번 쌓지 않는다(재전달 멱등).
 * 받을 때는 보유 상한까지만 — 넘치는 몫은 0으로 남는다(받을 일이 있었음은 기록). 쓸 때는 가진 만큼만.
 * 저장소가 새 줄만 추가하도록 복원 시점의 줄 수를 기억한다({@link #unsaved()}).
 */
public final class StreakFreezes {

    private final List<StreakFreezeEntry> entries;
    private final Set<String> refIds;
    private final int restoredCount;

    private StreakFreezes(Collection<StreakFreezeEntry> restored) {
        this.entries = new ArrayList<>();
        this.refIds = new HashSet<>();
        restored.forEach(this::append);
        this.restoredCount = entries.size();
    }

    public static StreakFreezes empty() {
        return new StreakFreezes(List.of());
    }

    public static StreakFreezes of(Collection<StreakFreezeEntry> restored) {
        return new StreakFreezes(restored);
    }

    private void append(StreakFreezeEntry entry) {
        if (!refIds.add(entry.refId())) throw new IllegalStateException("보호권 장부 refId 중복: " + entry.refId());
        entries.add(entry);
    }

    /** 지금 가진 보호권 수. */
    public int held() {
        return entries.stream().mapToInt(StreakFreezeEntry::amount).sum();
    }

    public boolean has(String refId) {
        return refIds.contains(refId);
    }

    /** 이 refId 줄의 수(없으면 0) — 상한에 막혀 0으로 남은 받기도 0. */
    public int amountOf(String refId) {
        return entries.stream().filter(entry -> entry.refId().equals(refId)).mapToInt(StreakFreezeEntry::amount).findFirst()
            .orElse(0);
    }

    /**
     * 보호권 받기 — 같은 refId 는 한 번만. 보유 상한(maxHeld)까지만 채우고 넘치는 몫은 버린다(0이어도 기록).
     * @return 실제로 늘어난 수
     */
    int earn(String refId, FreezeReason reason, int amount, int maxHeld, Instant at) {
        if (amount <= 0 || has(refId)) return 0;
        int granted = Math.max(0, Math.min(amount, maxHeld - held()));
        append(new StreakFreezeEntry(refId, reason, granted, null, at));
        return granted;
    }

    /** 빈 달을 메우느라 count 개 쓴다(그 달 한 번). 가진 것보다 많이는 쓸 수 없다. */
    void use(String refId, YearMonth month, int count, Instant at) {
        if (count <= 0 || has(refId)) return;
        if (count > held()) throw new IllegalStateException("보호권이 모자란다: " + count + " > " + held());
        append(new StreakFreezeEntry(refId, FreezeReason.USED, -count, month, at));
    }

    /** 가장 최근에 쓴 줄(화면의 "보호권으로 스트릭을 지켰어요"). */
    public Optional<StreakFreezeEntry> lastUse() {
        return entries.stream().filter(entry -> entry.reason() == FreezeReason.USED)
            .max(Comparator.comparing(StreakFreezeEntry::at));
    }

    /**
     * 연속 streak 구간 안에서 보호권으로 메운 달 전부(오래된 순, 8단계). 쓴 줄의 달(연속을 이은 달) 바로 앞 count 개 달이 메운 달이다.
     * 마지막 달에서 거꾸로 칠한 달을 streak 개월 수만큼 짚어 가며, 각 칠한 달에 쓴 줄이 있으면 그 앞 빈 달을 건너뛴다 — 구간의 첫 달에 남은
     * 쓴 줄은 없다(모자라 끊긴 달은 쓰지 않는다). 장부만으로 정해지므로 재계산해도 같은 목록이 나온다.
     */
    public List<YearMonth> bridgedWithin(Streak streak) {
        List<YearMonth> bridged = new ArrayList<>();
        YearMonth paintedMonth = streak.lastMonth();
        for (int remaining = streak.months(); remaining > 1; remaining--) {
            int used = usedIn(paintedMonth);
            for (int gap = used; gap >= 1; gap--) bridged.add(paintedMonth.minusMonths(gap));
            paintedMonth = paintedMonth.minusMonths(used + 1L);
        }
        return bridged.stream().sorted().toList();
    }

    /** month 에 연속을 이으려고 쓴 보호권 수(없으면 0). */
    private int usedIn(YearMonth month) {
        return entries.stream().filter(entry -> entry.reason() == FreezeReason.USED && month.equals(entry.month()))
            .mapToInt(entry -> -entry.amount()).sum();
    }

    /** 처리 시각 순(같으면 쌓인 순) — 재계산이 통째로 다시 넣을 때. */
    public List<StreakFreezeEntry> chronological() {
        return entries.stream().sorted(Comparator.comparing(StreakFreezeEntry::at)).toList();
    }

    /** 복원 이후 새로 쌓인 줄(저장소가 이것만 추가한다). */
    public List<StreakFreezeEntry> unsaved() {
        return List.copyOf(entries.subList(restoredCount, entries.size()));
    }

    /** 장부 전체(불변 뷰, 쌓인 순). */
    public List<StreakFreezeEntry> entries() {
        return List.copyOf(entries);
    }
}
