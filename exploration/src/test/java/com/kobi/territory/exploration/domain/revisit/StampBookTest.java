package com.kobi.territory.exploration.domain.revisit;

import static com.kobi.territory.exploration.domain.Fixtures.JONGNO;
import static com.kobi.territory.exploration.domain.Fixtures.JUNG;
import static com.kobi.territory.exploration.domain.Fixtures.KST;
import static com.kobi.territory.exploration.domain.Fixtures.ACCOUNT;
import static com.kobi.territory.exploration.domain.Fixtures.ME;
import static com.kobi.territory.exploration.domain.Fixtures.NOON;
import static com.kobi.territory.exploration.domain.Fixtures.POLICY;
import static com.kobi.territory.exploration.domain.Fixtures.refusal;
import static com.kobi.territory.exploration.domain.Fixtures.seoul;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.domain.ExplorationError;
import com.kobi.territory.exploration.domain.territory.CheckInContext;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 재방문 도장 — 이미 칠한 지역에 처음 칠한 해보다 뒤의 해에 "다시 다녀왔어요"를 누르면 그 해 도장 하나. 날짜는 처리 시각(서울 시각) 기준,
 * 하루 체크인 상한을 개인 지도 체크인과 함께 쓴다.
 */
@DisplayName("재방문 도장")
class StampBookTest {

    /** 2026-10-02 정오에 처음 칠했다. */
    private static final Optional<Instant> 올해_칠함 = Optional.of(NOON);
    private static final Optional<Instant> 안_칠함 = Optional.empty();
    private static final RegionCode 종로구 = JONGNO.code();
    private static final RegionCode 중구 = JUNG.code();

