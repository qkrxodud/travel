package com.kobi.territory.progression.domain.collectionbook;

import static com.kobi.territory.progression.domain.Fixtures.계절;
import static com.kobi.territory.progression.domain.Fixtures.나;
import static com.kobi.territory.progression.domain.Fixtures.서울정오;
import static com.kobi.territory.progression.domain.Fixtures.종로구;
import static com.kobi.territory.progression.domain.Fixtures.중구;
import static com.kobi.territory.progression.domain.Fixtures.지도;
import static com.kobi.territory.progression.domain.Fixtures.친구;
import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 계절 한정 테마(도감 — 지도 단위). 가을 "autumn" = 종로구 + 중구(10/1~11/30). 그 회차 기간 안에 처리된 체크인만 세고, 완성 시점 멤버 전원이
 * 보상을 받으며, 기간이 끝나면 미완성 진행도 닫힌다.
 */
@DisplayName("계절 한정 테마")
class SeasonProgressTest {

    private static final String 가을2026 = "autumn-2026";
    private static final List<ExplorerId> 나와친구 = List.of(나, 친구);

    private static Instant 가을(int day) {
        return 서울정오(2026, 10, day);
    }

    @Nested
    @DisplayName("기간 안에 칠하면")
    class InSeason {

        @Test
        @DisplayName("회차 지역이 모두 모이는 순간 완성되고 완성 시점 멤버 전원이 받는다")
        void completes() {
            CollectionBook book = CollectionBook.empty(지도);

            assertThat(book.applySeasonVisit(종로구, 친구, 가을(4), 계절, 나와친구)).isEmpty();
            List<SeasonCompletion> done = book.applySeasonVisit(중구, 나, 가을(5), 계절, 나와친구);

            assertThat(done).singleElement().satisfies(completion -> {
                assertThat(completion.roundId()).isEqualTo(가을2026);
                assertThat(completion.seasonId()).isEqualTo("autumn");
                assertThat(completion.recipients()).containsExactlyInAnyOrder(나, 친구);
                assertThat(completion.completedAt()).isEqualTo(가을(5));
            });
            assertThat(book.seasonProgressOf(가을2026).rewardedTo(친구)).isTrue();
        }

        @Test
        @DisplayName("완성은 회차마다 한 번이다")
        void once() {
            CollectionBook book = CollectionBook.empty(지도);
            book.applySeasonVisit(종로구, 나, 가을(4), 계절, List.of(나));
            book.applySeasonVisit(중구, 나, 가을(5), 계절, List.of(나));

            assertThat(book.applySeasonVisit(중구, 친구, 가을(6), 계절, 나와친구)).isEmpty();
            assertThat(book.seasonProgressOf(가을2026).completedAt()).isEqualTo(가을(5));
        }

        @Test
        @DisplayName("회차에 없는 지역은 세지 않는다")
        void otherRegion() {
            CollectionBook book = CollectionBook.empty(지도);
            book.applySeasonVisit(RegionCode.of("KR-31370"), 나, 가을(4), 계절, List.of(나));
            assertThat(book.seasonProgresses()).isEmpty();
        }
    }

    @Nested
    @DisplayName("기간 밖의 체크인은")
    class OutOfSeason {

        @Test
        @DisplayName("기간 전에 칠한 지역은 세지 않는다 — 소급 없음")
        void beforeSeason() {
            CollectionBook book = CollectionBook.empty(지도);
            book.applySeasonVisit(종로구, 나, 서울정오(2026, 9, 30), 계절, List.of(나));
            book.applySeasonVisit(중구, 나, 가을(4), 계절, List.of(나));

            assertThat(book.seasonProgressOf(가을2026).have()).isEqualTo(1);
            assertThat(book.seasonProgressOf(가을2026).completed()).isFalse();
        }

        @Test
        @DisplayName("기간이 끝난 뒤 칠해도 닫힌 회차는 그대로다")
        void afterSeason() {
            CollectionBook book = CollectionBook.empty(지도);
            book.applySeasonVisit(종로구, 나, 가을(4), 계절, List.of(나));

            book.applySeasonVisit(중구, 나, 서울정오(2026, 12, 2), 계절, List.of(나));

            assertThat(book.seasonProgressOf(가을2026).have()).isEqualTo(1);
            assertThat(book.seasonProgressOf(가을2026).completed()).isFalse();
        }

