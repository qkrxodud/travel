package com.kobi.territory.progression.domain;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.common.model.RegionCode;
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
import com.kobi.territory.progression.domain.policy.XpSource;
import com.kobi.territory.progression.domain.progress.ExplorerProgress;
import com.kobi.territory.progression.domain.progress.ProgressChange;
import com.kobi.territory.progression.domain.progress.ProgressVisit;
import com.kobi.territory.progression.domain.quest.QuestFact;
import com.kobi.territory.progression.domain.quest.QuestRule;
import com.kobi.territory.progression.domain.quest.QuestRules;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 진행 도메인 단위 테스트의 작은 세계(Spring 없음). 값은 카탈로그 정의와 같게 둔다(실제 정의는 catalog 테스트가 검증).
 *
 * <ul>
 *   <li>지역: 서울 종로구·중구(일반), 경기 가평군(희귀), 경북 울릉군(전설). 미니 카탈로그의 시·도 합계는 서울 2곳·경기 1곳.</li>
 *   <li>테마: "han" = 종로구 + 중구, "mix" = 중구 + 가평군.</li>
 *   <li>보상: 기본 10/20/50, 시·도 첫 발 15, 선점 10, 테마 완성 100. 레벨 L 하한 = 20·L·(L−1).</li>
 * </ul>
 */
public final class Fixtures {

    public static final ZoneId 서울시각 = ZoneId.of("Asia/Seoul");
    /** 2026-10-02 12:00 KST — 모든 이야기의 기준 시각. */
    public static final Instant 기준시각 = Instant.parse("2026-10-02T03:00:00Z");

    public static final ExplorerId 나 = ExplorerId.of("11111111-1111-1111-1111-111111111111");
    public static final ExplorerId 친구 = ExplorerId.of("22222222-2222-2222-2222-222222222222");
    /** 테마가 완성된 뒤에 지도에 들어온 멤버. */
    public static final ExplorerId 늦게온멤버 = ExplorerId.of("55555555-5555-5555-5555-555555555555");

    public static final String 지도 = "33333333-3333-3333-3333-333333333333";
    public static final String 다른지도 = "44444444-4444-4444-4444-444444444444";

    public static final RegionCode 종로구 = RegionCode.of("KR-11010");
    public static final RegionCode 중구 = RegionCode.of("KR-11020");
    public static final RegionCode 가평군 = RegionCode.of("KR-31370");
    public static final RegionCode 울릉군 = RegionCode.of("KR-37430");

