package com.kobi.territory.sharing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.sharing.domain.card.CardKind;
import com.kobi.territory.common.model.TerritoryComparison;
import com.kobi.territory.sharing.domain.privacy.PrivacyRoster;
import com.kobi.territory.sharing.domain.privacy.PrivacySettings;
import com.kobi.territory.sharing.domain.privacy.ProfileVisibility;
import com.kobi.territory.sharing.domain.showcase.CardComposer;
import com.kobi.territory.sharing.domain.showcase.CardContent;
import com.kobi.territory.sharing.domain.showcase.ProvinceInfo;
import com.kobi.territory.sharing.domain.showcase.PublicVisit;
import com.kobi.territory.sharing.domain.showcase.PublicVisits;
import com.kobi.territory.sharing.domain.showcase.RegionAtlas;
import com.kobi.territory.sharing.domain.showcase.RegionInfo;
import com.kobi.territory.sharing.domain.showcase.Showcase;
import com.kobi.territory.sharing.domain.showcase.ShowcaseProgress;
import com.kobi.territory.sharing.domain.showcase.ShowcaseScene;
import com.kobi.territory.sharing.domain.showcase.Tone;
import com.kobi.territory.sharing.domain.showcase.VisitFact;
import com.kobi.territory.sharing.domain.showcase.VersusTally;
import com.kobi.territory.sharing.domain.showcase.VisitMonth;
import com.kobi.territory.sharing.domain.showcase.YearRecap;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/** D1: PrivacySettings(FRIENDS 는 맞팔로우에게만 — 5단계), 날짜 월 단위, 공개 집계·리캡·VS, 카드 문구에 정확한 날짜 없음. */
class PrivacyAndShowcaseTest {

    static final ExplorerId EXPLORER = ExplorerId.of(UUID.randomUUID().toString());
    static final Instant T0 = Instant.parse("2026-10-03T03:00:00Z");

    static final RegionAtlas ATLAS = RegionAtlas.of(List.of(
        new RegionInfo("KR-11010", "종로구", "KR-11", "서울", Rarity.COMMON),
        new RegionInfo("KR-11020", "중구", "KR-11", "서울", Rarity.COMMON),
        new RegionInfo("KR-37430", "울릉군", "KR-37", "경북", Rarity.LEGEND),
        new RegionInfo("KR-37010", "포항시", "KR-37", "경북", Rarity.COMMON)),
        List.of(new ProvinceInfo("KR-11", "서울", 2), new ProvinceInfo("KR-37", "경북", 2)));

    private static VisitFact fact(String code, String date, int order) {
        return new VisitFact(code, LocalDate.parse(date), T0.plusSeconds(order));
    }

    @Test
    void 공개_범위_기본은_PRIVATE_공개하기를_켜야_열리고_FRIENDS_는_익명에게_PRIVATE_처럼_404() {
        PrivacySettings settings = PrivacySettings.defaults(EXPLORER);
        assertThat(settings.visibility()).isEqualTo(ProfileVisibility.PRIVATE);
        assertThatThrownBy(settings::requireVisibleToPublic).hasFieldOrPropertyWithValue("code", "PROFILE_NOT_FOUND");
        assertThat(settings.change(ProfileVisibility.PUBLIC, T0)).isTrue();
        settings.requireVisibleToPublic();

        assertThat(settings.change(ProfileVisibility.FRIENDS, T0)).isTrue();
        assertThat(settings.visibleToPublic()).isFalse();
        assertThatThrownBy(settings::requireVisibleToPublic).hasFieldOrPropertyWithValue("code", "PROFILE_NOT_FOUND");

        settings.change(ProfileVisibility.PRIVATE, T0);
        assertThatThrownBy(settings::requireVisibleToPublic).hasFieldOrPropertyWithValue("code", "PROFILE_NOT_FOUND");
        assertThat(settings.change(ProfileVisibility.PRIVATE, T0)).isFalse();
        assertThat(ProfileVisibility.parse(" public ")).isEqualTo(ProfileVisibility.PUBLIC);
        assertThatThrownBy(() -> ProfileVisibility.parse("everyone")).hasFieldOrPropertyWithValue("code", "INVALID_VISIBILITY");
    }

