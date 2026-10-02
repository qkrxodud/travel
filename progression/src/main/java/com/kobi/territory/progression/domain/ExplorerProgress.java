package com.kobi.territory.progression.domain;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.common.model.RegionCode;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 진행 애그리거트(explorerId). XP 장부·레벨·스트릭·뱃지·칭호, 그리고 탐험가 단위 지역(explorer_region).
 *
 * 불변식
 * - XP = 장부 합계. 감소는 음수 항목으로만. 레벨은 결정적 함수(LevelCurve).
 * - 뱃지·칭호는 추가만(회수 없음). 선택 칭호는 얻은 것 중에서만(없으면 레벨 칭호를 보여준다).
 * - 기본 XP 는 탐험가당 지역당 활성 1개: 어느 지도에서든 처음 활성이 되면 지급, 모든 지도에서 취소되면 회수(D2·D4).
 * - 시·도 첫 발 보너스는 탐험가 기준(이벤트의 지도 기준 isFirstInProvince 가 아님) 1회, 취소해도 회수 없음(D3).
 * - XP·레벨·스트릭은 본인 체크인만 집계한다(RegionVisited.explorerId = 체크인한 사람).
 */
public final class ExplorerProgress {

    private final ExplorerId explorerId;
    private final XpLedger ledger;
    private final ExploredRegions regions;
    private final Map<String, Instant> badges;
    private final Map<String, Instant> titles;
    private final List<String> unsavedBadges = new ArrayList<>();
    private final List<String> unsavedTitles = new ArrayList<>();
    private final boolean rebuilt;
    private Streak streak;
    private String selectedTitle;
    private int level;

    private ExplorerProgress(ExplorerId explorerId, XpLedger ledger, ExploredRegions regions, Streak streak,
                             Map<String, Instant> badges, Map<String, Instant> titles, String selectedTitle, int level,
                             boolean rebuilt) {
        this.explorerId = Objects.requireNonNull(explorerId, "explorerId");
        this.ledger = ledger;
        this.regions = regions;
        this.streak = Objects.requireNonNull(streak, "streak");
        this.badges = new LinkedHashMap<>(badges);
        this.titles = new LinkedHashMap<>(titles);
        this.selectedTitle = selectedTitle;
        this.level = level;
        this.rebuilt = rebuilt;
    }

    /** 처음 보는 탐험가: XP 0, 레벨 1(레벨 1 칭호 획득). */
    public static ExplorerProgress start(ExplorerId explorerId, ProgressionPolicy policy, Instant at) {
        ExplorerProgress progress = new ExplorerProgress(explorerId, XpLedger.empty(), ExploredRegions.empty(),
            Streak.NONE, Map.of(), Map.of(), null, 1, false);
        progress.settle(policy, at, 0, 1);
        return progress;
    }

    public static ExplorerProgress restore(ExplorerId explorerId, XpLedger ledger, ExploredRegions regions, Streak streak,
                                           Map<String, Instant> badges, Map<String, Instant> titles, String selectedTitle,
                                           int level) {
        return new ExplorerProgress(explorerId, ledger, regions, streak, badges, titles, selectedTitle, level, false);
    }

    // ---- 커맨드 ----------------------------------------------------------------------------------------------

    /** 본인 체크인(RegionVisited) 반영: 탐험가 단위 지역 → 보상(기본·시·도·선점) → 스트릭 → 레벨·칭호·뱃지. 멱등. */
    public ProgressChange applyVisit(ProgressVisit visit, ProgressionPolicy policy) {
        long xpBefore = ledger.total();
        int levelBefore = level;
        boolean firstInProvince = !regions.touches(visit.provinceCode());
        regions.add(visit.region(), visit.provinceCode(), visit.rarity(), visit.mapId(), visit.visitedAt());
        for (XpAward award : policy.rewards().checkIn(visit.rarity(), firstInProvince, visit.firstClaim())) {
            grant(award, visit);
        }
        streak = streak.record(YearMonth.from(visit.visitedAt().atZone(policy.zone())));
        return settle(policy, visit.visitedAt(), xpBefore, levelBefore);
    }

