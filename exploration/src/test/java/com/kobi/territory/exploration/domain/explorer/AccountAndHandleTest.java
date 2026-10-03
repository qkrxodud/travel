package com.kobi.territory.exploration.domain.explorer;

import static com.kobi.territory.exploration.domain.Fixtures.FRIEND;
import static com.kobi.territory.exploration.domain.Fixtures.ME;
import static com.kobi.territory.exploration.domain.Fixtures.NOON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.domain.ExplorationError;
import com.kobi.territory.exploration.domain.ExplorationException;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 4단계 D1: handle 규칙 · 계정 연결 · 병합(claimExplorer) 규칙 · 로그인 계획 — Spring 없음. */
class AccountAndHandleTest {

    static final ExplorerId THIRD = ExplorerId.of("55555555-5555-5555-5555-555555555555");
    static final java.time.Duration RESERVE = java.time.Duration.ofDays(30);
    static final AccountIdentity KIM = new AccountIdentity("google", "sub-kim", "Kim.Traveler+tag@Example.com");

    static Explorer anonymous(ExplorerId id) {
        return Explorer.anonymous(id, AccessToken.generate(RandomGenerator.getDefault()).hash(), NOON);
    }

    static Explorer linked(ExplorerId id, String handle) {
        Explorer explorer = anonymous(id);
        explorer.linkAccount(new Account(new AccountIdentity("google", "sub-" + id.value(), handle + "@example.com"), NOON),
            new Handle(handle));
        return explorer;
    }

    static ExplorationError errorOf(Runnable action) {
        try {
            action.run();
        } catch (ExplorationException exception) {
            return exception.error();
        }
        throw new AssertionError("예외가 나야 한다");
    }

    @Nested
    @DisplayName("handle")
    class Handles {
        @Test
        void 형식은_소문자_숫자_밑줄_3에서_20자_첫_글자_영숫자() {
            assertThat(Handle.of(" @Kim_01 ").value()).isEqualTo("kim_01");
            assertThat(errorOf(() -> Handle.of("ab"))).isEqualTo(ExplorationError.HANDLE_INVALID);
            assertThat(errorOf(() -> Handle.of("_kim"))).isEqualTo(ExplorationError.HANDLE_INVALID);
            assertThat(errorOf(() -> Handle.of("kim.lee"))).isEqualTo(ExplorationError.HANDLE_INVALID);
            assertThat(errorOf(() -> Handle.of("kim lee"))).isEqualTo(ExplorationError.HANDLE_INVALID);
            assertThat(errorOf(() -> Handle.of("a".repeat(21)))).isEqualTo(ExplorationError.HANDLE_INVALID);
            assertThat(errorOf(() -> Handle.of(null))).isEqualTo(ExplorationError.HANDLE_INVALID);
            assertThat(Handle.of("a".repeat(20)).value()).hasSize(20);
        }

        @Test
        void 금칙어는_쓸_수_없고_조회도_없는_handle_로_다룬다() {
            assertThat(errorOf(() -> Handle.of("Admin"))).isEqualTo(ExplorationError.HANDLE_RESERVED);
            assertThat(Handle.parse("admin")).isEmpty();
            assertThat(Handle.parse("@KIM")).contains(new Handle("kim"));
            assertThat(Handle.parse("bad handle!")).isEmpty();
        }

        @Test
        void 최초_로그인_자동_발급은_이메일과_무관한_랜덤_handle_Q1() {
            Handle handle = Handle.random(new java.util.Random(7), candidate -> false);
            assertThat(handle.value()).matches("explorer-[a-z2-9]{4}").doesNotContain("kim");
            // 같은 난수열이면 같은 값(이메일 입력 없음)
            assertThat(Handle.random(new java.util.Random(7), candidate -> false)).isEqualTo(handle);
        }