    @Test
    void FRIENDS_는_서로_팔로우한_친구에게만_보이고_PRIVATE_는_친구에게도_안_보인다() {
        ExplorerId friend = ExplorerId.of(UUID.randomUUID().toString());
        ExplorerId stranger = ExplorerId.of(UUID.randomUUID().toString());
        Predicate<ExplorerId> mutualWithOwner = friend::equals;
        PrivacySettings settings = PrivacySettings.defaults(EXPLORER);

        settings.change(ProfileVisibility.FRIENDS, T0);
        assertThat(settings.visibleTo(friend, mutualWithOwner)).isTrue();
        assertThat(settings.visibleTo(stranger, mutualWithOwner)).isFalse();
        assertThat(settings.visibleTo(null, mutualWithOwner)).isFalse();
        assertThat(settings.visibleTo(EXPLORER, viewer -> false)).isTrue(); // 주인 본인은 항상(리더 결정 2 — 미리보기)
        settings.requireVisibleTo(friend, mutualWithOwner);
        assertThatThrownBy(() -> settings.requireVisibleTo(stranger, mutualWithOwner))
            .hasFieldOrPropertyWithValue("code", "PROFILE_NOT_FOUND");

        settings.change(ProfileVisibility.PRIVATE, T0);
        assertThat(settings.visibleTo(friend, mutualWithOwner)).isFalse();
        assertThat(settings.visibleTo(EXPLORER, mutualWithOwner)).isTrue();
        settings.change(ProfileVisibility.PUBLIC, T0);
        assertThat(settings.visibleTo(null, viewer -> { throw new AssertionError("PUBLIC 은 친구 관계를 묻지 않는다"); })).isTrue();
    }

    @Test
    void 여러_주인의_공개_범위는_저장된_행이_없으면_PRIVATE_로_채운다() {
        ExplorerId open = ExplorerId.of(UUID.randomUUID().toString());
        ExplorerId friendsOnly = ExplorerId.of(UUID.randomUUID().toString());
        ExplorerId unset = ExplorerId.of(UUID.randomUUID().toString());
        ExplorerId viewer = ExplorerId.of(UUID.randomUUID().toString());
        PrivacyRoster roster = PrivacyRoster.of(List.of(open, friendsOnly, unset), List.of(
            PrivacySettings.restore(open, ProfileVisibility.PUBLIC, T0),
            PrivacySettings.restore(friendsOnly, ProfileVisibility.FRIENDS, T0),
            PrivacySettings.restore(viewer, ProfileVisibility.PUBLIC, T0)));

        assertThat(roster.visibleTo(viewer, (owner, candidate) -> false)).containsExactly(open);
        assertThat(roster.visibleTo(viewer, (owner, candidate) -> owner.equals(friendsOnly) && candidate.equals(viewer)))
            .containsExactlyInAnyOrder(open, friendsOnly);
    }

    @Test
    void VS_는_공유_커널_비교와_같은_수를_낸다() {
        PublicVisits mine = PublicVisits.of(List.of(fact("KR-11010", "2026-01-01", 1), fact("KR-11020", "2026-01-02", 2)), ATLAS);
        PublicVisits theirs = PublicVisits.of(List.of(fact("KR-11020", "2026-01-01", 1), fact("KR-37430", "2026-01-02", 2)), ATLAS);
        assertThat(mine.versus(theirs)).isEqualTo(VersusTally.of(TerritoryComparison.of(mine.paintedCodes(), theirs.paintedCodes())))
            .isEqualTo(new VersusTally(1, 1, 1));
    }

