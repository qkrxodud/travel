package com.kobi.territory.sharing.domain.showcase;

import static com.kobi.territory.sharing.domain.Fixtures.ATLAS;
import static com.kobi.territory.sharing.domain.Fixtures.JONGNO;
import static com.kobi.territory.sharing.domain.Fixtures.JUNG;
import static com.kobi.territory.sharing.domain.Fixtures.ULLEUNG;
import static com.kobi.territory.sharing.domain.Fixtures.visit;
import static com.kobi.territory.sharing.domain.Fixtures.visits;
import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.sharing.domain.card.CardKind;
import java.time.Year;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 회귀 출처: 4단계 자랑 카드 4종 · 6단계 QA P2-1(리캡 카드와 리캡 화면이 같은 값). */
@DisplayName("카드 내용")
class CardComposerTest {

    static final Year Y2026 = Year.of(2026);
    /** kim: Lv.3 "골목 탐험가", 종로(2026-10-17)·울릉(2025-05-02). */
    static final Showcase KIM = new Showcase("kim", ATLAS, visits(visit(JONGNO, "2026-10-17", 1), visit(ULLEUNG, "2025-05-02", 2)),
        new ShowcaseProgress(3, "골목 탐험가", 2, 1, 9), new ShowcaseScene(4, List.of("청사초롱 등불"), 2));

    static <T extends CardContent> T compose(CardKind kind, Showcase showcase, Class<T> type) {
        return type.cast(CardComposer.compose(kind, showcase, Y2026));
    }

    @Nested
    @DisplayName("최근 여행 카드")
    class Recent {

        @Test
        @DisplayName("몇 번째 영토인지와 그 달만 적고 정확한 날짜는 없다")
        void headlineWithMonthOnly() {
            assertThat(compose(CardKind.RECENT, KIM, CardContent.Recent.class).headline())
                .isEqualTo("1번째 영토 · 2026년 10월").doesNotContain("17");
        }

        @Test
        @DisplayName("가장 최근 지역을 강조한다")
        void highlightsLatest() {
            assertThat(compose(CardKind.RECENT, KIM, CardContent.Recent.class).paint().highlighted()).contains(JONGNO);
        }

        @Test
        @DisplayName("아래에 공개 프로필 주소를 적는다")
        void footerWithProfilePath() {
            assertThat(compose(CardKind.RECENT, KIM, CardContent.Recent.class).footer()).isEqualTo("나의 영토 /u/kim");
        }

        @Test
        @DisplayName("칠한 곳이 없으면 지역 없이 나의 최근 여행으로 시작한다")
        void emptyTerritory() {
            Showcase anonymous = new Showcase(null, ATLAS, PublicVisits.empty(), ShowcaseProgress.start(9), ShowcaseScene.empty());

            CardContent.Recent empty = compose(CardKind.RECENT, anonymous, CardContent.Recent.class);

            assertThat(empty.regionName()).isNull();
            assertThat(empty.headline()).startsWith("나의 최근 여행");
        }
    }

    @Nested
    @DisplayName("영토 카드")
    class Territory {

        @Test
        @DisplayName("제목에 공개 이름과 칭호를 적는다")
        void headline() {
            assertThat(compose(CardKind.TERRITORY, KIM, CardContent.Territory.class).headline()).isEqualTo("@kim의 영토 · 골목 탐험가");
        }

        @Test
        @DisplayName("전국 정복률을 적는다")
        void conquestPercent() {
            assertThat(compose(CardKind.TERRITORY, KIM, CardContent.Territory.class).conquestPercent()).isEqualTo(50);
        }

        @Test
        @DisplayName("칠한 전설 지역에 테두리를 두른다")
        void ringsLegends() {
            assertThat(compose(CardKind.TERRITORY, KIM, CardContent.Territory.class).paint().ringed(ULLEUNG)).isTrue();
        }
    }

    @Nested
    @DisplayName("리캡 카드")
    class Recap {

        @Test
        @DisplayName("올해 칠한 곳은 내 색으로 칠한다")
        void thisYearMine() {
            assertThat(compose(CardKind.RECAP, KIM, CardContent.Recap.class).paint().toneOf(JONGNO)).contains(Tone.MINE);
        }

        @Test
        @DisplayName("이전 해에 칠한 곳은 흐리게 칠한다")
        void earlierFaded() {
            assertThat(compose(CardKind.RECAP, KIM, CardContent.Recap.class).paint().toneOf(ULLEUNG)).contains(Tone.FADED);
        }

        /** 2026년 서울 두 곳(3월)·울릉(8월). */
        static final PublicVisits YEAR = visits(visit(JONGNO, "2026-03-01", 1), visit(JUNG, "2026-03-09", 2),
            visit(ULLEUNG, "2026-08-01", 3));
        static final Showcase YEAR_SHOWCASE = new Showcase("kim", ATLAS, YEAR, ShowcaseProgress.start(9), ShowcaseScene.empty());

        @Test
        @DisplayName("새로 칠한 곳과 달별 수는 연간 리캡과 같은 값이다")
        void sameNumbersAsRecap() {
            YearRecap recap = YEAR.recap(Y2026);
            CardContent.Recap card = compose(CardKind.RECAP, YEAR_SHOWCASE, CardContent.Recap.class);

            assertThat(card.newRegions()).isEqualTo(recap.newRegions());
            assertThat(card.monthCounts()).isEqualTo(recap.monthCounts());
        }

        @Test
        @DisplayName("부제에 새로 밟은 시·도 수를 적는다")
        void subline() {
            assertThat(compose(CardKind.RECAP, YEAR_SHOWCASE, CardContent.Recap.class).subline())
                .isEqualTo("올해 새로 밟은 땅 · 시·도 " + YEAR.recap(Y2026).newProvinces() + "곳 신규");
        }

        @Test
        @DisplayName("가장 많이 간 시·도와 가장 희귀한 곳을 적는다")
        void stats() {
            assertThat(compose(CardKind.RECAP, YEAR_SHOWCASE, CardContent.Recap.class).stats()).containsExactly(
                new CardContent.Stat("가장 많이 간 시·도", "서울 2곳"), new CardContent.Stat("가장 희귀한 곳", "울릉군 (전설)"));
        }
    }
}
