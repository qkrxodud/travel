package com.kobi.territory.progression.domain.progress;

import com.kobi.territory.progression.domain.policy.XpSource;
import com.kobi.territory.progression.domain.quest.QuestPeriod;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * 일급 컬렉션: XP 장부. XP = 장부 합계(감소는 음수 항목으로만). refId 가 멱등 키라 같은 지급은 두 번 쌓이지 않는다.
 * <p>
 * 기본 XP 세대 규칙(D4): 지역당 "회수 안 된 지급"은 최대 1개. 지급 재전달 시 활성 지급이 있으면 no-op,
 * 회수 재전달 시 활성 지급이 없으면 no-op. 취소 후 재체크인은 다음 세대(#k+1)로 다시 지급.
 * 저장소가 새 항목만 추가하도록 복원 시점의 항목 수를 기억한다({@link #unsaved()}).
 */
public final class XpLedger {

    private final List<XpLedgerEntry> entries;
    private final Set<String> refIds;
    private final int restoredCount;

    private XpLedger(Collection<XpLedgerEntry> restored) {
        this.entries = new ArrayList<>();
        this.refIds = new HashSet<>();
        restored.forEach(this::append);
        this.restoredCount = entries.size();
    }

    public static XpLedger empty() {
        return new XpLedger(List.of());
    }

    /** 복원. refId 가 겹치면 데이터 손상으로 거부한다. */
    public static XpLedger of(Collection<XpLedgerEntry> restored) {
        return new XpLedger(restored);
    }

    private void append(XpLedgerEntry entry) {
        if (!refIds.add(entry.refId())) throw new IllegalStateException("장부 refId 중복: " + entry.refId());
        entries.add(entry);
    }

    public long total() {
        return entries.stream().mapToLong(XpLedgerEntry::amount).sum();
    }

    public boolean has(String refId) {
        return refIds.contains(refId);
    }

    /** refId 가 없을 때만 한 번 지급한다. @return 새로 쌓였는지 */
    boolean grantOnce(XpSource source, String refId, int amount, Instant at) {
        if (amount <= 0 || has(refId)) return false;
        append(new XpLedgerEntry(source, amount, refId, at));
        return true;
    }

    /** 지역 기본 XP 지급(세대 규칙). 활성 지급이 있으면 no-op. @return 새로 쌓였는지 */
    boolean grantRegion(ExplorerId explorer, RegionCode region, int amount, Instant at) {
        if (activeGeneration(explorer, region) > 0) return false;
        append(new XpLedgerEntry(XpSource.REGION_BASE, amount,
            RefIds.regionGrant(explorer, region, generations(explorer, region) + 1), at));
        return true;
    }

    /** 지역 기본 XP 회수(활성 지급과 같은 양의 음수 항목). 활성 지급이 없으면 no-op. @return 회수했는지 */
    boolean revokeRegion(ExplorerId explorer, RegionCode region, Instant at) {
        int generation = activeGeneration(explorer, region);
        if (generation == 0) return false;
        String grantRef = RefIds.regionGrant(explorer, region, generation);
        int amount = entries.stream().filter(entry -> entry.refId().equals(grantRef)).findFirst().orElseThrow().amount();
        append(new XpLedgerEntry(XpSource.REGION_BASE, -amount, RefIds.regionRevoke(explorer, region, generation), at));
        return true;
    }

    /** 이 지역의 지급 세대 수(회수 여부 무관). */
    int generations(ExplorerId explorer, RegionCode region) {
        String prefix = RefIds.regionPrefix(explorer, region);
        return (int) entries.stream()
            .filter(entry -> entry.refId().startsWith(prefix) && !entry.refId().endsWith(RefIds.REVOKE)).count();
    }

    /** 회수되지 않은 지급의 세대 번호, 없으면 0. 불변식상 최신 세대만 활성일 수 있다. */
    int activeGeneration(ExplorerId explorer, RegionCode region) {
        int generation = generations(explorer, region);
        return generation > 0 && !has(RefIds.regionRevoke(explorer, region, generation)) ? generation : 0;
    }

    /** 이 출처의 지급 줄(양수, 쌓인 순). */
    public List<XpLedgerEntry> entriesOf(XpSource source) {
        return entries.stream().filter(entry -> entry.source() == source && entry.amount() > 0).toList();
    }

    public Optional<XpLedgerEntry> find(String refId) {
        return entries.stream().filter(entry -> entry.refId().equals(refId)).findFirst();
    }

    /** 그 달 보드의 퀘스트(questIds) 중 보상을 받은 수(8단계 — 이번 달 보호권 진행). */
    int questsRewarded(ExplorerId explorer, QuestPeriod period, Set<String> questIds) {
        return (int) questIds.stream().filter(questId -> has(RefIds.quest(explorer, period, questId))).count();
    }

    /** 그 달 보드의 퀘스트(questIds — 월간 퀘스트 전부)를 모두 보상 받았는지(8단계 보호권). 퀘스트가 없으면 아니다. */
    boolean allQuestsRewarded(ExplorerId explorer, QuestPeriod period, Set<String> questIds) {
        return !questIds.isEmpty() && questIds.stream().allMatch(questId -> has(RefIds.quest(explorer, period, questId)));
    }

    /** 월간 퀘스트(questIds)를 모두 보상 받은 달 → 마지막 보상 시각(8단계 — 재계산이 보호권 받을 일을 다시 만들 때). */
    Map<QuestPeriod, Instant> monthlyQuestsCompleted(Set<String> questIds) {
        Map<QuestPeriod, Map<String, Instant>> byPeriod = new TreeMap<>(Comparator.comparing(QuestPeriod::value));
        entriesOf(XpSource.QUEST).forEach(entry -> {
            QuestPeriod period = RefIds.questPeriodOf(entry.refId());
            if (!period.always()) {
                byPeriod.computeIfAbsent(period, key -> new LinkedHashMap<>()).put(RefIds.subjectOf(entry.refId()), entry.at());
            }
        });
        Map<QuestPeriod, Instant> completed = new LinkedHashMap<>();
        byPeriod.forEach((period, rewarded) -> {
            if (!questIds.isEmpty() && rewarded.keySet().containsAll(questIds)) {
                completed.put(period, questIds.stream().map(rewarded::get).max(Comparator.naturalOrder()).orElseThrow());
            }
        });
        return completed;
    }

    public int count(XpSource source) {
        return (int) entries.stream().filter(entry -> entry.source() == source && entry.amount() > 0).count();
    }

    public List<XpLedgerEntry> entries() {
        return List.copyOf(entries);
    }

    /**
     * 처리 시각(at) 오름차순, 같은 시각이면 지급 순서(출처 순서: 기본 → 시·도 → 선점 → 테마 → 퀘스트 — 체크인 한 번에서 쌓이는
     * 순서와 같다), 그다음 쌓인 순서. 재계산이 장부를 통째로 다시 넣을 때 원래 쌓인 순서를 되살리는 용도(QA S-2·S-3).
     */
    public List<XpLedgerEntry> chronological() {
        return entries.stream()
            .sorted(Comparator.comparing(XpLedgerEntry::at).thenComparing(XpLedgerEntry::source))
            .toList();
    }

    /** 복원 이후 새로 쌓인 항목(저장소가 이것만 추가한다). */
    public List<XpLedgerEntry> unsaved() {
        return List.copyOf(entries.subList(restoredCount, entries.size()));
    }

    /** 최근 count 개(최근 순). */
    public List<XpLedgerEntry> recent(int count) {
        List<XpLedgerEntry> newestFirst = new ArrayList<>(entries);
        Collections.reverse(newestFirst);
        return List.copyOf(newestFirst.subList(0, Math.min(count, newestFirst.size())));
    }

    /**
     * 재계산용: 지역 기본 XP 항목만 뺀 장부. 기본 XP 는 영토(현재 방문)에서 다시 만들고, 나머지(시·도 첫 발·선점·세트·퀘스트)는
     * 취소 비대칭으로 남은 보상이거나 사용자 행동이라 지우지 않는다 — 재생이 빠진 것만 덧붙인다.
     * keep: 탈퇴한 지도 덕분에 계속 활성인 지역 — 재생할 방문이 없으므로 그 기본 XP 항목은 남긴다(§5 탈퇴는 줄이지 않음).
     */
    XpLedger withoutRegionBase(ExplorerId explorer, Set<RegionCode> keep) {
        return new XpLedger(entries.stream().filter(entry -> entry.source() != XpSource.REGION_BASE
            || keep.stream().anyMatch(region -> entry.refId().startsWith(RefIds.regionPrefix(explorer, region)))).toList());
    }
}
