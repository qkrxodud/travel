package com.kobi.territory.exploration.domain.wishlist;

import static com.kobi.territory.exploration.domain.Fixtures.ACCOUNT;
import static com.kobi.territory.exploration.domain.Fixtures.GAPYEONG;
import static com.kobi.territory.exploration.domain.Fixtures.JONGNO;
import static com.kobi.territory.exploration.domain.Fixtures.ME;
import static com.kobi.territory.exploration.domain.Fixtures.NOON;
import static com.kobi.territory.exploration.domain.Fixtures.hours;
import static com.kobi.territory.exploration.domain.Fixtures.minutes;
import static com.kobi.territory.exploration.domain.Fixtures.refusal;
import static com.kobi.territory.exploration.domain.Fixtures.seoul;
import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.domain.ExplorationError;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 가고 싶은 곳 — 아직 칠하지 않은 지역에 핀(탐험가당 상한, 비공개). 핀을 꽂은 뒤 그 지역을 칠하면 "다녀옴". */
@DisplayName("가고 싶은 곳")
class WishlistTest {

    private static final WishlistPolicy 서른곳 = new WishlistPolicy(30);
    private static final RegionCode 종로구 = JONGNO.code();
    private static final RegionCode 가평군 = GAPYEONG.code();
    private static final boolean 칠함 = true;
    private static final boolean 안_칠함 = false;

    @Nested
    @DisplayName("핀 꽂기")
    class Pin {

