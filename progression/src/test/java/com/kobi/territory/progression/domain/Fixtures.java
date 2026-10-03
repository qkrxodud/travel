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
import com.kobi.territory.progression.domain.policy.ProvinceRoster;
import com.kobi.territory.progression.domain.policy.StreakMilestone;
import com.kobi.territory.progression.domain.policy.StreakRules;
import com.kobi.territory.progression.domain.policy.TitleRule;
import com.kobi.territory.progression.domain.policy.TitleRules;
import com.kobi.territory.progression.domain.policy.XpAward;
import com.kobi.territory.progression.domain.policy.XpRewards;
import com.kobi.territory.progression.domain.policy.XpSource;
import com.kobi.territory.progression.domain.progress.ExplorerProgress;
import com.kobi.territory.progression.domain.progress.MysteryFact;
import com.kobi.territory.progression.domain.progress.ProgressChange;
import com.kobi.territory.progression.domain.progress.ProgressVisit;
import com.kobi.territory.progression.domain.quest.QuestFact;
import com.kobi.territory.progression.domain.quest.QuestPeriod;
import com.kobi.territory.progression.domain.quest.QuestRule;
import com.kobi.territory.progression.domain.quest.QuestRules;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 진행 도메인 단위 테스트의 작은 세계(Spring 없음). 값은 카탈로그 정의와 같게 둔다(실제 정의는 catalog 테스트가 검증).
 *
 * <ul>
 *   <li>지역: 서울 종로구·중구·용산구(일반), 경기 가평군(희귀)·수원시(일반), 경북 울릉군(전설). 미니 카탈로그의 시·도 합계는 서울 3곳·경기 2곳,
 *       전국 5곳.</li>
 *   <li>테마: "han" = 종로구 + 중구, "mix" = 중구 + 가평군.</li>
 *   <li>보상: 기본 10/20/50, 시·도 첫 발 15, 선점 10, 테마 완성 100, 이번 주 미스터리 50, 시·도 정복 300. 레벨 L 하한 = 20·L·(L−1).</li>
 *   <li>8단계: 시·도 현행 지역 명부 = 서울 {종로구, 중구, 용산구}, 경기 {가평군, 수원시} — 서울에는 폐지된 옛 지역(옛서울구)도 있지만
 *       명부에 없다.
 *       보호권 최대 2개·월간 퀘스트 완주 1개, 마일스톤 3·6·12·24개월(XP 50·100·200·400, 보호권 1).</li>
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
    public static final RegionCode 용산구 = RegionCode.of("KR-11030");
    public static final RegionCode 가평군 = RegionCode.of("KR-31370");
    public static final RegionCode 수원시 = RegionCode.of("KR-31010");
    public static final RegionCode 울릉군 = RegionCode.of("KR-37430");
    /** 행정구역 개편으로 폐지된 서울의 옛 지역(현행 명부에 없다). */
    public static final RegionCode 옛서울구 = RegionCode.of("KR-11990");

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
        public List<XpAward> checkIn(Rarity rarity, boolean firstInProvince, boolean firstClaim, boolean mysteryOfWeek) {
            List<XpAward> awards = new ArrayList<>(checkIn(rarity, firstInProvince, firstClaim));
            if (mysteryOfWeek) awards.add(new XpAward(XpSource.MYSTERY_BONUS, 50));
            return awards;
        }

        @Override
        public int themeComplete() {
            return 100;
        }

        @Override
        public int provinceConquest() {
            return 300;
        }
    };

    /** 서울 3곳·경기 2곳짜리 미니 카탈로그의 현행 지역 명부(옛서울구는 폐지돼 없다). */
    public static final ProvinceRoster 시도명부 = ProvinceRoster.of(Map.of("KR-11", List.of(종로구, 중구, 용산구),
        "KR-31", List.of(가평군, 수원시)));

    public static final Badges 뱃지 = new Badges(List.of(
        new Badge("first", new BadgeRule.RegionCount(1)),
        new Badge("seoul", new BadgeRule.ProvincesComplete(List.of("KR-11"))),
        new Badge("legend", new BadgeRule.LegendCount(1)),
        new Badge("allprov", new BadgeRule.AllProvincesTouched()),
        new Badge("set1", new BadgeRule.ThemesCompleted(1)),
        new Badge("streak3", new BadgeRule.StreakMonths(3)),
        new Badge("half", new BadgeRule.ConquestRatio(0.5)),
        new Badge("mystery1", new BadgeRule.MysteryFound(1)),
        new Badge("mystery5", new BadgeRule.MysteryFound(5))));

    public static final TitleRules 칭호 = new TitleRules(List.of(
        new TitleRule("lv1", TitleRule.Source.LEVEL, "1"),
        new TitleRule("lv3", TitleRule.Source.LEVEL, "3"),
        new TitleRule("set-han", TitleRule.Source.SET, "han"),
        new TitleRule("long-leg5", TitleRule.Source.QUEST, "leg5"),
        new TitleRule("streak-3", TitleRule.Source.STREAK, "3"),
        new TitleRule("own-KR-11", TitleRule.Source.PROVINCE, "KR-11")));

    /** 보호권 최대 2개, 월간 퀘스트를 모두 받으면 1개, 마일스톤 3·6·12·24개월(XP 50·100·200·400, 보호권 1). */
    public static final StreakRules 연속규칙 = new StreakRules(2, 1, List.of(new StreakMilestone(3, 50, 1),
        new StreakMilestone(6, 100, 1), new StreakMilestone(12, 200, 1), new StreakMilestone(24, 400, 1)));

    public static final ProgressionPolicy 진행규칙 = new ProgressionPolicy(LevelCurve.withDivisor(5), 보상규칙, 뱃지, 칭호,
        시도명부, 5, 서울시각, 연속규칙, Set.of("m3", "mgun", "mprov", "mset"));

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
        return new 방문(지도, code, 기준시각, true, 0, null);
    }

    public static ProgressChange 칠한다(ExplorerProgress progress, 방문 visit) {
        return progress.applyVisit(visit.사실(), 진행규칙);
    }

    public static ProgressChange 취소한다(ExplorerProgress progress, 방문 visit) {
        return progress.revokeVisit(visit.map(), visit.code(), visit.generation(), visit.at(), 진행규칙);
    }

    /** 체크인 사실을 문장처럼 만드는 빌더: {@code 방문(종로구).지도(다른지도).처리시각(초(2)).회차(1)}. */
    public record 방문(String map, RegionCode code, Instant at, boolean firstClaim, int generation, MysteryFact mystery) {

        public 방문 지도(String mapId) {
            return new 방문(mapId, code, at, firstClaim, generation, mystery);
        }

        public 방문 처리시각(Instant processedAt) {
            return new 방문(map, code, processedAt, firstClaim, generation, mystery);
        }

        /** 지도 안에서 내가 먼저 칠한 사람이 아니다. */
        public 방문 선점아님() {
            return new 방문(map, code, at, false, generation, mystery);
        }

        public 방문 회차(int n) {
            return new 방문(map, code, at, firstClaim, n, mystery);
        }

        /** 처리 시각이 속한 주의 미스터리 지역이 이것이다(8단계 — 카탈로그 기록). */
        public 방문 그주의미스터리(String weekId, RegionCode mysteryRegion) {
            return new 방문(map, code, at, firstClaim, generation, new MysteryFact(weekId, mysteryRegion));
        }

        public ProgressVisit 사실() {
            return new ProgressVisit(map, code, 시도(code), 희귀도(code), at, firstClaim, generation, mystery);
        }
    }

    // ---- 연속 탐험(8단계) ------------------------------------------------------------------------------------------

    /** 그 달 15일 정오(서울 시각) — 달 단위 이야기의 처리 시각. */
    public static Instant 그달(int year, int month) {
        return YearMonth.of(year, month).atDay(15).atTime(12, 0).atZone(서울시각).toInstant();
    }

    /** 그 달 15일 정오에 경북의 어느 새 지역을 칠한 사실(달마다 다른 지역 — 스트릭 이야기용, 명부 밖이라 정복과 무관). */
    public static ProgressVisit 그달의방문(int year, int month) {
        RegionCode somewhere = RegionCode.of(String.format("KR-37%03d", year % 100 * 12 + month));
        return new ProgressVisit(지도, somewhere, "KR-37", Rarity.COMMON, 그달(year, month), false, 0);
    }

    /** 그 달에 새 지역 하나를 칠한다({@link #그달의방문}). */
    public static ProgressChange 그달에_칠한다(ExplorerProgress progress, int year, int month) {
        return progress.applyVisit(그달의방문(year, month), 진행규칙);
    }

    /** 같은 달에 또 다른 지역 하나를 칠한다(그 달 15일 정오 1분 뒤). */
    public static ProgressChange 그달에_한곳더_칠한다(ExplorerProgress progress, int year, int month) {
        RegionCode another = RegionCode.of(String.format("KR-37%03d", 500 + year % 100 * 12 + month));
        return progress.applyVisit(new ProgressVisit(지도, another, "KR-37", Rarity.COMMON, 그달(year, month).plusSeconds(60), false,
            0), 진행규칙);
    }

    /** 그 달의 월간 퀘스트 네 개(m3·mgun·mprov·mset) 보상을 모두 받는다(마지막 보상 시각 = 그 달 15일 정오 + 4초). */
    public static ProgressChange 월간퀘스트를_모두_받는다(ExplorerProgress progress, int year, int month) {
        QuestPeriod period = QuestPeriod.of(YearMonth.of(year, month));
        ProgressChange last = null;
        int order = 1;
        for (String questId : List.of("m3", "mgun", "mprov", "mset")) {
            last = progress.applyQuestReward(period, questId, 10, 그달(year, month).plusSeconds(order++), 진행규칙);
        }
        return last;
    }

    // ---- 퀘스트 -------------------------------------------------------------------------------------------------

    public static QuestFact 퀘스트사실(RegionCode code, boolean firstInProvince) {
        return new QuestFact(code, 시도(code), 희귀도(code), 테마.includeAny(code), firstInProvince);
    }
}
