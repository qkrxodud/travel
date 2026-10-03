package com.kobi.territory.sharing.domain.card;

import static com.kobi.territory.sharing.domain.Fixtures.JONGNO;
import static com.kobi.territory.sharing.domain.Fixtures.JUNG;
import static com.kobi.territory.sharing.domain.Fixtures.OWNER;
import static com.kobi.territory.sharing.domain.Fixtures.PERSONAL_MAP;
import static com.kobi.territory.sharing.domain.Fixtures.T0;
import static com.kobi.territory.sharing.domain.Fixtures.showcase;
import static com.kobi.territory.sharing.domain.Fixtures.visit;
import static com.kobi.territory.sharing.domain.Fixtures.visits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.sharing.domain.showcase.CardComposer;
import com.kobi.territory.sharing.domain.showcase.Showcase;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 회귀 출처: 4단계 QA P2-1(이벤트 없이 바뀐 요약도 낡음으로) · P2-2(공개 이름이 바뀌면 바로) · P3-4(기준별 그림 파일). */
@DisplayName("공유 카드")
class ShareCardTest {

    static final CardCachePolicy TEN_MINUTES = new CardCachePolicy(Duration.ofMinutes(10));
    static final ShareCardId ID = new ShareCardId(OWNER, PERSONAL_MAP, CardKind.TERRITORY);
    static final String TERRITORY_2026 = "TERRITORY@2026";

    static CardBasis basisOf(Showcase showcase) {
        return new CardBasis(showcase.summaryHash(TERRITORY_2026), showcase.handle());
    }

    static final CardBasis FIRST = basisOf(showcase("kim").painted(JONGNO));

    /** 처음 요약으로 T0 에 그린 카드. */
    static ShareCard renderedAtStart() {
        ShareCard card = ShareCard.unrendered(ID);
        card.markRendered(FIRST, ID.imageKey(FIRST), T0);
        return card;
    }

    @Nested
    @DisplayName("처음 열면")
    class FirstOpen {

        @Test
        @DisplayName("그린 적 없는 카드는 그린다")
        void unrenderedNeedsRender() {
            assertThat(ShareCard.unrendered(ID).needsRender(FIRST, T0, TEN_MINUTES)).isTrue();
        }

        @Test
        @DisplayName("처음 그린 카드에는 지울 옛 그림이 없다")
        void noPreviousImage() {
            assertThat(ShareCard.unrendered(ID).markRendered(FIRST, ID.imageKey(FIRST), T0)).isNull();
        }
    }

    @Nested
    @DisplayName("공개 요약이 그대로면")
    class Unchanged {

        @Test
        @DisplayName("며칠이 지나도 다시 그리지 않는다")
        void neverRedraws() {
            assertThat(renderedAtStart().needsRender(basisOf(showcase("kim").painted(JONGNO)), T0.plus(Duration.ofDays(3)), TEN_MINUTES))
                .isFalse();
        }
    }

    @Nested
    @DisplayName("공개 요약이 바뀌면")
    class SummaryChanged {

        /** 계정 병합 뒤 다시 계산되어 레벨·가방·칠한 곳이 바뀐 요약 — 알려 주는 소식 없이 바뀌었다. */
        static final CardBasis RECALCULATED = basisOf(showcase("kim").level(8).owned(46).painted(JONGNO, JUNG));

        @Test
        @DisplayName("소식 없이 바뀐 레벨·가방도 낡은 카드로 알아챈다")
        void recalculationMakesStale() {
            assertThat(renderedAtStart().stale(RECALCULATED)).isTrue();
        }

        @Test
        @DisplayName("칭호만 바꿔도 낡은 카드가 된다")
        void titleChangeMakesStale() {
            assertThat(renderedAtStart().stale(basisOf(showcase("kim").title("골목 탐험가").painted(JONGNO)))).isTrue();
        }

        @Test
        @DisplayName("최소 유지 시간 10분이 지나기 전에는 다시 그리지 않는다")
        void waitsForMinimumTtl() {
            assertThat(renderedAtStart().needsRender(RECALCULATED, T0.plus(Duration.ofMinutes(9)), TEN_MINUTES)).isFalse();
        }

        @Test
        @DisplayName("최소 유지 시간이 지나면 다시 그린다")
        void redrawsAfterTtl() {
            assertThat(renderedAtStart().needsRender(RECALCULATED, T0.plus(Duration.ofMinutes(10)), TEN_MINUTES)).isTrue();
        }

        @Test
        @DisplayName("다시 그리면 바뀐 요약을 이름에 담은 새 그림으로 바꾸고 옛 그림을 지우도록 알려 준다")
        void redrawReplacesImage() {
            ShareCard card = renderedAtStart();
            String previous = card.imageKey();

            assertThat(card.markRendered(RECALCULATED, ID.imageKey(RECALCULATED), T0.plus(Duration.ofMinutes(10)))).isEqualTo(previous);
            assertThat(card.imageKey()).isNotEqualTo(previous).contains(RECALCULATED.shortHash());
        }

