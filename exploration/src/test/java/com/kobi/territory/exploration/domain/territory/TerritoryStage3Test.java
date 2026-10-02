package com.kobi.territory.exploration.domain.territory;

import static com.kobi.territory.exploration.domain.Fixtures.FRIEND;
import static com.kobi.territory.exploration.domain.Fixtures.GAPYEONG;
import static com.kobi.territory.exploration.domain.Fixtures.JONGNO;
import static com.kobi.territory.exploration.domain.Fixtures.JUNG;
import static com.kobi.territory.exploration.domain.Fixtures.MAP;
import static com.kobi.territory.exploration.domain.Fixtures.ME;
import static com.kobi.territory.exploration.domain.Fixtures.NOON;
import static com.kobi.territory.exploration.domain.Fixtures.checkIn;
import static com.kobi.territory.exploration.domain.Fixtures.onboarding;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.domain.ExplorationError;
import com.kobi.territory.exploration.domain.ExplorationException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 3단계: 체크인 회차·선점 이전·탈퇴 숨김/재가입 복구/하드 삭제·지도장 이의. */
class TerritoryStage3Test {

    static final ExplorerId THIRD = ExplorerId.of("55555555-5555-5555-5555-555555555555");

    static Instant at(int minutes) {
        return NOON.plusSeconds(60L * minutes);
    }

    @Nested
    @DisplayName("체크인 회차(결정 6)")
    class Generation {
        @Test
        void 취소_후_다시_칠하면_회차가_오른다() {
            Territory territory = Territory.empty(MAP);
            assertThat(checkIn(territory, ME, JONGNO, onboarding(at(0))).visit().generation()).isEqualTo(1);
            territory.cancelVisit(ME, JONGNO.code());
            assertThat(checkIn(territory, ME, JONGNO, onboarding(at(1))).visit().generation()).isEqualTo(2);
            assertThat(checkIn(territory, FRIEND, JONGNO, onboarding(at(2))).visit().generation()).isEqualTo(1); // 멤버별
            assertThat(territory.changedGenerations()).hasSize(2);
        }

