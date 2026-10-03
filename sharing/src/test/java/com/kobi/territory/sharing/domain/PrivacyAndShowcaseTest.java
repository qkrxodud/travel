package com.kobi.territory.sharing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.sharing.domain.card.CardKind;
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
import com.kobi.territory.sharing.domain.showcase.VisitMonth;
import com.kobi.territory.sharing.domain.showcase.YearRecap;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** D1: PrivacySettings(FRIENDS 는 PRIVATE 처럼), 날짜 월 단위, 공개 집계·리캡·VS, 카드 문구에 정확한 날짜 없음. */
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
    void 공개_범위_기본은_PRIVATE_공개하기를_켜야_열리고_FRIENDS_는_5단계_전까지_PRIVATE_처럼_404() {
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
        assertThat(recap.topProvince()).isEqualTo("서울 2곳");
        assertThat(recap.newProvinces()).isEqualTo(1); // 서울(모두 2026) — 경북은 2025 방문
        assertThat(recap.busiestMonth()).isEqualTo("3월 2곳");
        assertThat(mine.recap(Year.of(2025)).rarest()).isEqualTo("울릉군 (전설)");

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
}
