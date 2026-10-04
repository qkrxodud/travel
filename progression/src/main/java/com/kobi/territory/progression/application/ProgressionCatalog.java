package com.kobi.territory.progression.application;

import com.kobi.territory.catalog.api.query.ProgressionRules;
import com.kobi.territory.catalog.api.query.MysteryRegionQuery;
import com.kobi.territory.catalog.api.query.MysteryWeekView;
import com.kobi.territory.catalog.api.query.ProgressionRules.BadgeConditionView;
import com.kobi.territory.catalog.api.query.ProgressionRules.QuestView;
import com.kobi.territory.catalog.api.query.ProgressionRules.StreakRulesView;
import com.kobi.territory.catalog.api.query.RegionCatalog;
import com.kobi.territory.catalog.api.query.RewardCalculator;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.progression.domain.policy.Badge;
import com.kobi.territory.progression.domain.policy.BadgeRule;
import com.kobi.territory.progression.domain.policy.Badges;
import com.kobi.territory.progression.domain.policy.LevelCurve;
import com.kobi.territory.progression.domain.policy.ProgressionPolicy;
import com.kobi.territory.progression.domain.policy.ProvinceRoster;
import com.kobi.territory.progression.domain.policy.StreakMilestone;
import com.kobi.territory.progression.domain.policy.StreakRules;
import com.kobi.territory.progression.domain.progress.MysteryFact;
import com.kobi.territory.progression.domain.quest.QuestRule;
import com.kobi.territory.progression.domain.quest.QuestRules;
import com.kobi.territory.progression.domain.collectionbook.Season;
import com.kobi.territory.progression.domain.collectionbook.SeasonCalendar;
import com.kobi.territory.progression.domain.collectionbook.Theme;
import com.kobi.territory.progression.domain.collectionbook.Themes;
import com.kobi.territory.progression.domain.policy.TitleRule;
import com.kobi.territory.progression.domain.policy.TitleRules;
import com.kobi.territory.progression.domain.policy.XpAward;
import com.kobi.territory.progression.domain.policy.XpRewards;
import com.kobi.territory.progression.domain.policy.XpSource;
import java.time.Clock;
import java.time.Instant;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.Optional;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 카탈로그(상류) Query → 진행 도메인 정책 VO 변환 어댑터(Anti-Corruption Layer). 시작 시 한 번 조립한다.
 * 보상 계산은 카탈로그 공개 함수(RewardCalculator)에 위임한다 — 체크인 미리보기와 같은 함수(D1).
 */
@Component
public class ProgressionCatalog implements XpRewards {

    /** 카탈로그 퀘스트 범위 값 중 월간(공개 계약 값). */
    private static final String MONTHLY = "MONTHLY";

    private final RewardCalculator rewards;
    private final MysteryRegionQuery mysteries;
    private final Clock clock;
    private final ProgressionPolicy policy;
    private final Themes themes;
    private final QuestRules questRules;
    private final SeasonCalendar seasonCalendar;

    public ProgressionCatalog(ProgressionRules rules, RewardCalculator rewards, RegionCatalog regions,
                              MysteryRegionQuery mysteries, Clock clock) {
        this.rewards = rewards;
        this.mysteries = mysteries;
        this.clock = clock;
        Map<String, List<RegionCode>> currentRegions = new LinkedHashMap<>();
        regions.provinces().forEach(province -> currentRegions.put(province.code(), new ArrayList<>()));
        regions.activeRegions().forEach(region -> currentRegions.computeIfAbsent(region.provinceCode(), key -> new ArrayList<>())
            .add(RegionCode.of(region.code())));
        StreakRulesView streak = rules.streakRules();
        this.policy = new ProgressionPolicy(LevelCurve.withDivisor(rules.levelDivisor()), this,
            new Badges(rules.badges().stream().map(badge -> new Badge(badge.id(), badgeRule(badge.condition()))).toList()),
            new TitleRules(rules.titles().stream()
                .map(title -> new TitleRule(title.id(), TitleRule.Source.valueOf(title.source()), title.ref())).toList()),
            ProvinceRoster.of(currentRegions), regions.activeRegions().size(), clock.getZone(),
            new StreakRules(streak.freezeMaxHeld(), streak.monthlyQuestsFreezes(), streak.milestones().stream()
                .map(milestone -> new StreakMilestone(milestone.months(), milestone.xp(), milestone.freezes())).toList()),
            rules.quests().stream().filter(quest -> MONTHLY.equals(quest.scope())).map(QuestView::id).collect(Collectors.toSet()));
        this.themes = new Themes(rules.sets().stream()
            .map(setView -> new Theme(setView.id(),
                setView.regionCodes().stream().map(RegionCode::of).collect(Collectors.toSet())))
            .toList());
        this.seasonCalendar = SeasonCalendar.of(rules.seasons().stream()
            .map(season -> new Season(season.id(), MonthDay.parse("--" + season.start()), MonthDay.parse("--" + season.end()),
                season.regionCodes().stream().map(RegionCode::of).collect(Collectors.toSet())))
            .toList(), clock.getZone());
        this.questRules = new QuestRules(rules.quests().stream()
            .map(quest -> new QuestRule(quest.id(), QuestRule.Scope.valueOf(quest.scope()),
                QuestRule.Metric.valueOf(quest.metric()), quest.param(), quest.target(), quest.xp()))
            .toList());
    }