    @Test
    void 날짜는_월_단위로만_공개된다() {
        VisitMonth month = VisitMonth.of(LocalDate.parse("2026-10-17"));
        assertThat(month.label()).isEqualTo("2026년 10월");
        assertThat(month.iso()).isEqualTo("2026-10");

        PublicVisits visits = PublicVisits.of(List.of(fact("KR-11010", "2026-10-17", 1), fact("KR-37430", "2025-05-02", 2)), ATLAS);
        PublicVisit latest = visits.mostRecent().orElseThrow();
        assertThat(latest.region().code()).isEqualTo("KR-11010");
        assertThat(latest.nth()).isEqualTo(1);
        assertThat(latest.month().label()).isEqualTo("2026년 10월");
        assertThat(visits.latest(10)).extracting(visit -> visit.month().iso()).containsExactly("2026-10", "2025-05");
    }

    @Test
    void 집계_정복률_시도_정복_전설_리캡_VS() {
        PublicVisits mine = PublicVisits.of(List.of(fact("KR-11010", "2026-03-01", 1), fact("KR-11020", "2026-03-09", 2),
            fact("KR-37430", "2025-08-01", 3), fact("KR-99999", "2026-01-01", 4)), ATLAS); // 모르는 지역은 뺀다
        assertThat(mine.count()).isEqualTo(3);
        assertThat(mine.conquestPercent(ATLAS)).isEqualTo(75);
        assertThat(mine.conqueredProvinceCount(ATLAS)).isEqualTo(1);
        assertThat(mine.legendCount()).isEqualTo(1);

        YearRecap recap = mine.recap(Year.of(2026));
        assertThat(recap.newRegions()).isEqualTo(2);
        assertThat(recap.monthCounts().get(2)).isEqualTo(2);
        assertThat(recap.topProvince()).isEqualTo(new YearRecap.ProvinceTally("KR-11", "서울", 2));
        assertThat(recap.newProvinces()).isEqualTo(1); // 서울(모두 2026) — 경북은 2025 방문
        assertThat(recap.busiestMonth()).isEqualTo(new YearRecap.MonthTally(3, 2));
        assertThat(mine.recap(Year.of(2025)).rarest().code()).isEqualTo("KR-37430");

        PublicVisits theirs = PublicVisits.of(List.of(fact("KR-11010", "2026-01-01", 1), fact("KR-37010", "2026-01-02", 2)), ATLAS);
        var tally = mine.versus(theirs);
        assertThat(tally.onlyMine()).isEqualTo(2);
        assertThat(tally.both()).isEqualTo(1);
        assertThat(tally.onlyTheirs()).isEqualTo(1);
    }

    @Test
    void 카드_내용에는_정확한_날짜가_없고_지도_칠은_종류별_규칙을_따른다() {
        PublicVisits visits = PublicVisits.of(List.of(fact("KR-11010", "2026-10-17", 1), fact("KR-37430", "2025-05-02", 2)), ATLAS);
        Showcase showcase = new Showcase("kim", ATLAS, visits, new ShowcaseProgress(3, "골목 탐험가", 2, 1, 9),
            new ShowcaseScene(4, List.of("청사초롱 등불"), 2));

        CardContent.Recent recent = (CardContent.Recent) CardComposer.compose(CardKind.RECENT, showcase, Year.of(2026));
        assertThat(recent.headline()).isEqualTo("1번째 영토 · 2026년 10월").doesNotContain("17");
        assertThat(recent.paint().highlighted()).contains("KR-11010");
        assertThat(recent.footer()).isEqualTo("나의 영토 /u/kim");

        CardContent.Territory territory = (CardContent.Territory) CardComposer.compose(CardKind.TERRITORY, showcase, Year.of(2026));
        assertThat(territory.headline()).isEqualTo("@kim의 영토 · 골목 탐험가");
        assertThat(territory.conquestPercent()).isEqualTo(50);
        assertThat(territory.paint().ringed("KR-37430")).isTrue();

        CardContent.Recap recap = (CardContent.Recap) CardComposer.compose(CardKind.RECAP, showcase, Year.of(2026));
        assertThat(recap.paint().toneOf("KR-11010")).contains(Tone.MINE);
        assertThat(recap.paint().toneOf("KR-37430")).contains(Tone.FADED);

        Showcase anonymous = new Showcase(null, ATLAS, PublicVisits.empty(), ShowcaseProgress.start(9), ShowcaseScene.empty());
        CardContent.Recent empty = (CardContent.Recent) CardComposer.compose(CardKind.RECENT, anonymous, Year.of(2026));
        assertThat(empty.regionName()).isNull();
        assertThat(empty.headline()).startsWith("나의 최근 여행");
    }

