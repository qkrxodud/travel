package com.kobi.territory.progression.application;

import com.kobi.territory.catalog.api.query.ProgressionRules;
import com.kobi.territory.catalog.api.query.ProgressionRules.BadgeConditionView;
import com.kobi.territory.catalog.api.query.ProvinceView;
import com.kobi.territory.catalog.api.query.RegionCatalog;
import com.kobi.territory.catalog.api.query.RewardCalculator;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.progression.domain.policy.Badge;
import com.kobi.territory.progression.domain.policy.BadgeRule;
import com.kobi.territory.progression.domain.policy.Badges;
import com.kobi.territory.progression.domain.policy.LevelCurve;
import com.kobi.territory.progression.domain.policy.ProgressionPolicy;
import com.kobi.territory.progression.domain.quest.QuestRule;
import com.kobi.territory.progression.domain.quest.QuestRules;
import com.kobi.territory.progression.domain.collectionbook.Theme;
import com.kobi.territory.progression.domain.collectionbook.Themes;
import com.kobi.territory.progression.domain.policy.TitleRule;
import com.kobi.territory.progression.domain.policy.TitleRules;
import com.kobi.territory.progression.domain.policy.XpAward;
import com.kobi.territory.progression.domain.policy.XpRewards;
import com.kobi.territory.progression.domain.policy.XpSource;
import java.time.Clock;
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

    private final RewardCalculator rewards;
    private final Clock clock;
    private final ProgressionPolicy policy;
    private final Themes themes;
    private final QuestRules questRules;

    public ProgressionCatalog(ProgressionRules rules, RewardCalculator rewards, RegionCatalog regions, Clock clock) {
        this.rewards = rewards;
        this.clock = clock;
        Map<String, Integer> provinceTotals = regions.provinces().stream()
            .collect(Collectors.toMap(ProvinceView::code, ProvinceView::regionCount, (first, second) -> first, LinkedHashMap::new));
        this.policy = new ProgressionPolicy(LevelCurve.withDivisor(rules.levelDivisor()), this,
            new Badges(rules.badges().stream().map(badge -> new Badge(badge.id(), badgeRule(badge.condition()))).toList()),
            new TitleRules(rules.titles().stream()
                .map(title -> new TitleRule(title.id(), TitleRule.Source.valueOf(title.source()), title.ref())).toList()),
            provinceTotals, regions.activeRegions().size(), clock.getZone());
        this.themes = new Themes(rules.sets().stream()
            .map(setView -> new Theme(setView.id(),
                setView.regionCodes().stream().map(RegionCode::of).collect(Collectors.toSet())))
            .toList());
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
            default -> throw new IllegalStateException("모르는 뱃지 조건: " + condition.type());
        };
    }

    @Override
    public List<XpAward> checkIn(Rarity rarity, boolean firstInProvince, boolean firstClaim) {
        return rewards.checkIn(rarity, firstInProvince, firstClaim).stream()
            .map(line -> new XpAward(XpSource.valueOf(line.source()), line.amount())).toList();
    }

    @Override
    public int themeComplete() {
        return rewards.setComplete().amount();
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