    /** 카탈로그 뱃지 조건 선언 → 도메인 판정 규칙(표현 변환). */
    private static BadgeRule badgeRule(BadgeConditionView condition) {
        return switch (condition.type()) {
            case "REGION_COUNT" -> new BadgeRule.RegionCount(condition.min());
            case "LEGEND_COUNT" -> new BadgeRule.LegendCount(condition.min());
            case "PROVINCES_COMPLETE" -> new BadgeRule.ProvincesComplete(condition.provinces());
            case "PROVINCE_GROUPS_TOUCHED" -> new BadgeRule.ProvinceGroupsTouched(condition.groups());
            case "ALL_PROVINCES_TOUCHED" -> new BadgeRule.AllProvincesTouched();
            case "SETS_COMPLETED" -> new BadgeRule.ThemesCompleted(condition.min());
            case "STREAK_MONTHS" -> new BadgeRule.StreakMonths(condition.min());
            case "CONQUEST_RATIO" -> new BadgeRule.ConquestRatio(condition.ratio());
            case "MYSTERY_FOUND" -> new BadgeRule.MysteryFound(condition.min());
            case "REVISIT_STAMPS" -> new BadgeRule.RevisitStamps(condition.min());
            case "WISHES_FULFILLED" -> new BadgeRule.WishesFulfilled(condition.min());
            default -> throw new IllegalStateException("모르는 뱃지 조건: " + condition.type());
        };
    }

    @Override
    public List<XpAward> checkIn(Rarity rarity, boolean firstInProvince, boolean firstClaim) {
        return rewards.checkIn(rarity, firstInProvince, firstClaim).stream()
            .map(line -> new XpAward(XpSource.valueOf(line.source()), line.amount())).toList();
    }

    @Override
    public List<XpAward> checkIn(Rarity rarity, boolean firstInProvince, boolean firstClaim, boolean mysteryOfWeek) {
        return rewards.checkIn(rarity, firstInProvince, firstClaim, mysteryOfWeek).stream()
            .map(line -> new XpAward(XpSource.valueOf(line.source()), line.amount())).toList();
    }

    @Override
    public int themeComplete() {
        return rewards.setComplete().amount();
    }

    @Override
    public int provinceConquest() {
        return rewards.provinceConquest().amount();
    }

    @Override
    public int seasonComplete() {
        return rewards.seasonComplete().amount();
    }

    @Override
    public int revisitStamp() {
        return rewards.revisitStamp().amount();
    }

    @Override
    public int wishFulfilled() {
        return rewards.wishFulfilled().amount();
    }

    /** 계절 한정 테마 달력(9단계). */
    public SeasonCalendar seasonCalendar() {
        return seasonCalendar;
    }

    /** 이번 주 미스터리 지역 보너스 XP. */
    public int mysteryBonus() {
        return rewards.mysteryBonus().amount();
    }

    /**
     * 처리 시각 at 이 속한 주의 미스터리 지역(8단계). 그 주 기록이 없으면(지난 주 — 소급 없음) 비어 있다. 지금 주면 카탈로그가 골라 기록한다.
     */
    public Optional<MysteryFact> mysteryOf(Instant at) {
        return mysteries.weekOf(at).map(week -> new MysteryFact(week.weekId(), RegionCode.of(week.regionCode())));
    }

    /** 이번 주 미스터리 지역(없으면 카탈로그가 골라 기록) — GET /mystery/this-week. */
    public MysteryWeekView mysteryThisWeek() {
        return mysteries.thisWeek();
    }

    public ProgressionPolicy policy() {
        return policy;
    }

    public Themes themes() {
        return themes;
    }

    public QuestRules questRules() {
        return questRules;
    }

    /** 지금 달(시간대 기준) — 월간 보드·스트릭 표시 기준. */
    public YearMonth currentMonth() {
        return YearMonth.now(clock);
    }
}