    /** 카탈로그 RewardRules 와 같은 규칙(기본 10/20/50, 시·도 첫 발 15, 선점 10, 테마 완성 100). */
    public static final XpRewards 보상규칙 = new XpRewards() {
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
    public static final Map<String, Integer> 시도별지역수 = Map.of("KR-11", 2, "KR-31", 1);

    public static final Badges 뱃지 = new Badges(List.of(
        new Badge("first", new BadgeRule.RegionCount(1)),
        new Badge("seoul", new BadgeRule.ProvincesComplete(List.of("KR-11"))),
        new Badge("legend", new BadgeRule.LegendCount(1)),
        new Badge("allprov", new BadgeRule.AllProvincesTouched()),
        new Badge("set1", new BadgeRule.ThemesCompleted(1)),
        new Badge("streak3", new BadgeRule.StreakMonths(3)),
        new Badge("half", new BadgeRule.ConquestRatio(0.5))));

    public static final TitleRules 칭호 = new TitleRules(List.of(
        new TitleRule("lv1", TitleRule.Source.LEVEL, "1"),
        new TitleRule("lv3", TitleRule.Source.LEVEL, "3"),
        new TitleRule("set-han", TitleRule.Source.SET, "han"),
        new TitleRule("long-leg5", TitleRule.Source.QUEST, "leg5"),
        new TitleRule("own-KR-11", TitleRule.Source.PROVINCE, "KR-11")));

    public static final ProgressionPolicy 진행규칙 = new ProgressionPolicy(LevelCurve.withDivisor(5), 보상규칙, 뱃지, 칭호,
        시도별지역수, 3, 서울시각);

    public static final Themes 테마 = new Themes(List.of(
        new Theme("han", Set.of(종로구, 중구)),
        new Theme("mix", Set.of(중구, 가평군))));

    /** 월간 4개(m3 새 지역 3곳, mgun 일반 아닌 지역 1곳, mprov 처음 가는 시·도 1곳, mset 테마 지역 2곳), 상시 2개(leg5 전설 5곳, p3 2곳 이상 칠한 시·도 2개). */
    public static final QuestRules 퀘스트 = new QuestRules(List.of(
        new QuestRule("m3", QuestRule.Scope.MONTHLY, QuestRule.Metric.NEW_REGIONS, 0, 3, 60),
        new QuestRule("mgun", QuestRule.Scope.MONTHLY, QuestRule.Metric.NON_COMMON_REGIONS, 0, 1, 40),
        new QuestRule("mprov", QuestRule.Scope.MONTHLY, QuestRule.Metric.FIRST_IN_PROVINCE, 0, 1, 80),
        new QuestRule("mset", QuestRule.Scope.MONTHLY, QuestRule.Metric.SET_REGIONS, 0, 2, 50),
        new QuestRule("leg5", QuestRule.Scope.ALWAYS, QuestRule.Metric.LEGEND_REGIONS, 0, 5, 150),
        new QuestRule("p3", QuestRule.Scope.ALWAYS, QuestRule.Metric.PROVINCES_WITH_MIN_REGIONS, 2, 2, 200)));

    private Fixtures() {}

    /** 기준 시각 n초 뒤. */
    public static Instant 초(int seconds) {
        return 기준시각.plusSeconds(seconds);
    }

    public static String 시도(RegionCode code) {
        return "KR-" + code.value().substring(3, 5);
    }

    public static Rarity 희귀도(RegionCode code) {
        return code.equals(울릉군) ? Rarity.LEGEND : code.equals(가평군) ? Rarity.RARE : Rarity.COMMON;
    }

    // ---- 탐험가 진행 -------------------------------------------------------------------------------------------

    /** 처음 보는 탐험가(나)의 진행. */
    public static ExplorerProgress 새_진행() {
        return ExplorerProgress.start(나, 진행규칙, 기준시각);
    }

    public static ExplorerProgress 새_진행(ExplorerId who) {
        return ExplorerProgress.start(who, 진행규칙, 기준시각);
    }

    /** 내 체크인 한 건(기본: 지도, 기준 시각, 그 지도의 선점, 회차 모름). */
    public static 방문 방문(RegionCode code) {
        return new 방문(지도, code, 기준시각, true, 0);
    }

    public static ProgressChange 칠한다(ExplorerProgress progress, 방문 visit) {
        return progress.applyVisit(visit.사실(), 진행규칙);
    }

    public static ProgressChange 취소한다(ExplorerProgress progress, 방문 visit) {
        return progress.revokeVisit(visit.map(), visit.code(), visit.generation(), visit.at(), 진행규칙);
    }

    /** 체크인 사실을 문장처럼 만드는 빌더: {@code 방문(종로구).지도(다른지도).처리시각(초(2)).회차(1)}. */
    public record 방문(String map, RegionCode code, Instant at, boolean firstClaim, int generation) {

        public 방문 지도(String mapId) {
            return new 방문(mapId, code, at, firstClaim, generation);
        }

        public 방문 처리시각(Instant processedAt) {
            return new 방문(map, code, processedAt, firstClaim, generation);
        }

        /** 지도 안에서 내가 먼저 칠한 사람이 아니다. */
        public 방문 선점아님() {
            return new 방문(map, code, at, false, generation);
        }

        public 방문 회차(int n) {
            return new 방문(map, code, at, firstClaim, n);
        }

        public ProgressVisit 사실() {
            return new ProgressVisit(map, code, 시도(code), 희귀도(code), at, firstClaim, generation);
        }
    }

    // ---- 퀘스트 -------------------------------------------------------------------------------------------------

    public static QuestFact 퀘스트사실(RegionCode code, boolean firstInProvince) {
        return new QuestFact(code, 시도(code), 희귀도(code), 테마.includeAny(code), firstInProvince);
    }
}
