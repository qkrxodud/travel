package com.kobi.territory.progression.domain.collectionbook;

import static com.kobi.territory.progression.domain.Fixtures.FRIEND;
import static com.kobi.territory.progression.domain.Fixtures.GAPYEONG;
import static com.kobi.territory.progression.domain.Fixtures.JONGNO;
import static com.kobi.territory.progression.domain.Fixtures.JUNG;
import static com.kobi.territory.progression.domain.Fixtures.MAP;
import static com.kobi.territory.progression.domain.Fixtures.ME;
import static com.kobi.territory.progression.domain.Fixtures.THEMES;
import static com.kobi.territory.progression.domain.Fixtures.T0;
import static com.kobi.territory.progression.domain.Fixtures.ULLEUNG;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 도감(지도 단위): 완성 1회, 취소 후 완성 기록 유지, 지도에 남은 지역은 빼지 않음(D2). */
class CollectionBookTest {

    @Test
    void 정의된_지역이_모두_모이면_한_번만_완성된다() {
        CollectionBook book = CollectionBook.empty(MAP);
        assertThat(book.applyVisit(JONGNO, ME, T0, THEMES)).isEmpty();
        var done = book.applyVisit(JUNG, ME, T0.plusSeconds(1), THEMES);
        assertThat(done).extracting(ThemeCompletion::themeId).containsExactly("han");
        assertThat(done.get(0).completedBy()).isEqualTo(ME);
        assertThat(book.progressOf("han").completedAt()).isEqualTo(T0.plusSeconds(1));
        assertThat(book.progressOf("mix").have()).isEqualTo(1);
        // 같은 지역 재전달 → 변화 없음
        assertThat(book.applyVisit(JUNG, ME, T0.plusSeconds(2), THEMES)).isEmpty();
        assertThat(book.completedCount()).isEqualTo(1);
    }

    @Test
    void 완성_후_취소해도_완성_기록은_유지되고_다시_모아도_재완성되지_않는다() {
        CollectionBook book = CollectionBook.empty(MAP);
        book.applyVisit(JONGNO, ME, T0, THEMES);
        book.applyVisit(JUNG, ME, T0.plusSeconds(1), THEMES);
        book.revokeVisit(JUNG, false, THEMES);
        assertThat(book.progressOf("han").have()).isEqualTo(1);
        assertThat(book.progressOf("han").completed()).isTrue();
        assertThat(book.progressOf("han").completedAt()).isEqualTo(T0.plusSeconds(1));
        assertThat(book.applyVisit(JUNG, ME, T0.plusSeconds(5), THEMES)).isEmpty();
        assertThat(book.progressOf("han").completedAt()).isEqualTo(T0.plusSeconds(1));
    }

    @Test
    void 지도에_다른_멤버_방문으로_남은_지역은_진행에서_빼지_않는다() {
        CollectionBook book = CollectionBook.empty(MAP);
        book.applyVisit(JONGNO, ME, T0, THEMES);
        book.applyVisit(JONGNO, FRIEND, T0.plusSeconds(1), THEMES);
        book.revokeVisit(JONGNO, true, THEMES);
        assertThat(book.progressOf("han").have()).isEqualTo(1);
        book.revokeVisit(JONGNO, false, THEMES);
        assertThat(book.progressOf("han").have()).isZero();
    }

    @Test
    void 지도_기준이라_멤버가_달라도_모이면_완성이고_세트에_없는_지역은_무시() {
        CollectionBook book = CollectionBook.empty(MAP);
        book.applyVisit(JONGNO, FRIEND, T0, THEMES);
        assertThat(book.applyVisit(JUNG, ME, T0.plusSeconds(1), THEMES)).extracting(ThemeCompletion::completedBy)
            .containsExactly(ME);
        assertThat(book.applyVisit(ULLEUNG, ME, T0, THEMES)).isEmpty();
        assertThat(book.applyVisit(GAPYEONG, ME, T0, THEMES)).extracting(ThemeCompletion::themeId).containsExactly("mix");
    }
}
