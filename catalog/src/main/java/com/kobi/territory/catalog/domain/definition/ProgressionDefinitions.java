package com.kobi.territory.catalog.domain.definition;

import com.kobi.territory.catalog.domain.region.Provinces;
import com.kobi.territory.catalog.domain.region.Regions;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.time.Instant;
import java.time.ZoneId;

/**
 * 진행(2단계) 정의 데이터 묶음: 레벨 곡선·칭호, 도감 세트, 뱃지, 퀘스트. 칭호 목록은 이들로부터 파생한다.
 * 생성 시 id 유일성을, {@link #requireConsistentWith}로 지역·시·도 참조 정합성을 검증한다(어긋나면 기동 실패).
 */
public record ProgressionDefinitions(LevelRules levels, List<ThemeDefinition> themes, List<BadgeDefinition> badges,
                                     List<QuestDefinition> quests, StreakRules streak, List<SeasonDefinition> seasons) {

    public ProgressionDefinitions {
        Objects.requireNonNull(levels, "levels");
        Objects.requireNonNull(streak, "streak");
        seasons = seasons == null ? List.of() : List.copyOf(seasons);
        unique("계절", seasons.stream().map(SeasonDefinition::id).toList());
        themes = List.copyOf(themes);
        badges = List.copyOf(badges);
        quests = List.copyOf(quests);
        unique("테마(세트)", themes.stream().map(ThemeDefinition::id).toList());
        unique("뱃지", badges.stream().map(BadgeDefinition::id).toList());
        unique("퀘스트", quests.stream().map(QuestDefinition::id).toList());
    }

    /** 계절 한정 테마 없이 — 9단계 이전 정의. */
    public ProgressionDefinitions(LevelRules levels, List<ThemeDefinition> themes, List<BadgeDefinition> badges,
                                  List<QuestDefinition> quests, StreakRules streak) {
        this(levels, themes, badges, quests, streak, List.of());
    }

    /** 연속 탐험 규칙(보호권·마일스톤) 없이 — 8단계 이전 정의. */
    public ProgressionDefinitions(LevelRules levels, List<ThemeDefinition> themes, List<BadgeDefinition> badges,
                                  List<QuestDefinition> quests) {
        this(levels, themes, badges, quests, StreakRules.none());
    }

    /** 1단계 테스트 등 진행 정의가 필요 없는 곳에서 쓰는 빈 정의. */
    public static ProgressionDefinitions empty() {
        return new ProgressionDefinitions(new LevelRules(5, List.of(new LevelTitle(1, "초보 탐험가"))), List.of(), List.of(),
            List.of());
    }

    /** 세트 지역은 카탈로그에 있어야 하고, 뱃지가 가리키는 시·도는 알려진 시·도여야 한다. */
    public void requireConsistentWith(Regions regions, Provinces provinces) {
        themes.forEach(theme -> theme.regions().forEach(code -> regions.find(code)
            .orElseThrow(() -> new IllegalStateException("테마(세트) " + theme.id() + " 의 모르는 지역: " + code))));
        badges.forEach(badge -> badge.condition().referencedProvinces().forEach(provinces::require));
        seasons.forEach(season -> season.regions().forEach(code -> regions.find(code)
            .orElseThrow(() -> new IllegalStateException("계절 " + season.id() + " 의 모르는 지역: " + code))));
    }

    /** 이 회차 id(계절-연도)가 정의된 계절의 회차인지(아이템 지급 규칙 참조 검증, 9단계). */
    public boolean definesSeasonRound(String roundId) {
        String seasonId = SeasonDefinition.seasonIdOf(roundId);
        return seasonId != null && seasons.stream().anyMatch(season -> season.id().equals(seasonId));
    }

    /** 회차 id({계절}-{연도})의 계절 정의. 모르는 계절·형식이면 빈 값. */
    public Optional<SeasonDefinition> seasonOfRound(String roundId) {
        String seasonId = SeasonDefinition.seasonIdOf(roundId);
        return seasons.stream().filter(season -> season.id().equals(seasonId)).findFirst();
    }

    /** 회차 id 의 기간. 모르는 계절·형식이면 빈 값. */
    public Optional<SeasonRoundWindow> roundWindow(String roundId, ZoneId zone) {
        return seasonOfRound(roundId).flatMap(season -> SeasonDefinition.yearOf(roundId).map(year -> season.windowOf(year, zone)));
    }

    /** 계절마다 이 시각 뒤에 처음 열리는 회차(정의 순서) — 자동 수집 대상. */
    public List<SeasonRoundWindow> upcomingWindows(Instant at, ZoneId zone) {
        return seasons.stream().map(season -> season.upcomingWindow(at, zone)).toList();
    }

    /** 이 시각에 열려 있는 회차(정의 순서). */
    public List<SeasonRoundWindow> openWindows(Instant at, ZoneId zone) {
        return seasons.stream().flatMap(season -> season.openWindow(at, zone).stream()).toList();
    }

    /**
     * 칭호 전체(표시 순서: 레벨 → 세트 → 상시 도전 → 연속 탐험 마일스톤(8단계) → 계절 한정(9단계) → 시·도 주인). 프로토타입 titles() 와 같은
     * 순서·id.
     */
    public List<TitleDefinition> titles(Provinces provinces) {
        List<TitleDefinition> out = new ArrayList<>();
        levels.titles().forEach(levelTitle -> out.add(new TitleDefinition("lv" + levelTitle.level(), levelTitle.name(), "Lv." + levelTitle.level(),
            TitleDefinition.Source.LEVEL, String.valueOf(levelTitle.level()))));
        themes.forEach(theme -> out.add(new TitleDefinition("set-" + theme.id(), theme.title(), theme.name() + " 세트",
            TitleDefinition.Source.SET, theme.id())));
        quests.stream().filter(quest -> quest.title() != null).forEach(quest -> out.add(new TitleDefinition("long-" + quest.id(), quest.title(),
            quest.name(), TitleDefinition.Source.QUEST, quest.id())));
        streak.milestones().forEach(milestone -> out.add(new TitleDefinition(milestone.titleId(), milestone.title(),
            milestone.months() + "개월 연속 탐험", TitleDefinition.Source.STREAK, String.valueOf(milestone.months()))));
        seasons.forEach(season -> out.add(new TitleDefinition(season.titleId(), season.title(), season.name() + " 완성(계절 한정)",
            TitleDefinition.Source.SEASON, season.id())));
        provinces.inDisplayOrder().forEach(province -> out.add(new TitleDefinition("own-" + province.code(), province.name() + "의 주인",
            province.name() + " 100%", TitleDefinition.Source.PROVINCE, province.code())));
        return List.copyOf(out);
    }

    private static void unique(String what, List<String> ids) {
        Set<String> seen = new HashSet<>();
        ids.forEach(id -> {
            if (!seen.add(id)) throw new IllegalStateException(what + " id 중복: " + id);
        });
    }
}