        @Test
        @DisplayName("다음 해 회차는 0부터 시작한다")
        void nextYearFromZero() {
            CollectionBook book = CollectionBook.empty(지도);
            book.applySeasonVisit(종로구, 나, 가을(4), 계절, List.of(나));

            book.applySeasonVisit(중구, 나, 서울정오(2027, 10, 3), 계절, List.of(나));

            assertThat(book.seasonProgressOf("autumn-2027").have()).isEqualTo(1);
            assertThat(book.seasonProgressOf(가을2026).have()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("취소·탈퇴·재가입")
    class Revoke {

        @Test
        @DisplayName("기간 안에 취소하면 그 방문은 빠지지만 같은 지역을 기간 안에 칠한 다른 멤버가 있으면 진행에 남는다")
        void cancelInSeason() {
            CollectionBook book = CollectionBook.empty(지도);
            book.applySeasonVisit(종로구, 나, 가을(4), 계절, 나와친구);
            book.applySeasonVisit(종로구, 친구, 가을(5), 계절, 나와친구);

            book.revokeSeasonVisit(종로구, 나, 가을(6), 계절);
            assertThat(book.seasonProgressOf(가을2026).have()).isEqualTo(1);

            book.revokeSeasonVisit(종로구, 친구, 가을(7), 계절);
            assertThat(book.seasonProgressOf(가을2026).have()).isZero();
        }

        @Test
        @DisplayName("기간 전에 칠해 둔 멤버가 남아 있어도 기간 안에 칠한 방문이 취소되면 진행에서 빠진다")
        void earlierVisitDoesNotCount() {
            CollectionBook book = CollectionBook.empty(지도);
            book.applySeasonVisit(종로구, 친구, 서울정오(2026, 9, 1), 계절, 나와친구);
            book.applySeasonVisit(종로구, 나, 가을(4), 계절, 나와친구);

            book.revokeSeasonVisit(종로구, 나, 가을(5), 계절);

            assertThat(book.seasonProgressOf(가을2026).have()).isZero();
        }

        @Test
        @DisplayName("완성 뒤 취소해도 완성 기록은 남는다")
        void completionStays() {
            CollectionBook book = CollectionBook.empty(지도);
            book.applySeasonVisit(종로구, 나, 가을(4), 계절, List.of(나));
            book.applySeasonVisit(중구, 나, 가을(5), 계절, List.of(나));

            book.revokeSeasonVisit(중구, 나, 가을(6), 계절);

            assertThat(book.seasonProgressOf(가을2026).completed()).isTrue();
            assertThat(book.seasonProgressOf(가을2026).have()).isEqualTo(1);
        }

        @Test
        @DisplayName("회차가 닫힌 뒤의 취소는 닫힌 기록을 바꾸지 않는다")
        void cancelAfterClose() {
            CollectionBook book = CollectionBook.empty(지도);
            book.applySeasonVisit(종로구, 나, 가을(4), 계절, List.of(나));

            book.revokeSeasonVisit(종로구, 나, 서울정오(2026, 12, 3), 계절);

            assertThat(book.seasonProgressOf(가을2026).have()).isEqualTo(1);
        }

        @Test
        @DisplayName("탈퇴로 숨기면 그 멤버가 센 방문이 빠지고 재가입하면 기간 안에 칠했던 방문만 돌아온다")
        void hideAndRestore() {
            CollectionBook book = CollectionBook.empty(지도);
            book.applySeasonVisit(종로구, 친구, 가을(4), 계절, 나와친구);
            book.revokeSeasonMember(친구, List.of(종로구, 중구), 가을(5), 계절);
            assertThat(book.seasonProgressOf(가을2026).have()).isZero();

            book.restoreSeasonVisits(Map.of(종로구, 가을(4), 중구, 서울정오(2026, 9, 1)), 친구, 가을(6), 계절, 나와친구);

            assertThat(book.seasonProgressOf(가을2026).collected()).containsExactly(종로구);
        }

        @Test
        @DisplayName("재가입 복구로 회차가 완성되면 복구 시점 멤버가 받는다")
        void restoreCompletes() {
            CollectionBook book = CollectionBook.empty(지도);
            book.applySeasonVisit(종로구, 나, 가을(4), 계절, 나와친구);

            List<SeasonCompletion> done = book.restoreSeasonVisits(Map.of(중구, 가을(3)), 친구, 가을(6), 계절, 나와친구);

            assertThat(done).singleElement().satisfies(completion -> {
                assertThat(completion.completedAt()).isEqualTo(가을(6));
                assertThat(completion.recipients()).containsExactlyInAnyOrder(나, 친구);
            });
        }

        @Test
        @DisplayName("계정 병합으로 멤버가 바뀌면 센 방문의 주인도 바뀌어 새 주인이 취소할 때 빠진다")
        void reassigned() {
            CollectionBook book = CollectionBook.empty(지도);
            book.applySeasonVisit(종로구, 친구, 가을(4), 계절, 나와친구);

            book.reassignSeasonMember(친구, 나, 가을(5), 계절);
            book.revokeSeasonVisit(종로구, 나, 가을(6), 계절);

            assertThat(book.seasonProgressOf(가을2026).have()).isZero();
        }
    }

    @Nested
    @DisplayName("다시 셀 때")
    class Rebuild {

        @Test
        @DisplayName("아직 열린 회차는 센 방문만 비우고 완성 기록을 남긴다")
        void openRoundCleared() {
            CollectionBook book = CollectionBook.empty(지도);
            book.applySeasonVisit(종로구, 나, 가을(4), 계절, List.of(나));
            book.applySeasonVisit(중구, 나, 가을(5), 계절, List.of(나));

            CollectionBook base = book.rebuildBase(계절, 가을(20));

            assertThat(base.seasonProgressOf(가을2026).have()).isZero();
            assertThat(base.seasonProgressOf(가을2026).completedAt()).isEqualTo(가을(5));
        }

        @Test
        @DisplayName("닫힌 회차는 확정 기록이라 그대로 둔다")
        void closedRoundKept() {
            CollectionBook book = CollectionBook.empty(지도);
            book.applySeasonVisit(종로구, 나, 가을(4), 계절, List.of(나));

            CollectionBook base = book.rebuildBase(계절, 서울정오(2027, 1, 10));

            assertThat(base.seasonProgressOf(가을2026).have()).isEqualTo(1);
        }
    }
}