        @Test
        @DisplayName("아직 칠하지 않은 지역에 핀을 꽂는다")
        void pins() {
            Wishlist wishlist = Wishlist.empty(ME);

            WishPin pin = wishlist.pin(가평군, 안_칠함, NOON, 서른곳);

            assertThat(pin.fulfilled()).isFalse();
            assertThat(wishlist.pins().pendingCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("같은 지역에 다시 꽂아도 핀은 하나다")
        void idempotent() {
            Wishlist wishlist = Wishlist.empty(ME);
            wishlist.pin(가평군, 안_칠함, NOON, 서른곳);

            WishPin again = wishlist.pin(가평군, 안_칠함, minutes(5), 서른곳);

            assertThat(again.pinnedAt()).isEqualTo(NOON);
            assertThat(wishlist.pins().newestFirst()).hasSize(1);
        }

        @Test
        @DisplayName("이미 칠한 지역에는 꽂을 수 없다")
        void paintedRefused() {
            Wishlist wishlist = Wishlist.empty(ME);

            assertThat(refusal(() -> wishlist.pin(종로구, 칠함, NOON, 서른곳))).isEqualTo(ExplorationError.WISH_ALREADY_VISITED);
            assertThat(wishlist.pins().newestFirst()).isEmpty();
        }

        @Test
        @DisplayName("아직 다녀오지 않은 핀이 서른 개면 더 꽂을 수 없다")
        void full() {
            Wishlist wishlist = Wishlist.empty(ME);
            for (int i = 1; i <= 30; i++) wishlist.pin(seoul(i).code(), 안_칠함, minutes(i), 서른곳);

            assertThat(refusal(() -> wishlist.pin(seoul(31).code(), 안_칠함, minutes(40), 서른곳)))
                .isEqualTo(ExplorationError.WISHLIST_FULL);
            assertThat(wishlist.pins().pendingCount()).isEqualTo(30);
        }

        @Test
        @DisplayName("다녀온 핀은 서른 개 안에 세지 않는다")
        void fulfilledDoNotCount() {
            Wishlist wishlist = Wishlist.empty(ME);
            for (int i = 1; i <= 30; i++) wishlist.pin(seoul(i).code(), 안_칠함, minutes(i), 서른곳);
            wishlist.fulfill(seoul(1).code(), hours(2));

            wishlist.pin(seoul(31).code(), 안_칠함, hours(3), 서른곳);

            assertThat(wishlist.pins().pendingCount()).isEqualTo(30);
            assertThat(wishlist.pins().fulfilledCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("그 지역을 칠하면")
    class Fulfill {

        @Test
        @DisplayName("핀이 다녀옴으로 바뀐다")
        void fulfilled() {
            Wishlist wishlist = Wishlist.empty(ME);
            wishlist.pin(가평군, 안_칠함, NOON, 서른곳);

            var fulfillment = wishlist.fulfill(가평군, hours(1));

            assertThat(fulfillment).contains(new WishFulfillment(가평군, NOON, hours(1)));
            assertThat(wishlist.pins().find(가평군).orElseThrow().fulfilled()).isTrue();
        }

        @Test
        @DisplayName("같은 체크인 소식이 다시 와도 한 번만 다녀옴이 된다")
        void once() {
            Wishlist wishlist = Wishlist.empty(ME);
            wishlist.pin(가평군, 안_칠함, NOON, 서른곳);
            wishlist.fulfill(가평군, hours(1));

            assertThat(wishlist.fulfill(가평군, hours(1))).isEmpty();
            assertThat(wishlist.fulfill(가평군, hours(5))).isEmpty();
        }

        @Test
        @DisplayName("핀을 꽂기 전에 칠한 체크인으로는 다녀옴이 되지 않는다")
        void beforePin() {
            Wishlist wishlist = Wishlist.empty(ME);
            wishlist.pin(가평군, 안_칠함, hours(2), 서른곳);

            assertThat(wishlist.fulfill(가평군, hours(1))).isEmpty();
        }

        @Test
        @DisplayName("핀 꽂기와 거의 동시에 칠해 체크인 시각이 핀보다 앞서도 지금 칠해져 있으면 다녀옴이 되고 다녀온 시각은 핀 시각이다")
        void almostSimultaneous() {
            Wishlist wishlist = Wishlist.empty(ME);
            wishlist.pin(가평군, 안_칠함, hours(2), 서른곳);

            var fulfillment = wishlist.fulfill(가평군, hours(1), 칠함);

            assertThat(fulfillment).contains(new WishFulfillment(가평군, hours(2), hours(2)));
        }

        @Test
        @DisplayName("핀이 없는 지역을 칠하면 아무 일도 없다")
        void notPinned() {
            assertThat(Wishlist.empty(ME).fulfill(가평군, hours(1))).isEmpty();
        }
    }

    @Nested
    @DisplayName("핀 빼기")
    class Unpin {

        @Test
        @DisplayName("다녀온 핀도 뺄 수 있고 없는 핀을 빼도 아무 일 없다")
        void unpins() {
            Wishlist wishlist = Wishlist.empty(ME);
            wishlist.pin(가평군, 안_칠함, NOON, 서른곳);
            wishlist.fulfill(가평군, hours(1));

            assertThat(wishlist.unpin(가평군)).isTrue();
            assertThat(wishlist.unpin(가평군)).isFalse();
            assertThat(wishlist.pins().newestFirst()).isEmpty();
            assertThat(wishlist.pins().removed()).containsExactly(가평군);
        }
    }

    @Nested
    @DisplayName("계정으로 합칠 때")
    class Merge {

        @Test
        @DisplayName("익명의 핀을 계정으로 합치고 같은 지역은 먼저 꽂은 시각과 다녀옴을 남긴다")
        void absorbs() {
            Wishlist anonymous = Wishlist.empty(ME);
            anonymous.pin(가평군, 안_칠함, NOON, 서른곳);
            anonymous.fulfill(가평군, hours(1));
            anonymous.pin(seoul(1).code(), 안_칠함, minutes(10), 서른곳);
            Wishlist account = Wishlist.empty(ACCOUNT);
            account.pin(가평군, 안_칠함, minutes(30), 서른곳);

            var newlyFulfilled = account.absorb(anonymous);

            assertThat(newlyFulfilled).extracting(WishPin::region).containsExactly(가평군);
            WishPin merged = account.pins().find(가평군).orElseThrow();
            assertThat(merged.pinnedAt()).isEqualTo(NOON);
            assertThat(merged.fulfilledAt()).isEqualTo(hours(1));
            assertThat(account.pins().newestFirst()).hasSize(2);
            assertThat(account.absorb(anonymous)).isEmpty();
        }

        @Test
        @DisplayName("합친 뒤 이미 칠해진 지역의 대기 핀은 다녀옴이 된다 — 그 사람이 실제로 다녀온 곳이다")
        void paintedPendingFulfilled() {
            Wishlist account = Wishlist.empty(ACCOUNT);
            account.pin(가평군, 안_칠함, NOON, 서른곳);
            account.pin(종로구, 안_칠함, minutes(1), 서른곳);

            var done = account.fulfillPainted(Set.of(가평군), hours(3));

            assertThat(done).extracting(WishFulfillment::region).containsExactly(가평군);
            assertThat(account.pins().pendingRegions()).containsExactly(종로구);
            assertThat(account.fulfillPainted(Set.of(가평군), hours(4))).isEmpty();
        }
    }
}
