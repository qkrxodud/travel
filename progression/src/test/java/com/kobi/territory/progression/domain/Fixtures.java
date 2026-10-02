package com.kobi.territory.progression.domain;

import com.kobi.territory.progression.domain.collectionbook.Theme;
import com.kobi.territory.progression.domain.collectionbook.Themes;
import com.kobi.territory.progression.domain.policy.Badge;
import com.kobi.territory.progression.domain.policy.BadgeRule;
import com.kobi.territory.progression.domain.policy.Badges;
import com.kobi.territory.progression.domain.policy.LevelCurve;
import com.kobi.territory.progression.domain.policy.ProgressionPolicy;
import com.kobi.territory.progression.domain.policy.TitleRule;
import com.kobi.territory.progression.domain.policy.TitleRules;
import com.kobi.territory.progression.domain.policy.XpAward;
import com.kobi.territory.progression.domain.policy.XpRewards;
import com.kobi.territory.progression.domain.progress.ProgressVisit;
import com.kobi.territory.progression.domain.policy.XpSource;
import com.kobi.territory.progression.domain.quest.QuestFact;
import com.kobi.territory.progression.domain.quest.QuestRule;
import com.kobi.territory.progression.domain.quest.QuestRules;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.common.model.RegionCode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 진행 도메인 단위 테스트 픽스처(Spring 없음). 값은 카탈로그 정의와 같게 둔다(실제 정의는 catalog 테스트가 검증). */
public final class Fixtures {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /** 2026-10-02 12:00 KST */
    public static final Instant T0 = Instant.parse("2026-10-02T03:00:00Z");
    public static final ExplorerId ME = ExplorerId.of("11111111-1111-1111-1111-111111111111");
    public static final ExplorerId FRIEND = ExplorerId.of("22222222-2222-2222-2222-222222222222");
    public static final String MAP = "33333333-3333-3333-3333-333333333333";
    public static final String MAP2 = "44444444-4444-4444-4444-444444444444";

    /** 카탈로그 RewardRules 와 같은 규칙(기본 10/20/50, 시·도 첫 발 15, 선점 10, 세트 100). */
    public static final XpRewards REWARDS = new XpRewards() {
        @Override
        public List<XpAward> checkIn(Rarity rarity, boolean firstInProvince, boolean firstClaim) {
            List<XpAward> awards = new ArrayList<>();
            awards.add(new XpAward(XpSource.REGION_BASE, Map.of(Rarity.COMMON, 10, Rarity.RARE, 20, Rarity.LEGEND, 50).get(rarity)));
            if (firstInProvince) awards.add(new XpAward(XpSource.PROVINCE_FIRST, 15));
            if (firstClaim) awards.add(new XpAward(XpSource.FIRST_CLAIM, 10));
            return awards;
        }

        @Override
        public int themeComplete() {
            return 100;
        }
    };

    /** 서울 2곳·경기 1곳짜리 미니 카탈로그. */
    public static final Map<String, Integer> PROVINCE_TOTALS = Map.of("KR-11", 2, "KR-31", 1);

    public static final Badges BADGES = new Badges(List.of(
        new Badge("first", new BadgeRule.RegionCount(1)),
        new Badge("seoul", new BadgeRule.ProvincesComplete(List.of("KR-11"))),
        new Badge("legend", new BadgeRule.LegendCount(1)),
        new Badge("allprov", new BadgeRule.AllProvincesTouched()),
        new Badge("set1", new BadgeRule.ThemesCompleted(1)),
        new Badge("streak3", new BadgeRule.StreakMonths(3)),
        new Badge("half", new BadgeRule.ConquestRatio(0.5))));

    public static final TitleRules TITLE_RULES = new TitleRules(List.of(
        new TitleRule("lv1", TitleRule.Source.LEVEL, "1"),
        new TitleRule("lv3", TitleRule.Source.LEVEL, "3"),
        new TitleRule("set-han", TitleRule.Source.SET, "han"),
        new TitleRule("long-leg5", TitleRule.Source.QUEST, "leg5"),
        new TitleRule("own-KR-11", TitleRule.Source.PROVINCE, "KR-11")));

    public static final ProgressionPolicy POLICY = new ProgressionPolicy(LevelCurve.withDivisor(5), REWARDS, BADGES, TITLE_RULES,
        PROVINCE_TOTALS, 3, KST);

    public static final RegionCode JONGNO = RegionCode.of("KR-11010");
    public static final RegionCode JUNG = RegionCode.of("KR-11020");
    public static final RegionCode GAPYEONG = RegionCode.of("KR-31370");
    public static final RegionCode ULLEUNG = RegionCode.of("KR-37430");

    public static final Themes THEMES = new Themes(List.of(
        new Theme("han", Set.of(JONGNO, JUNG)),
        new Theme("mix", Set.of(JUNG, GAPYEONG))));

    public static final QuestRules QUEST_RULES = new QuestRules(List.of(
        new QuestRule("m3", QuestRule.Scope.MONTHLY, QuestRule.Metric.NEW_REGIONS, 0, 3, 60),
        new QuestRule("mgun", QuestRule.Scope.MONTHLY, QuestRule.Metric.NON_COMMON_REGIONS, 0, 1, 40),
        new QuestRule("mprov", QuestRule.Scope.MONTHLY, QuestRule.Metric.FIRST_IN_PROVINCE, 0, 1, 80),
        new QuestRule("mset", QuestRule.Scope.MONTHLY, QuestRule.Metric.SET_REGIONS, 0, 2, 50),
        new QuestRule("leg5", QuestRule.Scope.ALWAYS, QuestRule.Metric.LEGEND_REGIONS, 0, 5, 150),
        new QuestRule("p3", QuestRule.Scope.ALWAYS, QuestRule.Metric.PROVINCES_WITH_MIN_REGIONS, 2, 2, 200)));

    private Fixtures() {}

    public static String province(RegionCode code) {
        return "KR-" + code.value().substring(3, 5);
    }

    public static Rarity rarity(RegionCode code) {
        return code.equals(ULLEUNG) ? Rarity.LEGEND : code.equals(GAPYEONG) ? Rarity.RARE : Rarity.COMMON;
    }

    public static ProgressVisit visit(String map, RegionCode code, Instant at, boolean firstClaim) {
        return new ProgressVisit(map, code, province(code), rarity(code), at, firstClaim);
    }

    public static ProgressVisit visit(RegionCode code, Instant at) {
        return visit(MAP, code, at, true);
    }

    public static QuestFact fact(RegionCode code, boolean firstInProvince) {
        return new QuestFact(code, province(code), rarity(code), THEMES.includeAny(code), firstInProvince);
    }
}