    /** 서울 시각 그 날짜·시각. */
    private static Instant 서울(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).atZone(KST).toInstant();
    }

    /** 가입한 지 오래된(온보딩이 끝난) 탐험가가 그 시각에 누른다. */
    private static CheckInContext 그때(Instant now) {
        return new CheckInContext(POLICY, NOON.minus(Duration.ofDays(30)), now, KST);
    }

    private static final Instant 내년_봄 = 서울(2027, 3, 1, 12, 0);

    @Nested
    @DisplayName("처음 칠한 해보다 뒤의 해에 누르면")
    class NextYear {

        @Test
        @DisplayName("그 해 도장을 하나 받는다")
        void stamped() {
            StampBook book = StampBook.empty(ME);

            StampResult result = book.stamp(종로구, 올해_칠함, 0, 그때(내년_봄));

            assertThat(result.stamp()).isEqualTo(new RevisitStamp(종로구, 2027, 내년_봄));
            assertThat(result.firstYear()).isEqualTo(2026);
            assertThat(result.stampCount()).isEqualTo(1);
            assertThat(book.stamps().yearsOf(종로구)).containsExactly(2027);
        }

        @Test
        @DisplayName("같은 해에 또 누르면 받지 못하고 내년부터 받을 수 있다고 안내한다")
        void oncePerYear() {
            StampBook book = StampBook.empty(ME);
            book.stamp(종로구, 올해_칠함, 0, 그때(내년_봄));

            StampEligibility again = book.judge(종로구, 올해_칠함, 0, 그때(내년_봄.plus(Duration.ofDays(30))));

            assertThat(again.refusal()).contains(StampRefusal.ALREADY_STAMPED);
            assertThat(again.availableFromYear()).contains(2028);
            assertThat(refusal(() -> book.stamp(종로구, 올해_칠함, 0, 그때(내년_봄.plus(Duration.ofDays(30))))))
                .isEqualTo(ExplorationError.REVISIT_ALREADY_STAMPED);
        }

        @Test
        @DisplayName("해가 바뀌면 같은 지역에 새 도장을 또 받는다")
        void everyYear() {
            StampBook book = StampBook.empty(ME);
            book.stamp(종로구, 올해_칠함, 0, 그때(내년_봄));

            book.stamp(종로구, 올해_칠함, 0, 그때(서울(2028, 5, 5, 9, 0)));

            assertThat(book.stamps().yearsOf(종로구)).containsExactly(2027, 2028);
            assertThat(book.stamps().count()).isEqualTo(2);
        }

        @Test
        @DisplayName("해는 서울 시각으로 가른다 — 1월 1일 0시 10분이면 새해 도장이다")
        void seoulNewYear() {
            Instant 새해_직후 = 서울(2027, 1, 1, 0, 10);

            StampEligibility eligibility = StampBook.empty(ME).judge(종로구, 올해_칠함, 0, 그때(새해_직후));

            assertThat(eligibility.year()).isEqualTo(2027);
            assertThat(eligibility.stampable()).isTrue();
        }
    }

    @Nested
    @DisplayName("받을 수 없을 때")
    class Refused {

        @Test
        @DisplayName("처음 칠한 해와 같은 해라면 받지 못하고 다음 해부터라고 안내한다")
        void sameYear() {
            StampBook book = StampBook.empty(ME);

            StampEligibility eligibility = book.judge(종로구, 올해_칠함, 0, 그때(서울(2026, 12, 31, 23, 50)));

            assertThat(eligibility.refusal()).contains(StampRefusal.SAME_YEAR);
            assertThat(eligibility.availableFromYear()).contains(2027);
            assertThat(refusal(() -> book.stamp(종로구, 올해_칠함, 0, 그때(서울(2026, 12, 31, 23, 50)))))
                .isEqualTo(ExplorationError.REVISIT_SAME_YEAR);
            assertThat(book.stamps().count()).isZero();
        }

        @Test
        @DisplayName("아직 칠하지 않은 지역이면 받지 못한다")
        void notPainted() {
            StampBook book = StampBook.empty(ME);

            assertThat(book.judge(종로구, 안_칠함, 0, 그때(내년_봄)).refusal()).contains(StampRefusal.NOT_PAINTED);
            assertThat(refusal(() -> book.stamp(종로구, 안_칠함, 0, 그때(내년_봄)))).isEqualTo(ExplorationError.REVISIT_NOT_PAINTED);
        }
    }

    @Nested
    @DisplayName("하루 체크인 상한")
    class DailyCap {

        @Test
        @DisplayName("오늘 개인 지도에서 네 곳을 칠했으면 도장은 한 개까지 받는다")
        void sharesWithCheckIns() {
            StampBook book = StampBook.empty(ME);
            book.stamp(종로구, 올해_칠함, 4, 그때(내년_봄));

            StampEligibility next = book.judge(중구, 올해_칠함, 4, 그때(내년_봄.plusSeconds(60)));

            assertThat(next.refusal()).contains(StampRefusal.DAILY_CAP);
            assertThat(refusal(() -> book.stamp(중구, 올해_칠함, 4, 그때(내년_봄.plusSeconds(60)))))
                .isEqualTo(ExplorationError.DAILY_CAP_EXCEEDED);
            assertThatThrownBy(() -> book.stamp(중구, 올해_칠함, 4, 그때(내년_봄.plusSeconds(60))))
                .hasMessageContaining("체크인과 재방문 도장을 합친");
        }

        @Test
        @DisplayName("도장도 한 건으로 세어 체크인 없이 다섯 개를 받으면 그날은 더 받지 못한다")
        void stampsCount() {
            StampBook book = StampBook.empty(ME);
            for (int i = 1; i <= 5; i++) book.stamp(seoul(i).code(), 올해_칠함, 0, 그때(내년_봄.plusSeconds(i)));

            assertThat(book.judge(seoul(6).code(), 올해_칠함, 0, 그때(내년_봄.plusSeconds(10))).refusal())
                .contains(StampRefusal.DAILY_CAP);
        }

        @Test
        @DisplayName("어제 받은 도장은 오늘 상한에 세지 않는다")
        void yesterdayDoesNotCount() {
            StampBook book = StampBook.empty(ME);
            for (int i = 1; i <= 5; i++) book.stamp(seoul(i).code(), 올해_칠함, 0, 그때(내년_봄.plusSeconds(i)));

            assertThat(book.judge(seoul(6).code(), 올해_칠함, 0, 그때(내년_봄.plus(Duration.ofDays(1)))).stampable()).isTrue();
        }

        @Test
        @DisplayName("가입 직후 온보딩 기간이면 상한 없이 받는다")
        void onboarding() {
            StampBook book = StampBook.empty(ME);
            CheckInContext 갓_가입 = new CheckInContext(POLICY, 내년_봄.minus(Duration.ofHours(1)), 내년_봄, KST);

            assertThat(book.judge(종로구, 올해_칠함, 9, 갓_가입).stampable()).isTrue();
        }
    }

    @Nested
    @DisplayName("계정으로 합칠 때")
    class Merge {

        @Test
        @DisplayName("익명으로 받은 도장을 계정 도장첩으로 옮기고 같은 지역·연도는 하나만 남긴다")
        void absorbs() {
            StampBook anonymous = StampBook.empty(ME);
            anonymous.stamp(종로구, 올해_칠함, 0, 그때(내년_봄));
            anonymous.stamp(중구, 올해_칠함, 0, 그때(내년_봄.plusSeconds(60)));
            StampBook account = StampBook.empty(ACCOUNT);
            account.stamp(종로구, 올해_칠함, 0, 그때(내년_봄.plusSeconds(120)));

            var adopted = account.absorb(anonymous);

            assertThat(adopted).extracting(RevisitStamp::region).containsExactly(중구);
            assertThat(account.stamps().count()).isEqualTo(2);
            assertThat(account.absorb(anonymous)).isEmpty();
        }
    }
}