        @Test
        @DisplayName("다시 그린 카드는 더 이상 낡지 않았다")
        void freshAfterRedraw() {
            ShareCard card = renderedAtStart();
            card.markRendered(RECALCULATED, ID.imageKey(RECALCULATED), T0.plus(Duration.ofMinutes(10)));

            assertThat(card.stale(RECALCULATED)).isFalse();
        }
    }

    @Nested
    @DisplayName("공개 이름이 생기거나 바뀌면")
    class IdentityChanged {

        static final CardBasis ANONYMOUS = basisOf(showcase(null).painted(JONGNO));
        static final CardBasis LINKED = basisOf(showcase("kim").painted(JONGNO));

        @Test
        @DisplayName("익명에서 계정으로 바뀌면 최소 유지 시간을 기다리지 않고 바로 다시 그린다")
        void anonymousToAccount() {
            ShareCard card = ShareCard.unrendered(ID);
            card.markRendered(ANONYMOUS, ID.imageKey(ANONYMOUS), T0);

            assertThat(card.needsRender(LINKED, T0.plusSeconds(1), TEN_MINUTES)).isTrue();
        }

        @Test
        @DisplayName("공개 이름을 바꾸면 바로 다시 그린다")
        void handleChanged() {
            ShareCard card = ShareCard.unrendered(ID);
            card.markRendered(LINKED, ID.imageKey(LINKED), T0);

            assertThat(card.needsRender(basisOf(showcase("kim2").painted(JONGNO)), T0.plusSeconds(2), TEN_MINUTES)).isTrue();
        }
    }

    @Nested
    @DisplayName("카드 기준")
    class Basis {

        static final Showcase KIM = showcase("kim").painted(JONGNO);

        @Test
        @DisplayName("같은 공개 요약은 언제나 같은 기준이 된다")
        void deterministic() {
            assertThat(KIM.summaryHash(TERRITORY_2026)).isEqualTo(showcase("kim").painted(JONGNO).summaryHash(TERRITORY_2026))
                .hasSize(64);
        }

        @Test
        @DisplayName("카드 종류가 다르면 기준이 다르다")
        void differsByKind() {
            assertThat(KIM.summaryHash("RECENT@2026")).isNotEqualTo(KIM.summaryHash(TERRITORY_2026));
        }

        @Test
        @DisplayName("기준 연도가 다르면 기준이 다르다")
        void differsByYear() {
            assertThat(KIM.summaryHash("TERRITORY@2027")).isNotEqualTo(KIM.summaryHash(TERRITORY_2026));
        }

        @Test
        @DisplayName("공개 이름이 다르면 기준이 다르다")
        void differsByHandle() {
            assertThat(showcase("lee").painted(JONGNO).summaryHash(TERRITORY_2026)).isNotEqualTo(KIM.summaryHash(TERRITORY_2026));
        }

        @Test
        @DisplayName("방문일이 바뀌면 기준이 다르다")
        void differsByVisitDate() {
            Showcase moved = new Showcase("kim", KIM.atlas(), visits(visit(JONGNO, "2025-03-01", 0)), KIM.progress(), KIM.scene());

            assertThat(moved.summaryHash(TERRITORY_2026)).isNotEqualTo(KIM.summaryHash(TERRITORY_2026));
        }

        @Test
        @DisplayName("대결 카드 기준은 상대의 공개 요약이 바뀌어도 달라진다")
        void versusBasisFollowsOpponent() {
            Showcase lee = showcase("lee").painted(JONGNO);
            Showcase leeMoved = showcase("lee").painted(JONGNO, JUNG);

            assertThat(KIM.pairHash(lee, "VS")).isNotEqualTo(KIM.pairHash(leeMoved, "VS"));
        }
    }

    @Nested
    @DisplayName("대결 카드와 카드 종류")
    class Kinds {

        @Test
        @DisplayName("대결 카드는 저장해 두지 않는다")
        void versusNotStored() {
            assertThatThrownBy(() -> new ShareCardId(OWNER, PERSONAL_MAP, CardKind.VS)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("자기 자신과는 대결 카드를 만들 수 없다")
        void noVersusWithSelf() {
            Showcase kim = showcase("kim").painted(JONGNO);

            assertThatThrownBy(() -> CardComposer.versus(kim, kim)).hasFieldOrPropertyWithValue("code", "PROFILE_NOT_FOUND");
        }

        @Test
        @DisplayName("혼자 카드 이름은 대소문자를 가리지 않는다")
        void soloKindCaseInsensitive() {
            assertThat(CardKind.parseSolo("Territory")).isEqualTo(CardKind.TERRITORY);
        }

        @Test
        @DisplayName("대결은 혼자 카드 종류가 아니다")
        void versusIsNotSolo() {
            assertThatThrownBy(() -> CardKind.parseSolo("vs")).hasMessageContaining("모르는 카드");
        }
    }
}
