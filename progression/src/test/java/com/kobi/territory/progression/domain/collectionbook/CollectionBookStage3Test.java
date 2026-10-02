package com.kobi.territory.progression.domain.collectionbook;

import static com.kobi.territory.progression.domain.Fixtures.FRIEND;
import static com.kobi.territory.progression.domain.Fixtures.GAPYEONG;
import static com.kobi.territory.progression.domain.Fixtures.JONGNO;
import static com.kobi.territory.progression.domain.Fixtures.JUNG;
import static com.kobi.territory.progression.domain.Fixtures.MAP;
import static com.kobi.territory.progression.domain.Fixtures.ME;
import static com.kobi.territory.progression.domain.Fixtures.T0;
import static com.kobi.territory.progression.domain.Fixtures.THEMES;
import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.common.model.ExplorerId;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 3단계: 테마 완성 수령자 = 완성 시점 지도 멤버 전원(결정 1), 탈퇴 숨김·재가입 복구. */
class CollectionBookStage3Test {

    static final ExplorerId LATE = ExplorerId.of("55555555-5555-5555-5555-555555555555");

    @Test
    void 완성_수령자는_완성_시점_멤버_전원이다() {
        CollectionBook book = CollectionBook.empty(MAP);
        book.applyVisit(JONGNO, FRIEND, T0, THEMES, List.of(ME, FRIEND));
        List<ThemeCompletion> done = book.applyVisit(JUNG, ME, T0.plusSeconds(1), THEMES, List.of(ME, FRIEND));
        assertThat(done).singleElement().satisfies(completion -> {
            assertThat(completion.completedBy()).isEqualTo(ME);
            assertThat(completion.recipients()).containsExactlyInAnyOrder(ME, FRIEND);
        });
        assertThat(book.themeIdsRewardedTo(FRIEND)).containsExactly("han");
        assertThat(book.themeIdsRewardedTo(LATE)).isEmpty(); // 나중 합류 멤버는 수령자가 아니다
        assertThat(book.progressOf("han").completedMembers()).containsExactlyInAnyOrder(ME, FRIEND);
    }

    @Test
    void 멤버_목록이_없던_예전_이벤트는_칠한_사람만_수령자() {
        CollectionBook book = CollectionBook.empty(MAP);
        book.applyVisit(JONGNO, ME, T0, THEMES, List.of());
        assertThat(book.applyVisit(JUNG, ME, T0.plusSeconds(1), THEMES, List.of()).get(0).recipients()).containsExactly(ME);
    }

    @Test
    void 탈퇴로_사라진_지역은_진행에서_빠지고_완성_기록은_남는다() {
        CollectionBook book = CollectionBook.empty(MAP);
        book.applyVisit(JONGNO, ME, T0, THEMES, List.of(ME, FRIEND));
        book.applyVisit(JUNG, FRIEND, T0.plusSeconds(1), THEMES, List.of(ME, FRIEND));
        book.revokeRegions(List.of(JUNG), THEMES);
        assertThat(book.progressOf("han").holds(JUNG)).isFalse();
        assertThat(book.progressOf("han").completed()).isTrue();
        book.revokeRegions(List.of(JUNG), THEMES); // 재전달 멱등
        assertThat(book.progressOf("han").have()).isEqualTo(1);
    }

    @Test
    void 재가입_복구로_테마가_처음_완성되면_복구_시점_멤버가_수령자다() {
        CollectionBook book = CollectionBook.empty(MAP);
        book.applyVisit(JUNG, ME, T0, THEMES, List.of(ME, FRIEND));
        List<ThemeCompletion> done = book.restoreRegions(List.of(GAPYEONG), FRIEND, T0.plusSeconds(5), THEMES,
            List.of(ME, FRIEND, LATE));
        assertThat(done).extracting(ThemeCompletion::themeId).containsExactly("mix");
        assertThat(done.get(0).recipients()).containsExactlyInAnyOrder(ME, FRIEND, LATE);
    }
}
