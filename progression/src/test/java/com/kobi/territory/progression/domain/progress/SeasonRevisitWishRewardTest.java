package com.kobi.territory.progression.domain.progress;

import static com.kobi.territory.progression.domain.Fixtures.가평군;
import static com.kobi.territory.progression.domain.Fixtures.새_진행;
import static com.kobi.territory.progression.domain.Fixtures.서울정오;
import static com.kobi.territory.progression.domain.Fixtures.종로구;
import static com.kobi.territory.progression.domain.Fixtures.중구;
import static com.kobi.territory.progression.domain.Fixtures.진행규칙;
import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.progression.domain.policy.XpSource;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 게임 요소 2순위의 진행 보상(9단계) — 셋 다 회수 없고 한 번만: 계절 한정 테마 회차 완성 +150·칭호(계절마다 하나), 재방문 도장 +10·
 * "단골 여행자"(도장 5개), 가고 싶은 곳 다녀옴 +20·"꿈을 이룬 여행자"(3곳).
 */
@DisplayName("계절·재방문 도장·가고 싶은 곳 보상")
class SeasonRevisitWishRewardTest {

    private static final Instant 가을 = 서울정오(2026, 10, 10);
    private static final Instant 내년_봄 = 서울정오(2027, 4, 1);

    private static RegionCode 서울(int ordinal) {
        return RegionCode.of(String.format("KR-11%03d", 100 + ordinal));
    }

    @Nested
    @DisplayName("계절 회차를 완성하면")
    class SeasonCompleted {

        @Test
        @DisplayName("150 XP 와 그 계절의 칭호를 받는다")
        void rewards() {
            ExplorerProgress progress = 새_진행();

            ProgressChange change = progress.applySeasonCompleted("autumn-2026", 가을, 진행규칙);

            assertThat(change.xpDelta()).isEqualTo(150);
            assertThat(change.titlesEarned()).contains("season-autumn");
            assertThat(progress.seasonsCompleted()).containsEntry("autumn-2026", 가을);
        }

        @Test
        @DisplayName("같은 회차 소식이 다시 와도 한 번만 받는다")
        void once() {
            ExplorerProgress progress = 새_진행();
            progress.applySeasonCompleted("autumn-2026", 가을, 진행규칙);

            assertThat(progress.applySeasonCompleted("autumn-2026", 가을, 진행규칙).xpDelta()).isZero();
        }

        @Test
        @DisplayName("다음 해 회차를 또 완성하면 XP 는 다시 받지만 칭호는 하나다")
        void nextYear() {
            ExplorerProgress progress = 새_진행();
            progress.applySeasonCompleted("autumn-2026", 가을, 진행규칙);

            ProgressChange change = progress.applySeasonCompleted("autumn-2027", 서울정오(2027, 10, 10), 진행규칙);

            assertThat(change.xpDelta()).isEqualTo(150);
            assertThat(change.titlesEarned()).isEmpty();
            assertThat(progress.titles()).containsKey("season-autumn");
        }
    }

    @Nested
    @DisplayName("재방문 도장을 받으면")
    class Revisit {

        @Test
        @DisplayName("10 XP 를 받고 같은 지역·연도는 한 번만이다")
        void rewards() {
            ExplorerProgress progress = 새_진행();

            assertThat(progress.applyRevisitStamp(종로구, 2027, 내년_봄, 진행규칙).xpDelta()).isEqualTo(10);
            assertThat(progress.applyRevisitStamp(종로구, 2027, 내년_봄, 진행규칙).xpDelta()).isZero();
            assertThat(progress.applyRevisitStamp(종로구, 2028, 서울정오(2028, 4, 1), 진행규칙).xpDelta()).isEqualTo(10);
            assertThat(progress.revisitStampCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("도장이 다섯 개가 되면 단골 여행자 뱃지를 받는다")
        void badge() {
            ExplorerProgress progress = 새_진행();
            for (int i = 1; i <= 4; i++) progress.applyRevisitStamp(서울(i), 2027, 내년_봄, 진행규칙);

            ProgressChange fifth = progress.applyRevisitStamp(서울(5), 2027, 내년_봄, 진행규칙);

            assertThat(fifth.badgesEarned()).containsExactly("revisit5");
        }
    }

    @Nested
    @DisplayName("가고 싶은 곳을 다녀오면")
    class Wish {

        @Test
        @DisplayName("20 XP 를 받고 같은 지역은 핀을 다시 꽂아 또 다녀와도 한 번만이다")
        void rewards() {
            ExplorerProgress progress = 새_진행();

            assertThat(progress.applyWishFulfilled(가평군, 가을, 진행규칙).xpDelta()).isEqualTo(20);
            assertThat(progress.applyWishFulfilled(가평군, 서울정오(2027, 1, 1), 진행규칙).xpDelta()).isZero();
            assertThat(progress.wishesFulfilledCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("세 곳을 다녀오면 꿈을 이룬 여행자 뱃지를 받는다")
        void badge() {
            ExplorerProgress progress = 새_진행();
            progress.applyWishFulfilled(종로구, 가을, 진행규칙);
            progress.applyWishFulfilled(중구, 가을, 진행규칙);

            assertThat(progress.applyWishFulfilled(가평군, 가을, 진행규칙).badgesEarned()).containsExactly("wish3");
        }
    }

    @Nested
    @DisplayName("다시 셀 때 기록만 있고 보상이 없으면")
    class Recover {

        @Test
        @DisplayName("계절 완성·도장·다녀온 곳의 보상을 그 기록 시각으로 채우고 이미 있는 것은 그대로 둔다")
        void recovers() {
            ExplorerProgress progress = 새_진행();
            progress.applyRevisitStamp(종로구, 2027, 내년_봄, 진행규칙);

            progress.recoverRecords(List.of("autumn-2026"), Map.of("autumn-2026", 가을),
                List.of(new StampFact(종로구, 2027, 내년_봄), new StampFact(중구, 2027, 내년_봄)),
                List.of(new WishFact(가평군, 가을)), 서울정오(2027, 5, 1), 진행규칙);

            assertThat(progress.ledger().count(XpSource.REVISIT_STAMP)).isEqualTo(2);
            assertThat(progress.ledger().find(RefIds.season(progress.explorerId(), "autumn-2026")).orElseThrow().at()).isEqualTo(가을);
            assertThat(progress.wishesFulfilledCount()).isEqualTo(1);
            assertThat(progress.titles()).containsKey("season-autumn");
        }
    }
}
