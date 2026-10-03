package com.kobi.territory.sharing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.sharing.domain.card.CardBasis;
import com.kobi.territory.sharing.domain.card.CardCachePolicy;
import com.kobi.territory.sharing.domain.card.CardKind;
import com.kobi.territory.sharing.domain.card.ShareCard;
import com.kobi.territory.sharing.domain.card.ShareCardId;
import com.kobi.territory.sharing.domain.showcase.CardComposer;
import com.kobi.territory.sharing.domain.showcase.ProvinceInfo;
import com.kobi.territory.sharing.domain.showcase.PublicVisits;
import com.kobi.territory.sharing.domain.showcase.RegionAtlas;
import com.kobi.territory.sharing.domain.showcase.RegionInfo;
import com.kobi.territory.sharing.domain.showcase.Showcase;
import com.kobi.territory.sharing.domain.showcase.ShowcaseProgress;
import com.kobi.territory.sharing.domain.showcase.ShowcaseScene;
import com.kobi.territory.sharing.domain.showcase.VisitFact;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** D1: ShareCard 기준(공개 요약 해시 + handle)·무효화·최소 TTL·handle 변경 TTL 면제(QA P2-1·P2-2), 요약 해시. */
class ShareCardTest {

    static final ExplorerId EXPLORER = ExplorerId.of(UUID.randomUUID().toString());
    static final String MAP = UUID.randomUUID().toString();
    static final Instant T0 = Instant.parse("2026-10-03T03:00:00Z");
    static final CardCachePolicy TEN_MINUTES = new CardCachePolicy(Duration.ofMinutes(10));
    static final ShareCardId ID = new ShareCardId(EXPLORER, MAP, CardKind.TERRITORY);
    static final RegionAtlas ATLAS = RegionAtlas.of(List.of(new RegionInfo("KR-11010", "종로구", "KR-11", "서울", Rarity.COMMON),
        new RegionInfo("KR-11020", "중구", "KR-11", "서울", Rarity.COMMON)), List.of(new ProvinceInfo("KR-11", "서울", 2)));

    static Showcase showcase(String handle, int level, String title, int owned, String... codes) {
        List<VisitFact> facts = IntStream.range(0, codes.length)
            .mapToObj(i -> new VisitFact(codes[i], LocalDate.parse("2026-10-01"), T0.plusSeconds(i))).toList();
        return new Showcase(handle, ATLAS, PublicVisits.of(facts, ATLAS), new ShowcaseProgress(level, title, 1, 0, 9),
            new ShowcaseScene(0, List.of(), owned));
    }

    static CardBasis basis(Showcase showcase) {
        return new CardBasis(showcase.summaryHash("TERRITORY@2026"), showcase.handle());
    }

    @Test
    void 그린_적_없으면_그린다_기준이_같으면_TTL이_지나도_다시_그리지_않는다() {
        CardBasis basis = basis(showcase("kim", 1, "초보", 1, "KR-11010"));
        ShareCard card = ShareCard.unrendered(ID);
        assertThat(card.needsRender(basis, T0, TEN_MINUTES)).isTrue();
        assertThat(card.markRendered(basis, ID.imageKey(basis), T0)).isNull();
        assertThat(card.needsRender(basis(showcase("kim", 1, "초보", 1, "KR-11010")), T0.plus(Duration.ofDays(3)), TEN_MINUTES)).isFalse();
    }

    @Test
    void 이벤트_없이_바뀐_진행_가방_칭호도_요약_해시가_바뀌어_낡음으로_잡히고_최소_TTL_뒤_다시_그린다() {
        CardBasis first = basis(showcase("kim", 1, "초보", 1, "KR-11010"));
        ShareCard card = ShareCard.unrendered(ID);
        card.markRendered(first, ID.imageKey(first), T0);

        // 병합 뒤 재계산(이벤트 없음): 레벨·가방 수가 바뀜 / 칭호만 바꿈 — 모두 기준이 달라진다(QA P2-1)
        CardBasis recalculated = basis(showcase("kim", 8, "초보", 46, "KR-11010", "KR-11020"));
        CardBasis titleOnly = basis(showcase("kim", 1, "골목 탐험가", 1, "KR-11010"));
        assertThat(card.stale(recalculated)).isTrue();
        assertThat(card.stale(titleOnly)).isTrue();
        assertThat(card.needsRender(recalculated, T0.plus(Duration.ofMinutes(9)), TEN_MINUTES)).isFalse();
        assertThat(card.needsRender(recalculated, T0.plus(Duration.ofMinutes(10)), TEN_MINUTES)).isTrue();

        String previous = card.imageKey();
        assertThat(card.markRendered(recalculated, ID.imageKey(recalculated), T0.plus(Duration.ofMinutes(10)))).isEqualTo(previous);
        assertThat(card.imageKey()).isNotEqualTo(previous).contains(recalculated.shortHash());
        assertThat(card.stale(recalculated)).isFalse();
    }

    @Test
    void handle_이_생기거나_바뀌면_TTL을_건너뛰고_바로_다시_그린다() {
        CardBasis anonymous = basis(showcase(null, 1, "초보", 1, "KR-11010"));
        ShareCard card = ShareCard.unrendered(ID);
        card.markRendered(anonymous, ID.imageKey(anonymous), T0);
        CardBasis linked = basis(showcase("kim", 1, "초보", 1, "KR-11010"));
        assertThat(card.needsRender(linked, T0.plusSeconds(1), TEN_MINUTES)).as("익명 → 계정").isTrue();
        card.markRendered(linked, ID.imageKey(linked), T0.plusSeconds(1));
        assertThat(card.needsRender(basis(showcase("kim2", 1, "초보", 1, "KR-11010")), T0.plusSeconds(2), TEN_MINUTES))
            .as("handle 변경").isTrue();
    }

    @Test
    void 요약_해시는_결정적이고_종류_연도_handle_방문일에_따라_달라진다() {
        Showcase base = showcase("kim", 1, "초보", 1, "KR-11010");
        assertThat(base.summaryHash("TERRITORY@2026")).isEqualTo(showcase("kim", 1, "초보", 1, "KR-11010").summaryHash("TERRITORY@2026"))
            .hasSize(64);
        assertThat(base.summaryHash("RECENT@2026")).isNotEqualTo(base.summaryHash("TERRITORY@2026"));
        assertThat(base.summaryHash("TERRITORY@2027")).isNotEqualTo(base.summaryHash("TERRITORY@2026"));
        assertThat(showcase("lee", 1, "초보", 1, "KR-11010").summaryHash("TERRITORY@2026"))
            .isNotEqualTo(base.summaryHash("TERRITORY@2026"));
    }

    @Test
    void VS_는_저장_식별을_갖지_않고_자기_자신과는_만들_수_없다() {
        assertThatThrownBy(() -> new ShareCardId(EXPLORER, MAP, CardKind.VS)).isInstanceOf(IllegalArgumentException.class);
        Showcase kim = showcase("kim", 1, "초보", 1, "KR-11010");
        assertThatThrownBy(() -> CardComposer.versus(kim, kim)).hasFieldOrPropertyWithValue("code", "PROFILE_NOT_FOUND");
        assertThat(CardKind.parseSolo("Territory")).isEqualTo(CardKind.TERRITORY);
        assertThatThrownBy(() -> CardKind.parseSolo("vs")).hasMessageContaining("모르는 카드");
    }
}