    /** 본인 체크인 취소(VisitCancelled): 그 지역이 모든 지도에서 사라졌을 때만 기본 XP 회수. 그 밖은 유지(취소 비대칭). 멱등. */
    public ProgressChange revokeVisit(String mapId, RegionCode region, Instant at, ProgressionPolicy policy) {
        long xpBefore = ledger.total();
        int levelBefore = level;
        if (regions.remove(region, mapId)) {
            ledger.revokeRegion(explorerId, region, at);
        }
        return settle(policy, at, xpBefore, levelBefore);
    }

    /** 도감 세트 완성 보너스(SetCompleted). 탐험가당 세트당 1회. */
    public ProgressChange applySetCompleted(String setId, Instant at, ProgressionPolicy policy) {
        long xpBefore = ledger.total();
        int levelBefore = level;
        ledger.grantOnce(XpSource.SET_COMPLETE, RefIds.set(explorerId, setId), policy.rewards().setComplete(), at);
        return settle(policy, at, xpBefore, levelBefore);
    }

    /** 퀘스트 보상(QuestCompleted). 보드·퀘스트당 1회. */
    public ProgressChange applyQuestReward(QuestPeriod period, String questId, int xp, Instant at, ProgressionPolicy policy) {
        long xpBefore = ledger.total();
        int levelBefore = level;
        ledger.grantOnce(XpSource.QUEST, RefIds.quest(explorerId, period, questId), xp, at);
        return settle(policy, at, xpBefore, levelBefore);
    }

    /**
     * 재계산 복구 규칙(QA P1-2, Q2 승인): 완성 기록은 있는데 장부에 그 세트 보너스가 없으면 지급하고, 보상을 받은(claimed)
     * 퀘스트인데 장부에 그 XP 가 없으면 지급한다. 칭호·뱃지는 settle 이 보정한다. refId 가 같아 멱등하다.
     */
    public ProgressChange recoverRewards(List<String> completedSetIds, List<QuestReward> claimedRewards, Instant at,
                                         ProgressionPolicy policy) {
        long xpBefore = ledger.total();
        int levelBefore = level;
        completedSetIds.forEach(setId -> ledger.grantOnce(XpSource.SET_COMPLETE, RefIds.set(explorerId, setId),
            policy.rewards().setComplete(), at));
        claimedRewards.forEach(reward -> ledger.grantOnce(XpSource.QUEST,
            RefIds.quest(explorerId, reward.period(), reward.questId()), reward.xp(), at));
        return settle(policy, at, xpBefore, levelBefore);
    }

    /** 칭호 선택. null 이면 선택 해제(레벨 칭호 표시). 얻은 칭호만 고를 수 있다. */
    public void selectTitle(String titleId, ProgressionPolicy policy) {
        if (titleId == null || titleId.isBlank()) {
            selectedTitle = null;
            return;
        }
        if (policy.titles().stream().noneMatch(title -> title.id().equals(titleId))) {
            throw ProgressionError.TITLE_NOT_FOUND.exception(titleId);
        }
        if (!titles.containsKey(titleId)) throw ProgressionError.TITLE_NOT_EARNED.exception(titleId);
        selectedTitle = titleId;
    }

    /**
     * 재계산(RecalculateService)용 출발점: 현재 방문으로만 정해지는 것(지역 활성·기본 XP·스트릭)은 비우고, 취소 비대칭·
     * "추가만" 규칙에 묶인 것(시·도 첫 발·선점·세트·퀘스트 XP, 뱃지·칭호·선택 칭호, 지역의 처음 밟은 시각)은 유지한다.
     * 재생은 빠진 것만 덧붙이므로 결과가 이벤트 누적과 같다. 저장소는 이 사본을 통째로 동기화한다({@link #rebuilt()}).
     */
    public ExplorerProgress rebuildBase() {
        return new ExplorerProgress(explorerId, ledger.withoutRegionBase(), regions.deactivated(), Streak.NONE, badges,
            titles, selectedTitle, level, true);
    }

    // ---- 판정 ----------------------------------------------------------------------------------------------