        @Test
        void 회차_기록이_없던_예전_방문은_그_방문의_회차부터_이어진다() {
            Territory first = Territory.empty(MAP);
            checkIn(first, ME, JONGNO, onboarding(at(0)));
            Territory restored = Territory.restore(MAP, first.allVisits()); // visit_generation 행 없음
            restored.cancelVisit(ME, JONGNO.code());
            assertThat(checkIn(restored, ME, JONGNO, onboarding(at(1))).visit().generation()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("선점 이전")
    class Claims {
        @Test
        void 선점자의_취소는_다음으로_칠한_멤버에게_선점을_넘긴다() {
            Territory territory = Territory.empty(MAP);
            checkIn(territory, ME, JONGNO, onboarding(at(0)));
            checkIn(territory, FRIEND, JONGNO, onboarding(at(1)));
            checkIn(territory, THIRD, JONGNO, onboarding(at(2)));
            CancelResult result = territory.cancelVisit(ME, JONGNO.code());
            assertThat(result.wasClaim()).isTrue();
            assertThat(result.transferredClaim()).hasValueSatisfying(transfer -> {
                assertThat(transfer.from()).isEqualTo(ME);
                assertThat(transfer.to()).isEqualTo(FRIEND);
                assertThat(transfer.reason()).isEqualTo(ClaimTransfer.Reason.CANCELLED);
            });
            assertThat(territory.claimOf(JONGNO.code()).orElseThrow().checkedInBy()).isEqualTo(FRIEND);
        }

        @Test
        void 선점자가_아닌_취소나_혼자인_지역의_취소는_이전이_없다() {
            Territory territory = Territory.empty(MAP);
            checkIn(territory, ME, JONGNO, onboarding(at(0)));
            checkIn(territory, FRIEND, JONGNO, onboarding(at(1)));
            checkIn(territory, ME, JUNG, onboarding(at(2)));
            assertThat(territory.cancelVisit(FRIEND, JONGNO.code()).transferredClaim()).isEmpty();
            assertThat(territory.cancelVisit(ME, JUNG.code()).transferredClaim()).isEmpty();
        }
    }

    @Nested
    @DisplayName("탈퇴 유예 — 숨김·복구·삭제")
    class Leave {
        Territory shared() {
            Territory territory = Territory.empty(MAP);
            checkIn(territory, ME, JONGNO, onboarding(at(0)));   // ME 선점
            checkIn(territory, FRIEND, JONGNO, onboarding(at(1)));
            checkIn(territory, ME, GAPYEONG, onboarding(at(2))); // ME 혼자
            checkIn(territory, FRIEND, JUNG, onboarding(at(3)));
            return territory;
        }

        @Test
        void 탈퇴하면_방문이_숨겨지고_선점은_넘어가며_혼자였던_지역은_지도에서_사라진다() {
            Territory territory = shared();
            HideResult result = territory.hideMember(ME, at(10));
            assertThat(result.hidden()).containsExactlyInAnyOrder(JONGNO.code(), GAPYEONG.code());
            assertThat(result.regionsGone()).containsExactly(GAPYEONG.code());
            assertThat(result.claimTransfers()).singleElement().satisfies(transfer -> {
                assertThat(transfer.region()).isEqualTo(JONGNO);
                assertThat(transfer.to()).isEqualTo(FRIEND);
                assertThat(transfer.reason()).isEqualTo(ClaimTransfer.Reason.LEFT);
            });
            assertThat(territory.visitsOf(ME)).isEmpty();
            assertThat(territory.claimedRegions()).extracting(RegionSnapshot::code)
                .containsExactlyInAnyOrder(JONGNO.code(), JUNG.code());
            assertThat(territory.allVisits()).hasSize(4); // 하드 삭제 전
            // 재전달 멱등
            assertThat(territory.hideMember(ME, at(11)).hidden()).isEmpty();
        }

        @Test
        void 숨긴_방문은_하루_상한_nth_선점_시도_첫방문_계산에서_빠진다() {
            Territory territory = shared();
            territory.hideMember(ME, at(10));
            VisitFacts facts = territory.factsFor(FRIEND, GAPYEONG);
            assertThat(facts.firstClaim()).isTrue(); // 지도에 GAPYEONG 이 안 보인다
        }

        @Test
        void 유예_안_재가입은_방문을_복구하지만_넘어간_선점은_돌아오지_않는다() {
            Territory territory = shared();
            territory.hideMember(ME, at(10));
            RestoreResult result = territory.restoreMember(ME, at(20));
            assertThat(result.restored()).containsExactlyInAnyOrder(JONGNO.code(), GAPYEONG.code());
            assertThat(result.regionsBack()).containsExactly(GAPYEONG.code());
            assertThat(territory.visitsOf(ME)).hasSize(2);
            assertThat(territory.claimOf(JONGNO.code()).orElseThrow().checkedInBy()).isEqualTo(FRIEND);
            assertThat(territory.claimOf(GAPYEONG.code()).orElseThrow().checkedInBy()).isEqualTo(ME);
            assertThat(territory.restoreMember(ME, at(21)).restored()).isEmpty(); // 멱등
        }

        @Test
        void 유예가_끝나면_숨긴_방문만_지운다() {
            Territory territory = shared();
            territory.hideMember(ME, at(10));
            assertThat(territory.purgeHidden(ME)).hasSize(2);
            assertThat(territory.allVisits()).extracting(Visit::checkedInBy).containsOnly(FRIEND);
            assertThat(territory.purgeHidden(ME)).isEmpty();
        }
    }

    @Test
    void 지도장_이의는_방문_플래그만_바꾸고_보이는_방문에만_걸린다() {
        Territory territory = Territory.empty(MAP);
        checkIn(territory, FRIEND, JONGNO, onboarding(at(0)));
        assertThat(territory.dispute(JONGNO.code(), FRIEND, true).disputed()).isTrue();
        assertThat(territory.dispute(JONGNO.code(), FRIEND, false).disputed()).isFalse();
        territory.hideMember(FRIEND, at(1));
        assertThatThrownBy(() -> territory.dispute(JONGNO.code(), FRIEND, true))
            .isInstanceOfSatisfying(ExplorationException.class,
                exception -> assertThat(exception.error()).isEqualTo(ExplorationError.VISIT_NOT_FOUND));
        assertThatThrownBy(() -> territory.dispute(RegionCode.of("KR-11999"), ME, true))
            .isInstanceOf(ExplorationException.class);
    }

    @Test
    void 지역마다_선점_방문_목록() {
        Territory territory = Territory.empty(MAP);
        checkIn(territory, FRIEND, JUNG, onboarding(at(0)));
        checkIn(territory, ME, JONGNO, onboarding(at(1)));
        checkIn(territory, ME, JUNG, onboarding(at(2)));
        assertThat(territory.claims()).extracting(visit -> visit.regionCode().value() + "=" + visit.checkedInBy().value())
            .containsExactly(JONGNO.code().value() + "=" + ME.value(), JUNG.code().value() + "=" + FRIEND.value());
        assertThat(List.copyOf(territory.claims())).hasSize(2);
    }

    @Test
    void 재계산_재생의_선점은_지금의_선점_순서를_따른다_재가입_뒤에도_P3_2() {
        Territory territory = Territory.empty(MAP);
        checkIn(territory, ME, JONGNO, onboarding(at(0)));     // ME 선점
        checkIn(territory, FRIEND, JONGNO, onboarding(at(1)));
        territory.hideMember(ME, at(2));                       // 선점 → FRIEND
        territory.restoreMember(ME, at(3));                    // 복구(선점은 안 돌아옴)
        assertThat(territory.history()).extracting(result -> result.visit().checkedInBy() + "=" + result.facts().firstClaim())
            .containsExactly(ME + "=false", FRIEND + "=true");
    }

    @Test
    void 늦게_처리된_탈퇴는_재가입_뒤_새로_칠한_방문을_숨기지_않는다_P3_3() {
        Territory territory = Territory.empty(MAP);
        checkIn(territory, ME, JONGNO, onboarding(at(0)));
        checkIn(territory, ME, JUNG, onboarding(at(5)));       // 탈퇴(at 2) → 재가입 뒤 새 체크인
        HideResult result = territory.hideMember(ME, at(2));
        assertThat(result.hidden()).containsExactly(JONGNO.code());
        assertThat(territory.visitsOf(ME)).extracting(Visit::regionCode).containsExactly(JUNG.code());
    }

    @Test
    void 사진_필수는_새_체크인에만_사진_없던_방문의_메모_수정은_허용하고_있던_사진_삭제는_막는다_Q5() {
        Territory territory = Territory.empty(MAP);
        checkIn(territory, ME, JONGNO, onboarding(at(0)));     // 사진 없음(설정 전)
        CheckInContext strict = new CheckInContext(new com.kobi.territory.exploration.domain.policy.CheckInPolicy(5,
            java.time.Duration.ofHours(72), true), at(0), at(1), com.kobi.territory.exploration.domain.Fixtures.KST);
        assertThat(territory.editVisit(ME, JONGNO.code(), VisitPatch.of(null, "메모만", null), strict).memo().value())
            .isEqualTo("메모만");
        territory.checkIn(ME, JUNG, VisitDate.of(strict.today()), Memo.EMPTY, new PhotoRef("https://example.com/a.jpg"), strict);
        assertThatThrownBy(() -> territory.editVisit(ME, JUNG.code(), VisitPatch.of(null, null, ""), strict))
            .isInstanceOfSatisfying(ExplorationException.class,
                exception -> assertThat(exception.error()).isEqualTo(ExplorationError.PHOTO_REQUIRED));
    }

    @Test
    void 다른_멤버_방문은_메모와_사진을_비워_보여준다_P3_10() {
        Territory territory = Territory.empty(MAP);
        territory.checkIn(FRIEND, JONGNO, VisitDate.of(onboarding(at(0)).today()), Memo.of("비밀"),
            new PhotoRef("https://example.com/b.jpg"), onboarding(at(0)));
        territory.checkIn(ME, JUNG, VisitDate.of(onboarding(at(1)).today()), Memo.of("내 메모"), null, onboarding(at(1)));
        List<VisitView> views = territory.viewedBy(ME);
        VisitView friends = views.stream().filter(view -> view.checkedInBy().equals(FRIEND)).findFirst().orElseThrow();
        VisitView mine = views.stream().filter(view -> view.checkedInBy().equals(ME)).findFirst().orElseThrow();
        assertThat(friends.memo().isEmpty()).isTrue();
        assertThat(friends.photo()).isNull();
        assertThat(friends.claim()).isTrue();
        assertThat(mine.memo().value()).isEqualTo("내 메모");
        assertThat(territory.claimCountOf(FRIEND)).isEqualTo(1);
    }
}
