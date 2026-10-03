package com.kobi.territory.sharing.domain.showcase;

import com.kobi.territory.sharing.domain.SharingError;
import com.kobi.territory.sharing.domain.card.CardKind;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 도메인 서비스: 공개 정보(Showcase) → 카드 내용(CardContent). 프로토타입 카드 4종의 문구·지도 칠 규칙을 옮겼다.
 * 렌더러는 그리기만 하고, 무엇을 보여 줄지(월 단위 날짜, 집계만)는 여기서 정한다.
 */
public final class CardComposer {

    private CardComposer() {}

    /** 한 탐험가 카드(TERRITORY·RECENT·RECAP). VS 는 {@link #versus}. */
    public static CardContent compose(CardKind kind, Showcase showcase, Year year) {
        Objects.requireNonNull(kind, "kind");
        return switch (kind) {
            case TERRITORY -> territory(showcase);
            case RECENT -> recent(showcase);
            case RECAP -> recap(showcase, year);
            case VS -> throw new IllegalArgumentException("VS 카드는 versus(subject, other)로 만든다");
        };
    }

    static CardContent.Territory territory(Showcase showcase) {
        PublicVisits visits = showcase.visits();
        RegionAtlas atlas = showcase.atlas();
        ShowcaseProgress progress = showcase.progress();
        MapPaint paint = MapPaint.builder().paint(visits.paintedCodes(), Tone.MINE).ring(visits.legendCodes()).build();
        List<CardContent.Stat> stats = List.of(
            new CardContent.Stat("정복한 시·도", showcase.conqueredProvinces() + " / " + atlas.provinceCount()),
            new CardContent.Stat("전설 지역", visits.legendCount() + " / " + atlas.legendCount()),
            new CardContent.Stat("도감 세트", progress.themesCompleted() + " / " + progress.themeTotal()),
            new CardContent.Stat("레벨", "Lv." + progress.level() + " · 스트릭 " + progress.streakMonths() + "개월"),
            new CardContent.Stat("꾸미기", showcase.scene().stylePoints() + "점 · 아이템 " + showcase.scene().ownedCount() + "개"));
        return new CardContent.Territory(footerOf(showcase), paint, ownerLine(showcase, "의 영토"),
            showcase.conquestPercent(), visits.count(), atlas.regionCount(), stats, showcase.scene().wornItemNames());
    }

    static CardContent.Recent recent(Showcase showcase) {
        PublicVisits visits = showcase.visits();
        List<CardContent.Stat> stats = List.of(new CardContent.Stat("전국", showcase.conquestPercent() + "% · "
            + visits.count() + "곳"));
        return visits.mostRecent().map(latest -> new CardContent.Recent(footerOf(showcase),
                MapPaint.builder().paint(visits.paintedCodes(), Tone.MINE).highlight(latest.region().code()).build(),
                latest.nth() + "번째 영토 · " + latest.month().label(), latest.region().name(),
                latest.region().provinceName() + " · " + RarityLabel.of(latest.region().rarity()) + " 지역",
                latest.region().legend(), stats))
            .orElseGet(() -> new CardContent.Recent(footerOf(showcase), MapPaint.builder().build(),
                ownerLine(showcase, "의 최근 여행"), null, "아직 칠한 곳이 없어요", false, stats));
    }

    static CardContent.Recap recap(Showcase showcase, Year year) {
        PublicVisits visits = showcase.visits();
        YearRecap recap = visits.recap(year);
        Set<String> thisYear = visits.codesVisitedIn(year);
        Set<String> earlier = visits.paintedCodes().stream().filter(code -> !thisYear.contains(code))
            .collect(Collectors.toUnmodifiableSet());
        MapPaint paint = MapPaint.builder().paint(earlier, Tone.FADED).paint(thisYear, Tone.MINE).build();
        List<CardContent.Stat> stats = new ArrayList<>();
        stats.add(new CardContent.Stat("가장 많이 간 시·도", orDash(recap.topProvince())));
        stats.add(new CardContent.Stat("가장 희귀한 곳", orDash(recap.rarest())));
        return new CardContent.Recap(footerOf(showcase), paint, showcase.displayName() + "의 " + recap.year() + " 리캡",
            recap.newRegions(), "올해 새로 밟은 땅 · 시·도 " + recap.newProvinces() + "곳 신규", recap.monthCounts(), stats);
    }

    /** VS 카드(두 탐험가 모두 공개일 때만 — 호출자가 확인). 자기 자신과의 비교는 없다(PROFILE_NOT_FOUND). */
    public static CardContent.Versus versus(Showcase mine, Showcase theirs) {
        if (Objects.equals(mine.handle(), theirs.handle())) throw SharingError.PROFILE_NOT_FOUND.exception();
        Set<String> myCodes = mine.visits().paintedCodes();
        Set<String> theirCodes = theirs.visits().paintedCodes();
        Set<String> both = myCodes.stream().filter(theirCodes::contains).collect(Collectors.toUnmodifiableSet());
        MapPaint paint = MapPaint.builder()
            .paint(myCodes, Tone.MINE).paint(theirCodes, Tone.THEIRS).paint(both, Tone.BOTH).build();
        return new CardContent.Versus(footerOf(mine), paint, mine.displayName(), theirs.displayName(),
            mine.visits().versus(theirs.visits()));
    }

    /** "@handle의 영토 · 칭호" — 칭호가 없으면 칭호 없이. */
    private static String ownerLine(Showcase showcase, String suffix) {
        String title = showcase.progress().titleName();
        return showcase.displayName() + suffix + (title == null ? "" : " · " + title);
    }

    private static String footerOf(Showcase showcase) {
        return showcase.footer();
    }

    private static String orDash(String value) {
        return value == null ? "—" : value;
    }
}
