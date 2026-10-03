package com.kobi.territory.progression.domain.collectionbook;

import static com.kobi.territory.progression.domain.Fixtures.가평군;
import static com.kobi.territory.progression.domain.Fixtures.기준시각;
import static com.kobi.territory.progression.domain.Fixtures.나;
import static com.kobi.territory.progression.domain.Fixtures.늦게온멤버;
import static com.kobi.territory.progression.domain.Fixtures.울릉군;
import static com.kobi.territory.progression.domain.Fixtures.종로구;
import static com.kobi.territory.progression.domain.Fixtures.중구;
import static com.kobi.territory.progression.domain.Fixtures.지도;
import static com.kobi.territory.progression.domain.Fixtures.초;
import static com.kobi.territory.progression.domain.Fixtures.친구;
import static com.kobi.territory.progression.domain.Fixtures.테마;
import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.common.model.ExplorerId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 도감(지도 단위 테마 진행). 테마 "han" = 종로구 + 중구, "mix" = 중구 + 가평군.
 * 회귀 출처: 2단계 D2(지도에 남은 지역), 3단계 결정 1(완성 시점 멤버 전원 수령).
 */
@DisplayName("도감")
class CollectionBookTest {

    private static final List<ExplorerId> 나와친구 = List.of(나, 친구);

    /** 종로구·중구를 내가 칠해 "han"이 완성된 개인 지도 도감. */
    private static CollectionBook 한강완성() {
        CollectionBook book = CollectionBook.empty(지도);
        book.applyVisit(종로구, 나, 기준시각, 테마);
        book.applyVisit(중구, 나, 초(1), 테마);
        return book;
    }

    /** 친구가 종로구, 내가 중구를 칠해 "han"이 완성된 공유 지도 도감(멤버 = 나와 친구). */
    private static CollectionBook 함께한강완성() {
        CollectionBook book = CollectionBook.empty(지도);
        book.applyVisit(종로구, 친구, 기준시각, 테마, 나와친구);
        book.applyVisit(중구, 나, 초(1), 테마, 나와친구);
        return book;
    }

    @Nested
    @DisplayName("지도에 지역이 칠해지면")
    class Paint {

        @Test
        @DisplayName("테마의 지역이 모두 모이는 순간 완성된다")
        void completesWhenAllCollected() {
            CollectionBook book = CollectionBook.empty(지도);

            assertThat(book.applyVisit(종로구, 나, 기준시각, 테마)).isEmpty();
            List<ThemeCompletion> done = book.applyVisit(중구, 나, 초(1), 테마);

            assertThat(done).extracting(ThemeCompletion::themeId).containsExactly("han");
            assertThat(book.progressOf("han").completedAt()).isEqualTo(초(1));
        }

        @Test
        @DisplayName("같은 지역을 모으는 다른 테마도 함께 진행된다")
        void otherThemesProgress() {
            assertThat(한강완성().progressOf("mix").have()).isEqualTo(1);
        }

