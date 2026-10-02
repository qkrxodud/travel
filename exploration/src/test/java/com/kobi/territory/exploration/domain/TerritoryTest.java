package com.kobi.territory.exploration.domain;

import static com.kobi.territory.exploration.domain.Fixtures.FRIEND;
import static com.kobi.territory.exploration.domain.Fixtures.GAPYEONG;
import static com.kobi.territory.exploration.domain.Fixtures.JONGNO;
import static com.kobi.territory.exploration.domain.Fixtures.JUNG;
import static com.kobi.territory.exploration.domain.Fixtures.KST;
import static com.kobi.territory.exploration.domain.Fixtures.MAP;
import static com.kobi.territory.exploration.domain.Fixtures.ME;
import static com.kobi.territory.exploration.domain.Fixtures.NOON;
import static com.kobi.territory.exploration.domain.Fixtures.POLICY;
import static com.kobi.territory.exploration.domain.Fixtures.TODAY;
import static com.kobi.territory.exploration.domain.Fixtures.checkIn;
import static com.kobi.territory.exploration.domain.Fixtures.onboarding;
import static com.kobi.territory.exploration.domain.Fixtures.seoul;
import static com.kobi.territory.exploration.domain.Fixtures.veteran;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TerritoryTest {

    private static ExplorationError errorOf(Throwable t) {
        return ((ExplorationException) t).error();
    }

    @Nested
    @DisplayName("checkIn")
    class CheckIn {

        @Test
        void 첫_체크인은_1번째_시도_첫방문_선점이고_visitedAt은_처리시각이다() {
            Territory t = Territory.empty(MAP);
            CheckInResult r = t.checkIn(ME, JONGNO, VisitDate.of(TODAY.minusYears(2)), Memo.of("경복궁"), null,
                onboarding(NOON));

            assertThat(r.facts()).isEqualTo(new VisitFacts(false, 1, true, true));
            assertThat(r.mapId()).isEqualTo(MAP);
            assertThat(r.visit().visitedAt()).isEqualTo(NOON);
            assertThat(r.visit().visitDate().value()).isEqualTo(TODAY.minusYears(2));
            assertThat(r.visit().verification()).isEqualTo(Verification.NONE);
            assertThat(r.visit().memo().value()).isEqualTo("경복궁");
            assertThat(t.visitsOf(ME)).hasSize(1);
        }

        @Test
        void 같은_시도_두번째는_시도_첫방문이_아니고_nth가_증가한다() {
            Territory t = Territory.empty(MAP);
            checkIn(t, ME, JONGNO, onboarding(NOON));
            CheckInResult r = checkIn(t, ME, JUNG, onboarding(NOON));
            assertThat(r.facts()).isEqualTo(new VisitFacts(false, 2, false, true));
            CheckInResult other = checkIn(t, ME, GAPYEONG, onboarding(NOON));
            assertThat(other.facts().firstInProvince()).isTrue();
            assertThat(other.facts().nth()).isEqualTo(3);
        }

        @Test
        void 같은_지역_같은_멤버_중복은_거부한다() {
            Territory t = Territory.empty(MAP);
            checkIn(t, ME, JONGNO, onboarding(NOON));
            assertThatThrownBy(() -> checkIn(t, ME, JONGNO, onboarding(NOON)))
                .isInstanceOf(ExplorationException.class)
                .satisfies(e -> assertThat(errorOf(e)).isEqualTo(ExplorationError.DUPLICATE_VISIT));
        }

        @Test
        void 같은_지역이라도_다른_멤버는_각자_체크인하고_두번째는_선점이_아니다() {
            Territory t = Territory.empty(MAP);
            checkIn(t, ME, JONGNO, onboarding(NOON));
            CheckInResult r = checkIn(t, FRIEND, JONGNO, onboarding(NOON.plusSeconds(1)));
            assertThat(r.facts()).isEqualTo(new VisitFacts(false, 1, true, false));
            assertThat(t.claimOf(JONGNO.code())).get().extracting(Visit::checkedInBy).isEqualTo(ME);
            assertThat(t.claimedRegions()).hasSize(1);
        }

        @Test
        void 미래_날짜는_거부하고_오늘과_과거는_허용한다() {
            Territory t = Territory.empty(MAP);
            assertThatThrownBy(() -> t.checkIn(ME, JONGNO, VisitDate.of(TODAY.plusDays(1)), Memo.EMPTY, null,
                onboarding(NOON)))
                .satisfies(e -> assertThat(errorOf(e)).isEqualTo(ExplorationError.FUTURE_VISIT_DATE));
            t.checkIn(ME, JONGNO, VisitDate.of(TODAY), Memo.EMPTY, null, onboarding(NOON));
            t.checkIn(ME, JUNG, VisitDate.of(TODAY.minusYears(10)), Memo.EMPTY, null, onboarding(NOON));
            assertThat(t.visitsOf(ME)).hasSize(2);
        }

        @Test
        void 오늘_판단은_서버_시간대_기준이다() {
            // 2026-10-02 23:30 KST = 14:30Z. UTC로는 아직 10-02지만 KST 기준 10-03 날짜는 미래.
            Instant lateNight = Instant.parse("2026-10-02T14:30:00Z");
            Territory t = Territory.empty(MAP);
            assertThatThrownBy(() -> t.checkIn(ME, JONGNO, VisitDate.of(TODAY.plusDays(1)), Memo.EMPTY, null,
                onboarding(lateNight)))
                .satisfies(e -> assertThat(errorOf(e)).isEqualTo(ExplorationError.FUTURE_VISIT_DATE));
            // 00:30 KST(10-03) = 15:30Z(10-02) → 10-03 은 오늘
            Instant afterMidnight = Instant.parse("2026-10-02T15:30:00Z");
            t.checkIn(ME, JONGNO, VisitDate.of(TODAY.plusDays(1)), Memo.EMPTY, null, onboarding(afterMidnight));
        }

        @Test
        void 폐지된_지역에는_새로_체크인할_수_없다() {
            Territory t = Territory.empty(MAP);
            RegionSnapshot retired = new RegionSnapshot(JONGNO.code(), JONGNO.rarity(), "KR-11", true);
            assertThatThrownBy(() -> checkIn(t, ME, retired, onboarding(NOON)))
                .satisfies(e -> {
                    assertThat(errorOf(e)).isEqualTo(ExplorationError.REGION_RETIRED);
                    assertThat(((ExplorationException) e).kind()).isEqualTo(com.kobi.territory.common.error.ErrorKind.RULE_VIOLATION);
                });
            assertThat(t.visits()).isEmpty();
        }

        @Test
        void 사진_필수_지도는_사진_없는_체크인을_거부한다() {
            Territory t = Territory.empty(MAP);
            var ctx = new CheckInContext(new CheckInPolicy(5, Duration.ofHours(72), true), NOON, NOON, KST);
            assertThatThrownBy(() -> t.checkIn(ME, JONGNO, VisitDate.of(TODAY), Memo.EMPTY, null, ctx))
                .satisfies(e -> assertThat(errorOf(e)).isEqualTo(ExplorationError.PHOTO_REQUIRED));
            t.checkIn(ME, JONGNO, VisitDate.of(TODAY), Memo.EMPTY, new PhotoRef("https://img/x.jpg"), ctx);
        }
    }

    @Nested
    @DisplayName("하루 상한")
    class DailyCap {

        @Test
        void 온보딩_종료후_하루_상한을_넘으면_거부한다() {
            Territory t = Territory.empty(MAP);
            for (int i = 0; i < POLICY.dailyCap(); i++) checkIn(t, ME, seoul(i), veteran(NOON.plusSeconds(i)));
            assertThatThrownBy(() -> checkIn(t, ME, seoul(9), veteran(NOON.plusSeconds(10))))
                .isInstanceOf(ExplorationException.class)
                .hasMessageContaining("5곳")
                .satisfies(e -> assertThat(errorOf(e)).isEqualTo(ExplorationError.DAILY_CAP_EXCEEDED));
        }

        @Test
        void 가입후_72시간_온보딩_중에는_상한을_적용하지_않는다() {
            Territory t = Territory.empty(MAP);
            for (int i = 0; i < 20; i++) checkIn(t, ME, seoul(i), onboarding(NOON));
            assertThat(t.visitsOf(ME)).hasSize(20);
        }

        @Test
        void 온보딩_경계_72시간_정각부터_상한이_적용된다() {
            Territory t = Territory.empty(MAP);
            Instant joined = NOON.minus(Duration.ofHours(72));
            var atBoundary = new CheckInContext(POLICY, joined, NOON, KST);
            var justBefore = new CheckInContext(POLICY, joined.plusSeconds(1), NOON, KST);
            assertThat(atBoundary.inOnboarding()).isFalse();
            assertThat(justBefore.inOnboarding()).isTrue();
            for (int i = 0; i < 5; i++) checkIn(t, ME, seoul(i), atBoundary);
            assertThatThrownBy(() -> checkIn(t, ME, seoul(5), atBoundary))
                .satisfies(e -> assertThat(errorOf(e)).isEqualTo(ExplorationError.DAILY_CAP_EXCEEDED));
        }

        @Test
        void 상한은_날짜가_바뀌면_초기화되고_방문일이_아니라_처리시각_기준이다() {
            Territory t = Territory.empty(MAP);
            // 과거 날짜로 기록해도 오늘 처리한 건이면 오늘 상한에 센다
            for (int i = 0; i < 5; i++) {
                t.checkIn(ME, seoul(i), VisitDate.of(TODAY.minusYears(1)), Memo.EMPTY, null, veteran(NOON));
            }
            assertThatThrownBy(() -> checkIn(t, ME, seoul(5), veteran(NOON)))
                .satisfies(e -> assertThat(errorOf(e)).isEqualTo(ExplorationError.DAILY_CAP_EXCEEDED));
            Instant tomorrow = NOON.plus(Duration.ofDays(1));
            checkIn(t, ME, seoul(5), veteran(tomorrow));
            assertThat(t.checkInsOn(ME, veteran(tomorrow))).isEqualTo(1);
        }

        @Test
        void 상한은_멤버별이다() {
            Territory t = Territory.empty(MAP);
            for (int i = 0; i < 5; i++) checkIn(t, ME, seoul(i), veteran(NOON));
            checkIn(t, FRIEND, seoul(0), veteran(NOON));
            assertThat(t.checkInsOn(FRIEND, veteran(NOON))).isEqualTo(1);
        }

        @Test
        void 취소한_체크인은_상한에서_빠진다() {
            Territory t = Territory.empty(MAP);
            for (int i = 0; i < 5; i++) checkIn(t, ME, seoul(i), veteran(NOON));
            t.cancelVisit(ME, seoul(0).code());
            checkIn(t, ME, seoul(5), veteran(NOON));
            assertThat(t.checkInsOn(ME, veteran(NOON))).isEqualTo(5);
        }

        @Test
        void 무제한_정책은_상한을_우회한다() {
            Territory t = Territory.empty(MAP);
            var ctx = new CheckInContext(CheckInPolicy.unlimited(), NOON.minus(Duration.ofDays(30)), NOON, KST);
            for (int i = 0; i < 30; i++) checkIn(t, ME, seoul(i), ctx);
            assertThat(t.visitsOf(ME)).hasSize(30);
        }
    }

    @Nested
    @DisplayName("editVisit / cancelVisit")
    class EditAndCancel {

        @Test
        void 방문_기록의_날짜와_메모를_수정한다() {
            Territory t = Territory.empty(MAP);
            checkIn(t, ME, JONGNO, onboarding(NOON));
            Visit v = t.editVisit(ME, JONGNO.code(), VisitPatch.of(TODAY.minusDays(3), "  야간개장 ", null),
                onboarding(NOON));
            assertThat(v.visitDate().value()).isEqualTo(TODAY.minusDays(3));
            assertThat(v.memo().value()).isEqualTo("야간개장");
            assertThat(v.visitedAt()).isEqualTo(NOON);
        }

        @Test
        void 수정도_미래_날짜는_거부한다() {
            Territory t = Territory.empty(MAP);
            checkIn(t, ME, JONGNO, onboarding(NOON));
            assertThatThrownBy(() -> t.editVisit(ME, JONGNO.code(), VisitPatch.of(TODAY.plusDays(1), null, null),
                onboarding(NOON)))
                .satisfies(e -> assertThat(errorOf(e)).isEqualTo(ExplorationError.FUTURE_VISIT_DATE));
        }

        @Test
        void 방문하지_않은_지역의_수정과_취소는_거부한다() {
            Territory t = Territory.empty(MAP);
            checkIn(t, FRIEND, JONGNO, onboarding(NOON));
            assertThatThrownBy(() -> t.editVisit(ME, JONGNO.code(), VisitPatch.of(TODAY, "", null),
                onboarding(NOON)))
                .satisfies(e -> assertThat(errorOf(e)).isEqualTo(ExplorationError.VISIT_NOT_FOUND));
            assertThatThrownBy(() -> t.cancelVisit(ME, JONGNO.code()))
                .satisfies(e -> assertThat(errorOf(e)).isEqualTo(ExplorationError.VISIT_NOT_FOUND));
        }

        @Test
        void 취소는_방문을_지우고_다시_체크인할_수_있다() {
            Territory t = Territory.empty(MAP);
            checkIn(t, ME, JONGNO, onboarding(NOON));
            checkIn(t, ME, JUNG, onboarding(NOON));
            CancelResult c = t.cancelVisit(ME, JONGNO.code());
            assertThat(c.wasClaim()).isTrue();
            assertThat(c.remaining()).isEqualTo(1);
            assertThat(t.find(JONGNO.code(), ME)).isEmpty();
            assertThatThrownBy(() -> t.cancelVisit(ME, JONGNO.code()))
                .satisfies(e -> assertThat(errorOf(e)).isEqualTo(ExplorationError.VISIT_NOT_FOUND));
            CheckInResult again = checkIn(t, ME, JONGNO, onboarding(NOON.plusSeconds(5)));
            assertThat(again.facts().nth()).isEqualTo(2);
        }

        @Test
        void 선점자가_아닌_멤버의_취소는_선점이_아니다() {
            Territory t = Territory.empty(MAP);
            checkIn(t, ME, JONGNO, onboarding(NOON));
            checkIn(t, FRIEND, JONGNO, onboarding(NOON.plusSeconds(1)));
            assertThat(t.cancelVisit(FRIEND, JONGNO.code()).wasClaim()).isFalse();
            assertThat(t.cancelVisit(ME, JONGNO.code()).wasClaim()).isTrue();
        }
    }

    @Test
    void 복원_데이터에_같은_지역_멤버가_두번_있으면_거부한다() {
        Visit v = new Visit(JONGNO, ME, VisitDate.of(TODAY), Memo.EMPTY, null, Verification.NONE, NOON);
        Visit dup = new Visit(JONGNO, ME, VisitDate.of(TODAY), Memo.EMPTY, null, Verification.NONE, NOON);
        assertThatThrownBy(() -> Territory.restore(MAP, List.of(v, dup))).isInstanceOf(IllegalStateException.class);
    }
}
