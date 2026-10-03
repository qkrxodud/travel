package com.kobi.territory.progression.domain.progress;

import com.kobi.territory.progression.domain.policy.XpSource;
import com.kobi.territory.progression.domain.ProgressionError;
import com.kobi.territory.progression.domain.policy.BadgeFacts;
import com.kobi.territory.progression.domain.policy.ProgressionPolicy;
import com.kobi.territory.progression.domain.policy.TitleRule;
import com.kobi.territory.progression.domain.policy.XpAward;
import com.kobi.territory.progression.domain.quest.QuestPeriod;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.common.model.RegionCode;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * 진행 애그리거트(explorerId). XP 장부·레벨·스트릭·뱃지·칭호, 그리고 탐험가 단위 지역(explorer_region).
 *
 * 불변식
 * - XP = 장부 합계. 감소는 음수 항목으로만. 레벨은 결정적 함수(LevelCurve).
 * - 뱃지·칭호는 추가만(회수 없음). 선택 칭호는 얻은 것 중에서만(없으면 레벨 칭호를 보여준다).
 * - 기본 XP 는 탐험가당 지역당 활성 1개: 어느 지도에서든 처음 활성이 되면 지급, 모든 지도에서 취소되면 회수(D2·D4).
 * - 시·도 첫 발 보너스는 탐험가 기준(이벤트의 지도 기준 isFirstInProvince 가 아님) 1회, 취소해도 회수 없음(D3).
 * - XP·레벨·스트릭은 본인 체크인만 집계한다(RegionVisited.explorerId = 체크인한 사람).
 * 8단계(게임 요소 1순위)
 * - 보호권: 빈 달 뒤 다시 칠하면 빈 달 수만큼 써서 스트릭을 잇는다(모자라면 끊기고 쓰지 않는다). 한 달 월간 퀘스트를 모두 보상 받거나
 *   마일스톤에 닿으면 받는다 — 보유 상한까지만. 받기·쓰기는 장부(StreakFreezes)로 남아 재계산이 같은 결과를 다시 만든다.
 * - 연속 탐험 마일스톤: 처음 닿으면 한 번(XP·칭호·보호권), 끊겼다 다시 쌓아도 다시 없음.
 * - 이번 주 미스터리 지역: 그 주(처리 시각 기준)에 칠하면 주마다 한 번 보너스, 취소해도 회수 없음.
 * - 시·도 정복: 탐험가 단위로 한 시·도의 현행 지역(폐지 지역 제외)을 모두 칠하면 한 번, 취소해도 회수 없음.
 */
public final class ExplorerProgress {

    private final ExplorerId explorerId;
    private final XpLedger ledger;
    private final ExploredRegions regions;
    private final StreakFreezes freezes;
    private final Map<String, Instant> badges;
    private final Map<String, Instant> titles;
    private final List<String> unsavedBadges = new ArrayList<>();
    private final List<String> unsavedTitles = new ArrayList<>();
    /** 지금 커맨드 하나에서 생긴 8단계 사건(settle 이 결과에 담고 비운다). */
    private final List<Integer> reachedNow = new ArrayList<>();
    private final List<String> conqueredNow = new ArrayList<>();
    private MysteryFact mysteryNow;
    private int freezesUsedNow;
    private Streak streak;
    private String selectedTitle;
    private int level;
    private Instant updatedAt;