        @Test
        @DisplayName("같은 지역이 다시 와도 다시 완성되지 않는다")
        void completesOnlyOnce() {
            CollectionBook book = 한강완성();

            assertThat(book.applyVisit(중구, 나, 초(2), 테마)).isEmpty();
            assertThat(book.completedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("누가 칠했든 지도에 모이면 완성되고 마지막 지역을 칠한 사람이 완성자다")
        void mapWideCompletion() {
            CollectionBook book = CollectionBook.empty(지도);
            book.applyVisit(종로구, 친구, 기준시각, 테마);

            assertThat(book.applyVisit(중구, 나, 초(1), 테마)).extracting(ThemeCompletion::completedBy).containsExactly(나);
        }

        @Test
        @DisplayName("어느 테마에도 없는 지역은 진행에 영향이 없다")
        void regionOutsideThemes() {
            assertThat(CollectionBook.empty(지도).applyVisit(울릉군, 나, 기준시각, 테마)).isEmpty();
        }

        @Test
        @DisplayName("다른 테마의 마지막 지역을 칠하면 그 테마가 완성된다")
        void anotherThemeCompletes() {
            CollectionBook book = 한강완성();

            assertThat(book.applyVisit(가평군, 나, 초(2), 테마)).extracting(ThemeCompletion::themeId).containsExactly("mix");
        }
    }

    @Nested
    @DisplayName("테마가 완성되면 보상을 받는 사람은")
    class Recipients {

        @Test
        @DisplayName("완성 시점의 지도 멤버 전원이다")
        void allMembersAtCompletion() {
            CollectionBook book = CollectionBook.empty(지도);
            book.applyVisit(종로구, 친구, 기준시각, 테마, 나와친구);

            List<ThemeCompletion> done = book.applyVisit(중구, 나, 초(1), 테마, 나와친구);

            assertThat(done).singleElement().satisfies(completion -> {
                assertThat(completion.completedBy()).isEqualTo(나);
                assertThat(completion.recipients()).containsExactlyInAnyOrder(나, 친구);
            });
        }

        @Test
        @DisplayName("완성 시점 멤버는 기록으로 남아 직접 칠하지 않은 멤버도 보상 대상이다")
        void recordedRecipients() {
            CollectionBook book = 함께한강완성();

            assertThat(book.progressOf("han").completedMembers()).containsExactlyInAnyOrder(나, 친구);
            assertThat(book.themeIdsRewardedTo(친구)).containsExactly("han");
        }

        @Test
        @DisplayName("완성 뒤에 들어온 멤버는 받지 않는다")
        void lateMemberExcluded() {
            assertThat(함께한강완성().themeIdsRewardedTo(늦게온멤버)).isEmpty();
        }

        @Test
        @DisplayName("멤버 목록이 없던 예전 소식이면 칠한 사람만 받는다")
        void legacyEventVisitorOnly() {
            CollectionBook book = CollectionBook.empty(지도);
            book.applyVisit(종로구, 나, 기준시각, 테마, List.of());

            assertThat(book.applyVisit(중구, 나, 초(1), 테마, List.of()).get(0).recipients()).containsExactly(나);
        }

        @Test
        @DisplayName("혼자인 개인 지도에서는 칠한 사람 혼자 받는다")
        void personalMapVisitorOnly() {
            CollectionBook book = CollectionBook.empty(지도);
            book.applyVisit(종로구, 나, 기준시각, 테마);

            assertThat(book.applyVisit(중구, 나, 초(1), 테마).get(0).recipients()).containsExactly(나);
        }
    }

    @Nested
    @DisplayName("칠한 지역이 취소되면")
    class Cancel {

        @Test
        @DisplayName("지도에서 사라진 지역은 진행에서 빠진다")
        void regionLeavesProgress() {
            CollectionBook book = 한강완성();

            book.revokeVisit(중구, false, 테마);

            assertThat(book.progressOf("han").have()).isEqualTo(1);
        }

        @Test
        @DisplayName("완성 기록과 완성 시각은 그대로 남는다")
        void completionStays() {
            CollectionBook book = 한강완성();

            book.revokeVisit(중구, false, 테마);

            assertThat(book.progressOf("han").completed()).isTrue();
            assertThat(book.progressOf("han").completedAt()).isEqualTo(초(1));
        }

        @Test
        @DisplayName("다시 모아도 두 번 완성되지 않는다")
        void noSecondCompletion() {
            CollectionBook book = 한강완성();
            book.revokeVisit(중구, false, 테마);

            assertThat(book.applyVisit(중구, 나, 초(5), 테마)).isEmpty();
            assertThat(book.progressOf("han").completedAt()).isEqualTo(초(1));
        }

        @Test
        @DisplayName("다른 멤버의 방문으로 지도에 남아 있으면 진행에서 빼지 않는다")
        void stillOnMap() {
            CollectionBook book = CollectionBook.empty(지도);
            book.applyVisit(종로구, 나, 기준시각, 테마);
            book.applyVisit(종로구, 친구, 초(1), 테마);

            book.revokeVisit(종로구, true, 테마);

            assertThat(book.progressOf("han").have()).isEqualTo(1);
        }

        @Test
        @DisplayName("지도에 남았던 방문까지 취소되면 그때 진행에서 빠진다")
        void lastVisitGone() {
            CollectionBook book = CollectionBook.empty(지도);
            book.applyVisit(종로구, 나, 기준시각, 테마);
            book.applyVisit(종로구, 친구, 초(1), 테마);
            book.revokeVisit(종로구, true, 테마);

            book.revokeVisit(종로구, false, 테마);

            assertThat(book.progressOf("han").have()).isZero();
        }
    }

    @Nested
    @DisplayName("멤버가 떠나 그 멤버의 지역이 지도에서 사라지면")
    class MemberLeft {

        @Test
        @DisplayName("사라진 지역은 진행에서 빠진다")
        void regionLeaves() {
            CollectionBook book = 함께한강완성();

            book.revokeRegions(List.of(중구), 테마);

            assertThat(book.progressOf("han").holds(중구)).isFalse();
        }

        @Test
        @DisplayName("완성 기록은 남는다")
        void completionStays() {
            CollectionBook book = 함께한강완성();

            book.revokeRegions(List.of(중구), 테마);

            assertThat(book.progressOf("han").completed()).isTrue();
        }

        @Test
        @DisplayName("같은 소식이 다시 와도 한 번만 빠진다")
        void idempotent() {
            CollectionBook book = 함께한강완성();
            book.revokeRegions(List.of(중구), 테마);

            book.revokeRegions(List.of(중구), 테마);

            assertThat(book.progressOf("han").have()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("떠난 멤버가 돌아와 지역이 되살아나면")
    class MemberRejoined {

        @Test
        @DisplayName("그로 인해 처음 완성되는 테마는 돌아온 시점의 멤버 전원이 받는다")
        void restoreCompletesForCurrentMembers() {
            CollectionBook book = CollectionBook.empty(지도);
            book.applyVisit(중구, 나, 기준시각, 테마, 나와친구);

            List<ThemeCompletion> done = book.restoreRegions(List.of(가평군), 친구, 초(5), 테마, List.of(나, 친구, 늦게온멤버));

            assertThat(done).extracting(ThemeCompletion::themeId).containsExactly("mix");
            assertThat(done.get(0).recipients()).containsExactlyInAnyOrder(나, 친구, 늦게온멤버);
        }
    }

    @Nested
    @DisplayName("재계산 출발점을 만들면")
    class RebuildBase {

        @Test
        @DisplayName("모은 지역은 비우고 완성 기록은 남긴다")
        void clearsCollectedKeepsCompletion() {
            CollectionBook base = 한강완성().rebuildBase();

            assertThat(base.progressOf("han").have()).isZero();
            assertThat(base.progressOf("han").completedAt()).isEqualTo(초(1));
        }
    }
}