    @Test
    void 리캡_동점은_지역_코드_순_바쁜_달은_이른_달_06_QA_P2_1() {
        // 칠한 순서는 경북 포항(1) → 서울 중구(2) → 서울 종로(3, 작년) — 순서가 아니라 지역 코드로 동점을 가른다
        PublicVisits visits = PublicVisits.of(List.of(fact("KR-37010", "2026-05-01", 1), fact("KR-11020", "2026-02-01", 2),
            fact("KR-11010", "2025-01-01", 3)), ATLAS);
        YearRecap recap = visits.recap(Year.of(2026));
        assertThat(recap.newRegions()).isEqualTo(2);
        assertThat(recap.topProvince()).isEqualTo(new YearRecap.ProvinceTally("KR-11", "서울", 1));
        assertThat(recap.rarest().code()).isEqualTo("KR-11020");
        assertThat(recap.busiestMonth()).isEqualTo(new YearRecap.MonthTally(2, 1));
        assertThat(recap.newProvinces()).isEqualTo(1); // 경북만(서울은 작년 방문이 있다)

        PublicVisits sameRarity = PublicVisits.of(List.of(fact("KR-11020", "2026-03-01", 1), fact("KR-11010", "2026-03-02", 2)), ATLAS);
        assertThat(sameRarity.recap(Year.of(2026)).rarest().code()).isEqualTo("KR-11010");

        YearRecap empty = PublicVisits.empty().recap(Year.of(2026));
        assertThat(empty.topProvince()).isNull();
        assertThat(empty.rarest()).isNull();
        assertThat(empty.busiestMonth()).isNull();
        assertThat(empty.monthCounts()).hasSize(12).containsOnly(0);
    }

    @Test
    void 리캡_카드_문구는_같은_리캡_값에서_나온다() {
        PublicVisits visits = PublicVisits.of(List.of(fact("KR-11010", "2026-03-01", 1), fact("KR-11020", "2026-03-09", 2),
            fact("KR-37430", "2026-08-01", 3)), ATLAS);
        Showcase showcase = new Showcase("kim", ATLAS, visits, ShowcaseProgress.start(9), ShowcaseScene.empty());
        YearRecap recap = visits.recap(Year.of(2026));
        CardContent.Recap card = (CardContent.Recap) CardComposer.compose(CardKind.RECAP, showcase, Year.of(2026));
        assertThat(card.newRegions()).isEqualTo(recap.newRegions());
        assertThat(card.monthCounts()).isEqualTo(recap.monthCounts());
        assertThat(card.subline()).isEqualTo("올해 새로 밟은 땅 · 시·도 " + recap.newProvinces() + "곳 신규");
        assertThat(card.stats()).containsExactly(new CardContent.Stat("가장 많이 간 시·도", "서울 2곳"),
            new CardContent.Stat("가장 희귀한 곳", "울릉군 (전설)"));
    }

    @Test
    void 리캡_연도는_생략하면_올해_범위_밖은_INVALID_YEAR() {
        assertThat(YearRecap.yearOf(null, Year.of(2026))).isEqualTo(Year.of(2026));
        assertThat(YearRecap.yearOf(2025, Year.of(2026))).isEqualTo(Year.of(2025));
        assertThatThrownBy(() -> YearRecap.yearOf(0, Year.of(2026))).hasFieldOrPropertyWithValue("code", "INVALID_YEAR");
        assertThatThrownBy(() -> YearRecap.yearOf(10000, Year.of(2026))).hasFieldOrPropertyWithValue("code", "INVALID_YEAR");
    }
}