        @Test
        void 겹치면_다시_뽑고_계속_겹치면_6자로_늘린다() {
            java.util.List<String> seen = new java.util.ArrayList<>();
            Handle handle = Handle.random(new java.util.Random(1), candidate -> {
                seen.add(candidate.value());
                return seen.size() <= 5; // 처음 5번은 쓰였다고 답한다
            });
            assertThat(seen).hasSize(6);
            assertThat(seen.subList(0, 5)).allSatisfy(value -> assertThat(value).matches("explorer-[a-z2-9]{4}"));
            assertThat(handle.value()).matches("explorer-[a-z2-9]{6}");
        }

        @Test
        void 하이픈을_허용한다() {
            assertThat(Handle.of("kim-lee").value()).isEqualTo("kim-lee");
            assertThat(errorOf(() -> Handle.of("-kim"))).isEqualTo(ExplorationError.HANDLE_INVALID);
        }
    }

    @Nested
    @DisplayName("계정 연결")
    class Link {
        @Test
        void 연결하면_handle_이_생기고_익명_토큰은_무효() {
            Explorer explorer = anonymous(ME);
            assertThat(explorer.linkable()).isTrue();
            Explorer.HandleChange change = explorer.linkAccount(new Account(KIM, NOON), new Handle("kim_traveler"));
            assertThat(change.previous()).isNull();
            assertThat(explorer.anonymous()).isFalse();
            assertThat(explorer.publicProfile()).isTrue();
            assertThat(explorer.tokenHash()).isNull();
            assertThat(explorer.account()).map(account -> account.identity().email()).contains("kim.traveler+tag@example.com");
            assertThat(errorOf(() -> explorer.linkAccount(new Account(KIM, NOON), new Handle("again"))))
                .isEqualTo(ExplorationError.MERGE_NOT_ALLOWED);
        }

        @Test
        void handle_변경은_계정_탐험가만_중복이면_HANDLE_TAKEN_같으면_변화_없음() {
            Explorer anonymousExplorer = anonymous(ME);
            assertThat(errorOf(() -> anonymousExplorer.changeHandle(new Handle("kim"), NOON, RESERVE, handle -> false)))
                .isEqualTo(ExplorationError.LOGIN_REQUIRED);
            Explorer explorer = linked(FRIEND, "lee");
            assertThat(explorer.changeHandle(new Handle("lee"), NOON, RESERVE, handle -> true)).isEmpty();
            assertThat(errorOf(() -> explorer.changeHandle(new Handle("park"), NOON, RESERVE, handle -> true)))
                .isEqualTo(ExplorationError.HANDLE_TAKEN);
            Optional<Explorer.HandleChange> change = explorer.changeHandle(new Handle("lee_2"), NOON, RESERVE, handle -> false);
            assertThat(change).map(Explorer.HandleChange::previous).contains(new Handle("lee"));
            assertThat(explorer.handle()).isEqualTo(new Handle("lee_2"));
        }

        @Test
        void 놓은_handle_은_기간_동안_예약되고_본인은_되돌릴_수_있다_P3_10() {
            Explorer explorer = linked(FRIEND, "lee");
            explorer.changeHandle(new Handle("lee_2"), NOON, RESERVE, handle -> false);
            assertThat(explorer.handleReservations()).containsExactly(new HandleReservation(new Handle("lee"), NOON.plus(RESERVE)));
            assertThat(explorer.handleReservations().get(0).activeAt(NOON.plus(RESERVE).minusSeconds(1))).isTrue();
            assertThat(explorer.handleReservations().get(0).activeAt(NOON.plus(RESERVE))).isFalse();
            // 본인이 옛 handle 로 되돌리면 그 예약은 빠지고 방금 놓은 handle 이 예약된다
            explorer.changeHandle(new Handle("lee"), NOON.plusSeconds(60), RESERVE, handle -> false);
            assertThat(explorer.handleReservations()).extracting(HandleReservation::handle).containsExactly(new Handle("lee_2"));
            // 기한이 지난 예약은 다음 변경 때 정리된다
            explorer.changeHandle(new Handle("lee_3"), NOON.plus(RESERVE).plusSeconds(120), RESERVE, handle -> false);
            assertThat(explorer.handleReservations()).extracting(HandleReservation::handle).containsExactly(new Handle("lee"));
        }