    private ExplorerProgress(ExplorerId explorerId, XpLedger ledger, ExploredRegions regions, StreakFreezes freezes,
                             Streak streak, Map<String, Instant> badges, Map<String, Instant> titles, String selectedTitle,
                             int level, Instant updatedAt) {
        this.explorerId = Objects.requireNonNull(explorerId, "explorerId");
        this.ledger = ledger;
        this.regions = regions;
        this.freezes = Objects.requireNonNull(freezes, "freezes");
        this.streak = Objects.requireNonNull(streak, "streak");
        this.badges = new LinkedHashMap<>(badges);
        this.titles = new LinkedHashMap<>(titles);
        this.selectedTitle = selectedTitle;
        this.level = level;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /** 처음 보는 탐험가: XP 0, 레벨 1(레벨 1 칭호 획득). */
    public static ExplorerProgress start(ExplorerId explorerId, ProgressionPolicy policy, Instant at) {
        ExplorerProgress progress = new ExplorerProgress(explorerId, XpLedger.empty(), ExploredRegions.empty(),
            StreakFreezes.empty(), Streak.NONE, Map.of(), Map.of(), null, 1, at);
        progress.settle(policy, at, 0, 1);
        return progress;
    }

    public static ExplorerProgress restore(ExplorerId explorerId, XpLedger ledger, ExploredRegions regions,
                                           StreakFreezes freezes, Streak streak, Map<String, Instant> badges,
                                           Map<String, Instant> titles, String selectedTitle, int level, Instant updatedAt) {
        return new ExplorerProgress(explorerId, ledger, regions, freezes, streak, badges, titles, selectedTitle, level,
            updatedAt);
    }

    // ---- 커맨드 ----------------------------------------------------------------------------------------------

    /**
     * 본인 체크인(RegionVisited) 반영: 탐험가 단위 지역 → 보상(기본·시·도·선점·이번 주 미스터리) → 스트릭(빈 달은 보호권으로) →
     * 마일스톤 → 시·도 정복·레벨·칭호·뱃지. 멱등.
     */
    public ProgressChange applyVisit(ProgressVisit visit, ProgressionPolicy policy) {
        long xpBefore = ledger.total();
        int levelBefore = level;
        if (regions.staleVisit(visit.region(), visit.mapId(), visit.generation())) {
            return unchanged(xpBefore, levelBefore, visit.visitedAt()); // 늦게 온 예전 회차(결정 6)
        }
        boolean firstInProvince = !regions.touches(visit.provinceCode());
        regions.add(visit.region(), visit.provinceCode(), visit.rarity(), visit.mapId(), visit.generation(), visit.visitedAt());
        for (XpAward award : policy.rewards().checkIn(visit.rarity(), firstInProvince, visit.firstClaim(),
                visit.mysteryFound().isPresent())) {
            grant(award, visit);
        }
        recordStreak(YearMonth.from(visit.visitedAt().atZone(policy.zone())), visit.visitedAt());
        reachMilestones(policy, visit.visitedAt());
        return settle(policy, visit.visitedAt(), xpBefore, levelBefore);
    }

    /**
     * 본인 체크인 취소(VisitCancelled): 그 지역이 모든 지도에서 사라졌을 때만 기본 XP 회수. 그 밖은 유지(취소 비대칭). 멱등.
     * generation: 취소된 방문의 회차 — 더 새 회차를 이미 봤으면 무시(결정 6). 0 = 예전 이벤트.
     */
    public ProgressChange revokeVisit(String mapId, RegionCode region, int generation, Instant at, ProgressionPolicy policy) {
        long xpBefore = ledger.total();
        int levelBefore = level;
        if (regions.remove(region, mapId, generation)) {
            ledger.revokeRegion(explorerId, region, at);
        }
        return settle(policy, at, xpBefore, levelBefore);
    }

    /** 회차를 모르는(예전) 취소. */
    public ProgressChange revokeVisit(String mapId, RegionCode region, Instant at, ProgressionPolicy policy) {
        return revokeVisit(mapId, region, 0, at, policy);
    }

    /**
     * 선점 이전(ClaimTransferred) — 이 탐험가가 새 선점자가 됐다: 선점 보너스(refId claim:{mapId}:{code}:{e}, 지도마다·수령자마다
     * 1회 — §5). 액수는 체크인 보상 함수의 선점 줄. 떠난 사람 보너스는 건드리지 않는다.
     */
    public ProgressChange applyClaimTransferred(String mapId, RegionCode region, Rarity rarity, Instant at,
                                                ProgressionPolicy policy) {
        long xpBefore = ledger.total();
        int levelBefore = level;
        policy.rewards().checkIn(rarity, false, true).stream().filter(award -> award.source() == XpSource.FIRST_CLAIM)
            .forEach(award -> ledger.grantOnce(XpSource.FIRST_CLAIM, RefIds.claim(mapId, region, explorerId),
                award.amount(), at));
        return settle(policy, at, xpBefore, levelBefore);
    }

    /** 도감 테마(세트) 완성 보너스(SetCompleted). 탐험가당 테마당 1회. */
    public ProgressChange applyThemeCompleted(String themeId, Instant at, ProgressionPolicy policy) {
        long xpBefore = ledger.total();
        int levelBefore = level;
        ledger.grantOnce(XpSource.SET_COMPLETE, RefIds.theme(explorerId, themeId), policy.rewards().themeComplete(), at);
        return settle(policy, at, xpBefore, levelBefore);
    }

    /** 퀘스트 보상(QuestCompleted). 보드·퀘스트당 1회. */
    public ProgressChange applyQuestReward(QuestPeriod period, String questId, int xp, Instant at, ProgressionPolicy policy) {
        long xpBefore = ledger.total();
        int levelBefore = level;
        ledger.grantOnce(XpSource.QUEST, RefIds.quest(explorerId, period, questId), xp, at);
        earnFreezeForMonthlyQuests(period, policy, at);
        return settle(policy, at, xpBefore, levelBefore);
    }

    /**
     * 보호권을 받을 일 한 건 반영(재계산 재생용 — 장부에서 다시 만든 {@link FreezeGrant}). 같은 refId 는 한 번, 보유 상한까지만.
     */
    public void applyFreezeGrant(FreezeGrant grant, ProgressionPolicy policy) {
        freezes.earn(grant.refId(), grant.reason(), grant.amount(), policy.streakRules().freezeMaxHeld(), grant.at());
    }

    /**
     * 장부에 남은 보상에서 다시 만든 "보호권을 받을 일"(처리 시각 순): 받은 마일스톤마다 그 시각에, 월간 퀘스트를 모두 보상 받은 달마다
     * 마지막 보상 시각에. 재계산이 보호권 장부를 비운 뒤 체크인(소모)과 시간 순으로 섞어 다시 쌓는다 — 상한·소모가 순서에 달려 있어서.
     */
    public List<FreezeGrant> freezeGrantsOnRecord(ProgressionPolicy policy) {
        List<FreezeGrant> grants = new ArrayList<>();
        ledger.entriesOf(XpSource.STREAK_MILESTONE).forEach(entry -> {
            int months = Integer.parseInt(RefIds.subjectOf(entry.refId()));
            policy.streakRules().find(months).filter(milestone -> milestone.freezes() > 0).ifPresent(milestone -> grants.add(
                new FreezeGrant(RefIds.freezeFromMilestone(explorerId, months), FreezeReason.MILESTONE, milestone.freezes(),
                    entry.at())));
        });
        ledger.monthlyQuestsCompleted(policy.monthlyQuestIds()).forEach((period, completedAt) -> {
            if (policy.streakRules().monthlyQuestsFreezes() > 0) {
                grants.add(new FreezeGrant(RefIds.freezeFromQuests(explorerId, period), FreezeReason.MONTHLY_QUESTS,
                    policy.streakRules().monthlyQuestsFreezes(), completedAt));
            }
        });
        grants.sort(Comparator.comparing(FreezeGrant::at));
        return List.copyOf(grants);
    }

    /**
     * 재계산 복구 규칙(QA P1-2, Q2 승인): 완성 기록(이 탐험가가 완성 시점 멤버였던 것만 — 결정 1·R2-1)은 있는데 장부에 그 테마
     * 보너스가 없으면 지급하고, 보상을 받은(claimed)
     * 퀘스트인데 장부에 그 XP 가 없으면 지급한다. 칭호·뱃지는 settle 이 보정한다. refId 가 같아 멱등하다.
     */
    public ProgressChange recoverRewards(List<String> completedThemeIds, List<QuestXp> claimedRewards, Instant at,
                                         ProgressionPolicy policy) {
        long xpBefore = ledger.total();
        int levelBefore = level;
        completedThemeIds.forEach(themeId -> ledger.grantOnce(XpSource.SET_COMPLETE, RefIds.theme(explorerId, themeId),
            policy.rewards().themeComplete(), at));
        claimedRewards.forEach(reward -> ledger.grantOnce(XpSource.QUEST,
            RefIds.quest(explorerId, reward.period(), reward.questId()), reward.xp(), at));
        claimedRewards.forEach(reward -> earnFreezeForMonthlyQuests(reward.period(), policy, at));
        return settle(policy, at, xpBefore, levelBefore);
    }

    /** 칭호 선택. null 이면 선택 해제(레벨 칭호 표시). 얻은 칭호만 고를 수 있다. at = 처리 시각. */
    public void selectTitle(String titleId, ProgressionPolicy policy, Instant at) {
        if (titleId != null && !titleId.isBlank()) {
            if (!policy.titleRules().contains(titleId)) throw ProgressionError.TITLE_NOT_FOUND.exception(titleId);
            if (!titles.containsKey(titleId)) throw ProgressionError.TITLE_NOT_EARNED.exception(titleId);
        }
        selectedTitle = titleId == null || titleId.isBlank() ? null : titleId;
        updatedAt = at;
    }

    /**
     * 재계산(RecalculateService)용 출발점: 현재 방문으로만 정해지는 것(지금 멤버인 지도의 지역 활성·기본 XP·스트릭·보호권 장부)은 비우고,
     * 취소 비대칭·"추가만" 규칙에 묶인 것(시·도 첫 발·선점·세트·퀘스트 XP, 뱃지·칭호·선택 칭호, 지역의 처음 밟은 시각)과
     * 탈퇴한 지도로 남은 활성(§5 — 탈퇴는 줄이지 않음)은 유지한다. 재생은 빠진 것만 덧붙이므로 결과가 이벤트 누적과 같다.
     * 8단계 미스터리·시·도 정복·마일스톤 XP 도 회수 없는 보상이라 남긴다. 보호권은 소모가 스트릭(현재 방문)에 달려 있어 비우고, 재생이
     * 장부에 남은 보상({@link #freezeGrantsOnRecord})과 체크인을 시간 순으로 섞어 다시 쌓는다.
     * 저장은 호출자가 replace 로 통째로 바꾼다.
     *
     * @param currentMaps 탐험가가 지금 멤버인 지도 id
     */
    public ExplorerProgress rebuildBase(Set<String> currentMaps) {
        ExploredRegions base = regions.deactivated(currentMaps);
        return new ExplorerProgress(explorerId, ledger.withoutRegionBase(explorerId, base.activeCodes()), base,
            StreakFreezes.empty(), Streak.NONE, badges, titles, selectedTitle, level, updatedAt);
    }

    // ---- 판정 ----------------------------------------------------------------------------------------------

    private void grant(XpAward award, ProgressVisit visit) {
        switch (award.source()) {
            case REGION_BASE -> ledger.grantRegion(explorerId, visit.region(), award.amount(), visit.visitedAt());
            case PROVINCE_FIRST -> ledger.grantOnce(XpSource.PROVINCE_FIRST,
                RefIds.province(explorerId, visit.provinceCode()), award.amount(), visit.visitedAt());
            case FIRST_CLAIM -> ledger.grantOnce(XpSource.FIRST_CLAIM, RefIds.claim(visit.mapId(), visit.region(), explorerId),
                award.amount(), visit.visitedAt());
            case MYSTERY_BONUS -> visit.mysteryFound().ifPresent(mystery -> {
                if (ledger.grantOnce(XpSource.MYSTERY_BONUS, RefIds.mystery(explorerId, mystery.weekId()), award.amount(),
                    visit.visitedAt())) {
                    mysteryNow = mystery;
                }
            });
            default -> throw new IllegalArgumentException("체크인 보상이 아닌 출처: " + award.source());
        }
    }

    /** 그 달 체크인으로 스트릭을 잇는다 — 빈 달이 있으면 가진 보호권으로 메운다(모자라면 끊기고 쓰지 않는다). */
    private void recordStreak(YearMonth month, Instant at) {
        StreakStep step = streak.record(month, freezes.held());
        if (step.freezesUsed() > 0) {
            freezes.use(RefIds.freezeUse(explorerId, month), month, step.freezesUsed(), at);
            freezesUsedNow += step.freezesUsed();
        }
        streak = step.streak();
    }

    /** 지금 연속 개월로 닿은 마일스톤 중 처음인 것: XP(장부가 "받았음" 기록) + 보호권(상한 안). 끊겼다 다시 닿아도 다시 없음. */
    private void reachMilestones(ProgressionPolicy policy, Instant at) {
        policy.streakRules().reachedBy(streak.months()).forEach(milestone -> {
            if (ledger.grantOnce(XpSource.STREAK_MILESTONE, RefIds.milestone(explorerId, milestone.months()), milestone.xp(), at)) {
                reachedNow.add(milestone.months());
                freezes.earn(RefIds.freezeFromMilestone(explorerId, milestone.months()), FreezeReason.MILESTONE,
                    milestone.freezes(), policy.streakRules().freezeMaxHeld(), at);
            }
        });
    }

    /** 그 달의 월간 퀘스트를 모두 보상 받았으면 보호권(그 달 한 번, 상한 안). 상시 보드는 해당 없음. */
    private void earnFreezeForMonthlyQuests(QuestPeriod period, ProgressionPolicy policy, Instant at) {
        if (period.always() || !ledger.allQuestsRewarded(explorerId, period, policy.monthlyQuestIds())) return;
        freezes.earn(RefIds.freezeFromQuests(explorerId, period), FreezeReason.MONTHLY_QUESTS,
            policy.streakRules().monthlyQuestsFreezes(), policy.streakRules().freezeMaxHeld(), at);
    }

    /** 지금 칠한 지역으로 처음 정복한 시·도 — 정복 보상 XP(시·도당 한 번, 취소해도 회수 없음). */
    private void conquerProvinces(ProgressionPolicy policy, Instant at) {
        policy.provinceRoster().conqueredBy(regions.activeCodes()).forEach(province -> {
            if (ledger.grantOnce(XpSource.PROVINCE_CONQUEST, RefIds.conquest(explorerId, province),
                policy.rewards().provinceConquest(), at)) {
                conqueredNow.add(province);
            }
        });
    }

    private ProgressChange unchanged(long xpBefore, int levelBefore, Instant at) {
        return new ProgressChange(xpBefore, ledger.total(), Optional.empty(), List.of(), List.of(), at, List.of(), List.of(),
            Optional.empty(), 0);
    }

    /** 시·도 정복 → 레벨 재계산 → 칭호·뱃지 추가(회수 없음). 이번 커맨드의 8단계 사건을 결과에 담고 비운다. */
    private ProgressChange settle(ProgressionPolicy policy, Instant at, long xpBefore, int levelBefore) {
        conquerProvinces(policy, at);
        level = policy.curve().levelOf(ledger.total());
        updatedAt = at;
        List<String> newTitles = policy.titleRules().newlyEarned(titles.keySet(), title -> earned(title, policy)).stream()
            .map(TitleRule::id).toList();
        newTitles.forEach(titleId -> titles.put(titleId, at));
        List<String> newBadges = policy.badges().newlyEarned(badges.keySet(), badgeFacts(policy));
        newBadges.forEach(badgeId -> badges.put(badgeId, at));
        unsavedTitles.addAll(newTitles);
        unsavedBadges.addAll(newBadges);
        Optional<Integer> levelUp = level > levelBefore ? Optional.of(level) : Optional.empty();
        ProgressChange change = new ProgressChange(xpBefore, ledger.total(), levelUp, List.copyOf(newBadges),
            List.copyOf(newTitles), at, List.copyOf(reachedNow), List.copyOf(conqueredNow), Optional.ofNullable(mysteryNow),
            freezesUsedNow);
        reachedNow.clear();
        conqueredNow.clear();
        mysteryNow = null;
        freezesUsedNow = 0;
        return change;
    }

    private boolean earned(TitleRule title, ProgressionPolicy policy) {
        return switch (title.source()) {
            case LEVEL -> level >= Integer.parseInt(title.ref());
            case SET -> ledger.has(RefIds.theme(explorerId, title.ref()));
            case QUEST -> ledger.has(RefIds.quest(explorerId, QuestPeriod.ALL, title.ref()));
            case PROVINCE -> policy.provinceRoster().conquered(title.ref(), regions.activeCodes());
            case STREAK -> ledger.has(RefIds.milestone(explorerId, Integer.parseInt(title.ref())));
        };
    }

    private BadgeFacts badgeFacts(ProgressionPolicy policy) {
        return new BadgeFacts(regions.activeCount(), regions.count(Rarity.LEGEND), regions.perProvince(),
            policy.provinceTotals(), Set.copyOf(policy.provinceRoster().conqueredBy(regions.activeCodes())),
            policy.totalRegions(), ledger.count(XpSource.SET_COMPLETE), streak.months(), ledger.count(XpSource.MYSTERY_BONUS));
    }

    // ---- 조회 ----------------------------------------------------------------------------------------------

    public ExplorerId explorerId() { return explorerId; }
    public long xp() { return ledger.total(); }
    public int level() { return level; }
    public Streak streak() { return streak; }
    public XpLedger ledger() { return ledger; }
    public ExploredRegions regions() { return regions; }
    public StreakFreezes freezes() { return freezes; }

    /** 지금 보이는 연속 개월(이번 달에 칠하면 빈 달을 가진 보호권으로 메울 수 있으면 유지 — 8단계). */
    public int streakMonthsAsOf(YearMonth current) {
        return streak.asOf(current, freezes.held());
    }

    /**
     * 지금 보이는 연속 구간 안에서 보호권으로 메운 달 전부(오래된 순, 8단계). 연속이 끊겨 0으로 보이면 비어 있다.
     * 이번 달에 칠하면 쓰게 될 보호권(아직 쓰지 않은 빈 달)은 들어가지 않는다.
     */
    public List<YearMonth> frozenMonthsAsOf(YearMonth current) {
        if (streakMonthsAsOf(current) == 0) return List.of();
        return freezes.bridgedWithin(streak);
    }

    /** 받은 연속 탐험 마일스톤(개월 수 → 받은 시각, 오름차순 — 8단계). */
    public Map<Integer, Instant> milestonesReached() {
        Map<Integer, Instant> reached = new TreeMap<>();
        ledger.entriesOf(XpSource.STREAK_MILESTONE)
            .forEach(entry -> reached.put(Integer.parseInt(RefIds.subjectOf(entry.refId())), entry.at()));
        return reached;
    }

    /** 정복 보상을 받은 시·도(시·도 코드 → 정복 시각 — 8단계). */
    public Map<String, Instant> provincesConquered() {
        Map<String, Instant> conquered = new LinkedHashMap<>();
        ledger.entriesOf(XpSource.PROVINCE_CONQUEST).forEach(entry -> conquered.put(RefIds.subjectOf(entry.refId()), entry.at()));
        return conquered;
    }

    /** 연속 탐험 마일스톤별 내 현황(오름차순 — 받았는지·더 필요한 개월 수, 8단계). current = 지금 달. */
    public List<MilestoneStatus> milestoneStatus(ProgressionPolicy policy, YearMonth current) {
        Map<Integer, Instant> reached = milestonesReached();
        int months = streakMonthsAsOf(current);
        return policy.streakRules().milestones().stream().map(milestone -> new MilestoneStatus(milestone.months(),
            milestone.xp(), milestone.freezes(), policy.titleRules().forStreak(milestone.months()).map(TitleRule::id).orElse(null),
            reached.get(milestone.months()),
            reached.containsKey(milestone.months()) ? 0 : Math.max(1, milestone.months() - months))).toList();
    }

    /** 아직 받지 않은 가장 가까운 마일스톤(모두 받았으면 빈 값 — 8단계). */
    public Optional<MilestoneStatus> nextMilestone(ProgressionPolicy policy, YearMonth current) {
        return milestoneStatus(policy, current).stream().filter(status -> !status.reached()).findFirst();
    }

    /** 이번 달(current) 월간 퀘스트로 받는 보호권 진행 — 받은 보상 수·필요 수·이번 달 몫을 받았는지·실제로 늘어난 수(8단계). */
    public MonthlyFreezeProgress monthlyFreezeProgress(ProgressionPolicy policy, YearMonth current) {
        QuestPeriod period = QuestPeriod.of(current);
        int rewarded = ledger.questsRewarded(explorerId, period, policy.monthlyQuestIds());
        String refId = RefIds.freezeFromQuests(explorerId, period);
        return new MonthlyFreezeProgress(period.value(), rewarded, policy.monthlyQuestIds().size(),
            policy.streakRules().monthlyQuestsFreezes(), freezes.has(refId), freezes.amountOf(refId));
    }

    /** 시·도별 칠하기 현황(표시 순서 — 현행 지역 기준, 정복 기록 포함, 8단계). */
    public List<ProvinceCoverage> provinceCoverage(ProgressionPolicy policy) {
        Set<RegionCode> active = regions.activeCodes();
        Map<String, Instant> conquered = provincesConquered();
        return policy.provinceRoster().provinces().stream().map(province -> new ProvinceCoverage(province,
            policy.provinceRoster().covered(province, active), policy.provinceRoster().total(province),
            policy.provinceRoster().conquered(province, active), conquered.get(province))).toList();
    }

    /** 이 주(weekId)의 미스터리 보너스를 받았으면 그 시각(8단계). */
    public Optional<Instant> mysteryFoundAt(String weekId) {
        return ledger.find(RefIds.mystery(explorerId, weekId)).map(XpLedgerEntry::at);
    }

    /** 미스터리 보너스를 받은 주 수(8단계). */
    public int mysteryFoundCount() {
        return ledger.count(XpSource.MYSTERY_BONUS);
    }
    public Map<String, Instant> badges() { return Collections.unmodifiableMap(badges); }
    public Map<String, Instant> titles() { return Collections.unmodifiableMap(titles); }
    public Optional<String> selectedTitle() { return Optional.ofNullable(selectedTitle); }

    /** 마지막으로 바뀐 처리 시각(explorer_progress.updated_at). */
    public Instant updatedAt() { return updatedAt; }

    /** 복원 이후 새로 얻은 뱃지·칭호 id(저장소가 이것만 추가한다). */
    public List<String> unsavedBadges() { return List.copyOf(unsavedBadges); }
    public List<String> unsavedTitles() { return List.copyOf(unsavedTitles); }

    /** 화면에 보일 칭호 id: 선택한 칭호, 없으면 레벨 칭호. */
    public String displayTitle(ProgressionPolicy policy) {
        return selectedTitle().orElseGet(() -> levelTitle(policy));
    }

    /** 현재 레벨 이하에서 가장 높은 레벨 칭호 id. */
    public String levelTitle(ProgressionPolicy policy) {
        return policy.titleRules().levelTitleFor(level).map(TitleRule::id).orElse(null);
    }
}