    private void grant(XpAward award, ProgressVisit visit) {
        switch (award.source()) {
            case REGION_BASE -> ledger.grantRegion(explorerId, visit.region(), award.amount(), visit.visitedAt());
            case PROVINCE_FIRST -> ledger.grantOnce(XpSource.PROVINCE_FIRST,
                RefIds.province(explorerId, visit.provinceCode()), award.amount(), visit.visitedAt());
            case FIRST_CLAIM -> ledger.grantOnce(XpSource.FIRST_CLAIM, RefIds.claim(visit.mapId(), visit.region(), explorerId),
                award.amount(), visit.visitedAt());
            default -> throw new IllegalArgumentException("체크인 보상이 아닌 출처: " + award.source());
        }
    }

    /** 레벨 재계산 → 칭호·뱃지 추가(회수 없음). */
    private ProgressChange settle(ProgressionPolicy policy, Instant at, long xpBefore, int levelBefore) {
        level = policy.curve().levelOf(ledger.total());
        List<String> newTitles = new ArrayList<>();
        policy.titles().stream().filter(title -> !titles.containsKey(title.id()) && earned(title, policy)).forEach(title -> {
            titles.put(title.id(), at);
            newTitles.add(title.id());
        });
        BadgeFacts facts = badgeFacts(policy);
        List<String> newBadges = new ArrayList<>();
        policy.badges().stream().filter(badge -> !badges.containsKey(badge.id()) && badge.rule().satisfiedBy(facts))
            .forEach(badge -> {
                badges.put(badge.id(), at);
                newBadges.add(badge.id());
            });
        unsavedTitles.addAll(newTitles);
        unsavedBadges.addAll(newBadges);
        Optional<Integer> levelUp = level > levelBefore ? Optional.of(level) : Optional.empty();
        return new ProgressChange(xpBefore, ledger.total(), levelUp, List.copyOf(newBadges), List.copyOf(newTitles), at);
    }

    private boolean earned(TitleRule title, ProgressionPolicy policy) {
        return switch (title.source()) {
            case LEVEL -> level >= Integer.parseInt(title.ref());
            case SET -> ledger.has(RefIds.set(explorerId, title.ref()));
            case QUEST -> ledger.has(RefIds.quest(explorerId, QuestPeriod.ALL, title.ref()));
            case PROVINCE -> {
                int total = policy.provinceTotals().getOrDefault(title.ref(), 0);
                yield total > 0 && regions.perProvince().getOrDefault(title.ref(), 0) >= total;
            }
        };
    }

    private BadgeFacts badgeFacts(ProgressionPolicy policy) {
        return new BadgeFacts(regions.activeCount(), regions.count(Rarity.LEGEND), regions.perProvince(),
            policy.provinceTotals(), policy.totalRegions(), ledger.count(XpSource.SET_COMPLETE), streak.months());
    }

    // ---- 조회 ----------------------------------------------------------------------------------------------

    public ExplorerId explorerId() { return explorerId; }
    public long xp() { return ledger.total(); }
    public int level() { return level; }
    public Streak streak() { return streak; }
    public XpLedger ledger() { return ledger; }
    public ExploredRegions regions() { return regions; }
    public Map<String, Instant> badges() { return Collections.unmodifiableMap(badges); }
    public Map<String, Instant> titles() { return Collections.unmodifiableMap(titles); }
    public Optional<String> selectedTitle() { return Optional.ofNullable(selectedTitle); }

    /** 재계산으로 만든 사본인지 — 저장소가 통째로 동기화해야 한다(지운 장부·지역 반영). */
    public boolean rebuilt() { return rebuilt; }

    /** 복원 이후 새로 얻은 뱃지·칭호 id(저장소가 이것만 추가한다). */
    public List<String> unsavedBadges() { return List.copyOf(unsavedBadges); }
    public List<String> unsavedTitles() { return List.copyOf(unsavedTitles); }

    /** 화면에 보일 칭호 id: 선택한 칭호, 없으면 레벨 칭호. */
    public String displayTitle(ProgressionPolicy policy) {
        return selectedTitle().orElseGet(() -> levelTitle(policy));
    }

    /** 현재 레벨 이하에서 가장 높은 레벨 칭호 id. */
    public String levelTitle(ProgressionPolicy policy) {
        return policy.titles().stream()
            .filter(title -> title.source() == TitleRule.Source.LEVEL && level >= Integer.parseInt(title.ref()))
            .reduce((lower, higher) -> higher).map(TitleRule::id).orElse(null);
    }
}