        @Test
        void 신원_형식() {
            assertThat(errorOf(() -> new AccountIdentity("Google!", "sub", "a@b.c"))).isEqualTo(ExplorationError.ACCOUNT_INVALID);
            assertThat(errorOf(() -> new AccountIdentity("google", " ", "a@b.c"))).isEqualTo(ExplorationError.ACCOUNT_INVALID);
            assertThat(errorOf(() -> new AccountIdentity("google", "sub", "no-at"))).isEqualTo(ExplorationError.ACCOUNT_INVALID);
        }
    }

    @Nested
    @DisplayName("병합(claimExplorer — 기존 계정으로 병합)")
    class Merge {
        @Test
        void 활성_익명만_다른_활성_계정_탐험가로_병합된다_비활성_토큰_무효() {
            Explorer device = anonymous(ME);
            Explorer account = linked(FRIEND, "kim");
            assertThat(device.canMergeInto(account)).isTrue();
            assertThat(device.mergeInto(account, NOON)).map(Explorer.Merge::into).contains(FRIEND);
            assertThat(device.active()).isFalse();
            assertThat(device.status()).isEqualTo(ExplorerStatus.MERGED);
            assertThat(device.mergedInto()).contains(FRIEND);
            assertThat(device.tokenHash()).isNull();
        }

        @Test
        void 같은_대상으로_다시_병합하면_아무것도_하지_않는다_멱등() {
            Explorer device = anonymous(ME);
            Explorer account = linked(FRIEND, "kim");
            device.mergeInto(account, NOON);
            assertThat(device.mergeInto(account, NOON.plusSeconds(1))).isEmpty();
            assertThat(device.mergedAt()).isEqualTo(NOON);
            // 다른 대상으로는 안 된다(되돌리지 않음)
            assertThat(errorOf(() -> device.mergeInto(linked(THIRD, "lee"), NOON))).isEqualTo(ExplorationError.MERGE_NOT_ALLOWED);
        }

        @Test
        void 계정_탐험가_자기_자신_익명_대상은_병합할_수_없다() {
            Explorer account = linked(FRIEND, "kim");
            assertThat(linked(ME, "lee").canMergeInto(account)).isFalse(); // 이미 계정 탐험가
            assertThat(anonymous(ME).canMergeInto(anonymous(THIRD))).isFalse(); // 대상이 익명
            assertThat(account.canMergeInto(account)).isFalse();
            assertThat(errorOf(() -> anonymous(ME).mergeInto(anonymous(THIRD), NOON))).isEqualTo(ExplorationError.MERGE_NOT_ALLOWED);
        }

        @Test
        void 로그인_계획() {
            Explorer device = anonymous(ME);
            Explorer account = linked(FRIEND, "kim");
            assertThat(LoginPlan.decide(Optional.of(account), Optional.of(device)).kind()).isEqualTo(LoginPlan.Kind.MERGE);
            assertThat(LoginPlan.decide(Optional.of(account), Optional.empty()).kind()).isEqualTo(LoginPlan.Kind.SIGN_IN);
            assertThat(LoginPlan.decide(Optional.of(account), Optional.of(account)).kind()).isEqualTo(LoginPlan.Kind.SIGN_IN);
            assertThat(LoginPlan.decide(Optional.empty(), Optional.of(device)).kind()).isEqualTo(LoginPlan.Kind.LINK);
            assertThat(LoginPlan.decide(Optional.empty(), Optional.empty()).kind()).isEqualTo(LoginPlan.Kind.CREATE);
            // 다른 계정의 탐험가가 "지금 기기"라면 그 계정을 건드리지 않고 새로 만든다
            assertThat(LoginPlan.decide(Optional.empty(), Optional.of(linked(THIRD, "lee"))).kind()).isEqualTo(LoginPlan.Kind.CREATE);
            // 이미 병합된 기기로 다시 로그인 → 로그인만(멱등)
            device.mergeInto(account, NOON);
            assertThat(LoginPlan.decide(Optional.of(account), Optional.of(device)).kind()).isEqualTo(LoginPlan.Kind.SIGN_IN);
        }
    }
}
