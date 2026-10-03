package com.kobi.territory.exploration.domain.territory;

import static com.kobi.territory.exploration.domain.Fixtures.ACCOUNT;
import static com.kobi.territory.exploration.domain.Fixtures.ANON_MAP;
import static com.kobi.territory.exploration.domain.Fixtures.FRIEND;
import static com.kobi.territory.exploration.domain.Fixtures.GAPYEONG;
import static com.kobi.territory.exploration.domain.Fixtures.JONGNO;
import static com.kobi.territory.exploration.domain.Fixtures.JUNG;
import static com.kobi.territory.exploration.domain.Fixtures.KST;
import static com.kobi.territory.exploration.domain.Fixtures.MAP;
import static com.kobi.territory.exploration.domain.Fixtures.ME;
import static com.kobi.territory.exploration.domain.Fixtures.NOON;
import static com.kobi.territory.exploration.domain.Fixtures.POLICY;
import static com.kobi.territory.exploration.domain.Fixtures.THIRD;
import static com.kobi.territory.exploration.domain.Fixtures.TODAY;
import static com.kobi.territory.exploration.domain.Fixtures.checkIn;
import static com.kobi.territory.exploration.domain.Fixtures.joinedAt;
import static com.kobi.territory.exploration.domain.Fixtures.minutes;
import static com.kobi.territory.exploration.domain.Fixtures.onboarding;
import static com.kobi.territory.exploration.domain.Fixtures.photoRequired;
import static com.kobi.territory.exploration.domain.Fixtures.refusal;
import static com.kobi.territory.exploration.domain.Fixtures.retired;
import static com.kobi.territory.exploration.domain.Fixtures.seoul;
import static com.kobi.territory.exploration.domain.Fixtures.territory;
import static com.kobi.territory.exploration.domain.Fixtures.veteran;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.common.error.ErrorKind;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.domain.ExplorationError;
import com.kobi.territory.exploration.domain.ExplorationException;
import com.kobi.territory.exploration.domain.policy.CheckInPolicy;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 영토(지도 하나에 칠한 방문들)의 규칙. 회귀 출처: 1~4단계 QA(N3·P3-2·P3-3·P3-10·Q1·Q2·Q5, 결정 6, D2).
 */
@DisplayName("영토")
class TerritoryTest {

    @Nested
    @DisplayName("체크인")
    class CheckIn {

        @Nested
        @DisplayName("처음 칠할 때")
        class FirstPaint {

            @Test
            @DisplayName("첫 영토이자 시·도 첫 발이고 그 지역의 선점이 된다")
            void firstOfEverything() {
                CheckInResult result = checkIn(Territory.empty(MAP), ME, JONGNO, onboarding(NOON));
                assertThat(result.facts()).isEqualTo(new VisitFacts(false, 1, true, true));
                assertThat(result.mapId()).isEqualTo(MAP);
            }

            @Test
            @DisplayName("처리 시각은 서버 시계이고 고른 방문일은 기록으로만 남는다")
            void processedAtServerClock() {
                Visit visit = Territory.empty(MAP).checkIn(ME, JONGNO, VisitDate.of(TODAY.minusYears(2)), Memo.of("경복궁"), null,
                    onboarding(NOON)).visit();
                assertThat(visit.visitedAt()).isEqualTo(NOON);
                assertThat(visit.visitDate().value()).isEqualTo(TODAY.minusYears(2));
            }

            @Test
            @DisplayName("메모가 함께 남고 위치 인증은 아직 없다")
            void memoAndNoVerification() {
                Territory territory = Territory.empty(MAP);
                Visit visit = territory.checkIn(ME, JONGNO, VisitDate.of(TODAY), Memo.of("경복궁"), null, onboarding(NOON)).visit();
                assertThat(visit.memo().value()).isEqualTo("경복궁");
                assertThat(visit.verification()).isEqualTo(Verification.NONE);
                assertThat(territory.visitsOf(ME)).hasSize(1);
            }
        }

        @Nested
        @DisplayName("이어서 칠할 때")
        class NextPaint {

            @Test
            @DisplayName("같은 시·도의 두 번째 지역은 시·도 첫 발이 아니고 몇 번째 영토인지가 하나 오른다")
            void sameProvince() {
                Territory territory = territory().paint(ME, JONGNO).build();
                assertThat(checkIn(territory, ME, JUNG, onboarding(NOON)).facts()).isEqualTo(new VisitFacts(false, 2, false, true));
            }

            @Test
            @DisplayName("새 시·도의 지역은 다시 시·도 첫 발이다")
            void newProvince() {
                Territory territory = territory().paint(ME, JONGNO, JUNG).build();
                VisitFacts facts = checkIn(territory, ME, GAPYEONG, onboarding(NOON)).facts();
                assertThat(facts.firstInProvince()).isTrue();
                assertThat(facts.nth()).isEqualTo(3);
            }

            @Test
            @DisplayName("다른 멤버가 칠한 시·도라도 나에게는 시·도 첫 발이다")
            void provinceIsPerMember() {
                Territory territory = territory().paint(FRIEND, JONGNO).build();
                assertThat(checkIn(territory, ME, JUNG, onboarding(NOON)).facts().firstInProvince()).isTrue();
            }
        }

        @Nested
        @DisplayName("같은 지역을 다른 멤버가 이미 칠했을 때")
        class AlreadyPaintedByOther {

            @Test
            @DisplayName("나도 칠할 수 있지만 선점은 아니다")
            void paintsWithoutClaim() {
                Territory territory = territory().paint(ME, JONGNO).build();
                CheckInResult result = checkIn(territory, FRIEND, JONGNO, onboarding(minutes(5)));
                assertThat(result.facts()).isEqualTo(new VisitFacts(false, 1, true, false));
            }

            @Test
            @DisplayName("지역 색은 먼저 칠한 선점자의 것으로 남는다")
            void claimStaysWithFirst() {
                Territory territory = territory().paint(ME, JONGNO).paint(FRIEND, JONGNO).build();
                assertThat(territory.claimOf(JONGNO.code())).get().extracting(Visit::checkedInBy).isEqualTo(ME);
                assertThat(territory.claimedRegions()).hasSize(1);
            }
        }

        @Nested
        @DisplayName("방문일을 고를 때")
        class VisitDates {

            @Test
            @DisplayName("오늘과 아주 예전 날짜는 칠할 수 있다")
            void todayAndPastAllowed() {
                Territory territory = Territory.empty(MAP);
                territory.checkIn(ME, JONGNO, VisitDate.of(TODAY), Memo.EMPTY, null, onboarding(NOON));
                territory.checkIn(ME, JUNG, VisitDate.of(TODAY.minusYears(10)), Memo.EMPTY, null, onboarding(NOON));
                assertThat(territory.visitsOf(ME)).hasSize(2);
            }

            @Test
            @DisplayName("내일 날짜로는 칠할 수 없다")
            void futureRefused() {
                Territory territory = Territory.empty(MAP);
                assertThat(refusal(() -> territory.checkIn(ME, JONGNO, VisitDate.of(TODAY.plusDays(1)), Memo.EMPTY, null,
                    onboarding(NOON)))).isEqualTo(ExplorationError.FUTURE_VISIT_DATE);
            }

            @Test
            @DisplayName("오늘인지는 한국 시간으로 판단한다 — 밤 11시 반에 내일은 아직 미래다")
            void lateNightIsStillToday() {
                Instant lateNight = Instant.parse("2026-10-02T14:30:00Z"); // 23:30 KST
                assertThat(refusal(() -> Territory.empty(MAP).checkIn(ME, JONGNO, VisitDate.of(TODAY.plusDays(1)), Memo.EMPTY,
                    null, onboarding(lateNight)))).isEqualTo(ExplorationError.FUTURE_VISIT_DATE);
            }

            @Test
            @DisplayName("오늘인지는 한국 시간으로 판단한다 — 자정이 지나면 그날이 오늘이다")
            void afterMidnightIsNewDay() {
                Instant afterMidnight = Instant.parse("2026-10-02T15:30:00Z"); // 00:30 KST 다음 날
                Territory territory = Territory.empty(MAP);
                territory.checkIn(ME, JONGNO, VisitDate.of(TODAY.plusDays(1)), Memo.EMPTY, null, onboarding(afterMidnight));
                assertThat(territory.visitsOf(ME)).hasSize(1);
            }
        }

        @Nested
        @DisplayName("이미 칠한 지역을 다시 칠하려 할 때")
        class Duplicate {

            @Test
            @DisplayName("같은 멤버는 다시 칠할 수 없다")
            void refused() {
                Territory territory = territory().paint(ME, JONGNO).build();
                assertThat(refusal(() -> checkIn(territory, ME, JONGNO, onboarding(NOON)))).isEqualTo(ExplorationError.DUPLICATE_VISIT);
            }
        }

        @Nested
        @DisplayName("행정구역 개편으로 없어진 지역일 때")
        class RetiredRegion {

            @Test
            @DisplayName("새로 칠할 수 없고 규칙 위반으로 알린다")
            void refused() {
                Territory territory = Territory.empty(MAP);
                ExplorationException refused = (ExplorationException) org.assertj.core.api.Assertions.catchThrowable(
                    () -> checkIn(territory, ME, retired(JONGNO), onboarding(NOON)));
                assertThat(refused.error()).isEqualTo(ExplorationError.REGION_RETIRED);
                assertThat(refused.kind()).isEqualTo(ErrorKind.RULE_VIOLATION);
            }

            @Test
            @DisplayName("거절된 지역은 영토에 남지 않는다")
            void leavesNothing() {
                Territory territory = Territory.empty(MAP);
                refusal(() -> checkIn(territory, ME, retired(JONGNO), onboarding(NOON)));
                assertThat(territory.visits()).isEmpty();
            }
        }

        @Nested
        @DisplayName("사진이 필수인 지도일 때")
        class PhotoRequired {

            @Test
            @DisplayName("사진을 붙이면 칠할 수 있다")
            void withPhoto() {
                Territory territory = Territory.empty(MAP);
                territory.checkIn(ME, JONGNO, VisitDate.of(TODAY), Memo.EMPTY, new PhotoRef("https://img/x.jpg"), photoRequired(NOON));
                assertThat(territory.visitsOf(ME)).hasSize(1);
            }

            @Test
            @DisplayName("사진 없이는 칠할 수 없다")
            void withoutPhotoRefused() {
                assertThat(refusal(() -> Territory.empty(MAP).checkIn(ME, JONGNO, VisitDate.of(TODAY), Memo.EMPTY, null,
                    photoRequired(NOON)))).isEqualTo(ExplorationError.PHOTO_REQUIRED);
            }
        }
    }

    @Nested
    @DisplayName("하루 상한")
    class DailyCap {

        @Nested
        @DisplayName("가입 후 72시간이 지났을 때")
        class AfterOnboarding {

            @Test
            @DisplayName("하루 다섯 곳까지만 칠해지고 여섯 번째는 상한을 알리며 거절된다")
            void sixthRefused() {
                Territory territory = Territory.empty(MAP);
                for (int i = 0; i < POLICY.dailyCap(); i++) checkIn(territory, ME, seoul(i), veteran(NOON.plusSeconds(i)));
                assertThatThrownBy(() -> checkIn(territory, ME, seoul(9), veteran(NOON.plusSeconds(10))))
                    .isInstanceOf(ExplorationException.class)
                    .hasMessageContaining("5곳");
                assertThat(refusal(() -> checkIn(territory, ME, seoul(9), veteran(NOON.plusSeconds(10)))))
                    .isEqualTo(ExplorationError.DAILY_CAP_EXCEEDED);
            }

            @Test
            @DisplayName("예전 날짜로 기록해도 오늘 칠한 것이면 오늘 상한에 센다")
            void countsByProcessingDay() {
                Territory territory = Territory.empty(MAP);
                for (int i = 0; i < 5; i++) {
                    territory.checkIn(ME, seoul(i), VisitDate.of(TODAY.minusYears(1)), Memo.EMPTY, null, veteran(NOON));
                }
                assertThat(refusal(() -> checkIn(territory, ME, seoul(5), veteran(NOON)))).isEqualTo(ExplorationError.DAILY_CAP_EXCEEDED);
            }

            @Test
            @DisplayName("날짜가 바뀌면 다시 칠할 수 있다")
            void resetsNextDay() {
                Territory territory = Territory.empty(MAP);
                for (int i = 0; i < 5; i++) checkIn(territory, ME, seoul(i), veteran(NOON));
                Instant tomorrow = NOON.plus(Duration.ofDays(1));
                checkIn(territory, ME, seoul(5), veteran(tomorrow));
                assertThat(territory.checkInsOn(ME, veteran(tomorrow))).isEqualTo(1);
            }

            @Test
            @DisplayName("상한은 멤버마다 따로 센다")
            void perMember() {
                Territory territory = Territory.empty(MAP);
                for (int i = 0; i < 5; i++) checkIn(territory, ME, seoul(i), veteran(NOON));
                checkIn(territory, FRIEND, seoul(0), veteran(NOON));
                assertThat(territory.checkInsOn(FRIEND, veteran(NOON))).isEqualTo(1);
            }

            @Test
            @DisplayName("취소한 체크인은 오늘 상한에서 빠진다")
            void cancelledFreesSlot() {
                Territory territory = Territory.empty(MAP);
                for (int i = 0; i < 5; i++) checkIn(territory, ME, seoul(i), veteran(NOON));
                territory.cancelVisit(ME, seoul(0).code());
                checkIn(territory, ME, seoul(5), veteran(NOON));
                assertThat(territory.checkInsOn(ME, veteran(NOON))).isEqualTo(5);
            }

            @Test
            @DisplayName("지도장이 상한을 낮춘 지도에서는 낮춘 값까지만 칠해진다")
            void loweredCap() {
                Territory territory = Territory.empty(MAP);
                var lowered = new CheckInContext(new CheckInPolicy(2, Duration.ofHours(72), false), NOON.minus(Duration.ofDays(5)),
                    NOON, KST);
                checkIn(territory, ME, seoul(0), lowered);
                checkIn(territory, ME, seoul(1), lowered);
                assertThat(refusal(() -> checkIn(territory, ME, seoul(2), lowered))).isEqualTo(ExplorationError.DAILY_CAP_EXCEEDED);
            }
        }

        @Nested
        @DisplayName("가입 후 72시간 안일 때")
        class DuringOnboarding {

            @Test
            @DisplayName("예전에 간 곳을 몰아 채우도록 상한 없이 칠해진다")
            void noCap() {
                Territory territory = Territory.empty(MAP);
                for (int i = 0; i < 20; i++) checkIn(territory, ME, seoul(i), onboarding(NOON));
                assertThat(territory.visitsOf(ME)).hasSize(20);
            }

            @Test
            @DisplayName("72시간이 되기 1초 전까지는 온보딩이다")
            void justBeforeBoundary() {
                Instant joined = NOON.minus(Duration.ofHours(72));
                assertThat(joinedAt(joined.plusSeconds(1), NOON).inOnboarding()).isTrue();
            }

            @Test
            @DisplayName("72시간 정각부터는 상한이 적용된다")
            void atBoundary() {
                Territory territory = Territory.empty(MAP);
                var atBoundary = joinedAt(NOON.minus(Duration.ofHours(72)), NOON);
                assertThat(atBoundary.inOnboarding()).isFalse();
                for (int i = 0; i < 5; i++) checkIn(territory, ME, seoul(i), atBoundary);
                assertThat(refusal(() -> checkIn(territory, ME, seoul(5), atBoundary))).isEqualTo(ExplorationError.DAILY_CAP_EXCEEDED);
            }
        }

        @Nested
        @DisplayName("개발용 시드처럼 상한 없는 규칙일 때")
        class Unlimited {

            @Test
            @DisplayName("몇 곳이든 칠해진다")
            void bypasses() {
                Territory territory = Territory.empty(MAP);
                var ctx = new CheckInContext(CheckInPolicy.unlimited(), NOON.minus(Duration.ofDays(30)), NOON, KST);
                for (int i = 0; i < 30; i++) checkIn(territory, ME, seoul(i), ctx);
                assertThat(territory.visitsOf(ME)).hasSize(30);
            }
        }
    }

    @Nested
    @DisplayName("방문 기록 수정")
    class Edit {

        @Test
        @DisplayName("날짜와 메모를 고칠 수 있고 메모 앞뒤 공백은 잘린다")
        void editsDateAndMemo() {
            Territory territory = territory().paint(ME, JONGNO).build();
            Visit visit = territory.editVisit(ME, JONGNO.code(), VisitPatch.of(TODAY.minusDays(3), "  야간개장 ", null),
                onboarding(NOON));
            assertThat(visit.visitDate().value()).isEqualTo(TODAY.minusDays(3));
            assertThat(visit.memo().value()).isEqualTo("야간개장");
        }

        @Test
        @DisplayName("고쳐도 칠한 처리 시각은 그대로다")
        void keepsProcessingTime() {
            Territory territory = Territory.empty(MAP);
            checkIn(territory, ME, JONGNO, onboarding(NOON));
            Visit visit = territory.editVisit(ME, JONGNO.code(), VisitPatch.of(TODAY.minusDays(3), "야간개장", null),
                onboarding(NOON.plusSeconds(600)));
            assertThat(visit.visitedAt()).isEqualTo(NOON);
        }

        @Test
        @DisplayName("고치지 않은 항목은 그대로 남는다")
        void omittedKept() {
            Territory territory = Territory.empty(MAP);
            territory.checkIn(ME, JONGNO, VisitDate.of(TODAY.minusDays(9)), Memo.of("원래"), new PhotoRef("https://p/1.jpg"),
                onboarding(NOON));
            Visit visit = territory.editVisit(ME, JONGNO.code(), VisitPatch.of(null, "바뀜", null), onboarding(NOON));
            assertThat(visit.visitDate().value()).isEqualTo(TODAY.minusDays(9));
            assertThat(visit.memo().value()).isEqualTo("바뀜");
            assertThat(visit.photo().url()).isEqualTo("https://p/1.jpg");
        }

        @Test
        @DisplayName("빈 값으로 고치면 메모와 사진이 지워진다")
        void blankClears() {
            Territory territory = Territory.empty(MAP);
            territory.checkIn(ME, JONGNO, VisitDate.of(TODAY.minusDays(9)), Memo.of("원래"), new PhotoRef("https://p/1.jpg"),
                onboarding(NOON));
            Visit visit = territory.editVisit(ME, JONGNO.code(), VisitPatch.of(TODAY, "", ""), onboarding(NOON));
            assertThat(visit.visitDate().value()).isEqualTo(TODAY);
            assertThat(visit.memo().isEmpty()).isTrue();
            assertThat(visit.photo()).isNull();
        }

        @Test
        @DisplayName("내일 날짜로는 고칠 수 없다")
        void futureRefused() {
            Territory territory = territory().paint(ME, JONGNO).build();
            assertThat(refusal(() -> territory.editVisit(ME, JONGNO.code(), VisitPatch.of(TODAY.plusDays(1), null, null),
                onboarding(NOON)))).isEqualTo(ExplorationError.FUTURE_VISIT_DATE);
        }

        @Test
        @DisplayName("내가 칠하지 않은 지역은 고칠 수 없다")
        void notMineRefused() {
            Territory territory = territory().paint(FRIEND, JONGNO).build();
            assertThat(refusal(() -> territory.editVisit(ME, JONGNO.code(), VisitPatch.of(TODAY, "", null), onboarding(NOON))))
                .isEqualTo(ExplorationError.VISIT_NOT_FOUND);
        }

        @Nested
        @DisplayName("사진 필수가 나중에 켜진 지도일 때")
        class PhotoRuleTurnedOn {

            @Test
            @DisplayName("사진 없이 칠했던 방문도 메모는 고칠 수 있다")
            void memoOfPhotolessVisit() {
                Territory territory = territory().paint(ME, JONGNO).build();
                assertThat(territory.editVisit(ME, JONGNO.code(), VisitPatch.of(null, "메모만", null), photoRequired(minutes(1)))
                    .memo().value()).isEqualTo("메모만");
            }

            @Test
            @DisplayName("붙어 있던 사진은 지울 수 없다")
            void cannotRemovePhoto() {
                Territory territory = Territory.empty(MAP);
                var strict = photoRequired(minutes(1));
                territory.checkIn(ME, JUNG, VisitDate.of(strict.today()), Memo.EMPTY, new PhotoRef("https://example.com/a.jpg"), strict);
                assertThat(refusal(() -> territory.editVisit(ME, JUNG.code(), VisitPatch.of(null, null, ""), strict)))
                    .isEqualTo(ExplorationError.PHOTO_REQUIRED);
            }
        }
    }

    @Nested
    @DisplayName("체크인 취소")
    class Cancel {

        @Test
        @DisplayName("방문이 지워지고 내 남은 영토 수를 알려준다")
        void removesAndCounts() {
            Territory territory = territory().paint(ME, JONGNO, JUNG).build();
            CancelResult result = territory.cancelVisit(ME, JONGNO.code());
            assertThat(result.remaining()).isEqualTo(1);
            assertThat(territory.find(JONGNO.code(), ME)).isEmpty();
        }

        @Test
        @DisplayName("취소한 지역은 다시 칠할 수 있고 몇 번째 영토인지는 지금 영토 수로 다시 센다")
        void canRepaint() {
            Territory territory = territory().paint(ME, JONGNO, JUNG).build();
            territory.cancelVisit(ME, JONGNO.code());
            assertThat(checkIn(territory, ME, JONGNO, onboarding(minutes(5))).facts().nth()).isEqualTo(2);
        }

        @Test
        @DisplayName("선점자의 취소였는지 알려준다")
        void tellsWasClaim() {
            Territory territory = territory().paint(ME, JONGNO).paint(FRIEND, JONGNO).build();
            assertThat(territory.cancelVisit(FRIEND, JONGNO.code()).wasClaim()).isFalse();
            assertThat(territory.cancelVisit(ME, JONGNO.code()).wasClaim()).isTrue();
        }

        @Test
        @DisplayName("다른 멤버가 칠한 같은 지역이 남으면 지역이 지도에 남아 있다고 알려준다")
        void regionStillOnMap() {
            Territory territory = territory().paint(ME, JONGNO).paint(FRIEND, JONGNO).build();
            assertThat(territory.cancelVisit(ME, JONGNO.code()).regionStillOnMap()).isTrue();
        }

        @Test
        @DisplayName("마지막 방문을 취소하면 지역이 지도에서 사라졌다고 알려준다")
        void regionGone() {
            Territory territory = territory().paint(ME, JONGNO).paint(FRIEND, JONGNO).build();
            territory.cancelVisit(ME, JONGNO.code());
            assertThat(territory.cancelVisit(FRIEND, JONGNO.code()).regionStillOnMap()).isFalse();
        }

        @Test
        @DisplayName("이미 취소한 지역은 다시 취소할 수 없다")
        void twiceRefused() {
            Territory territory = territory().paint(ME, JONGNO).build();
            territory.cancelVisit(ME, JONGNO.code());
            assertThat(refusal(() -> territory.cancelVisit(ME, JONGNO.code()))).isEqualTo(ExplorationError.VISIT_NOT_FOUND);
        }

        @Test
        @DisplayName("다른 멤버가 칠한 지역은 내가 취소할 수 없다")
        void notMineRefused() {
            Territory territory = territory().paint(FRIEND, JONGNO).build();
            assertThat(refusal(() -> territory.cancelVisit(ME, JONGNO.code()))).isEqualTo(ExplorationError.VISIT_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("체크인 회차")
    class Generation {

        @Test
        @DisplayName("처음 칠하면 1회차다")
        void startsAtOne() {
            assertThat(checkIn(Territory.empty(MAP), ME, JONGNO, onboarding(minutes(0))).visit().generation()).isEqualTo(1);
        }

        @Test
        @DisplayName("취소 후 다시 칠하면 회차가 오른다")
        void risesAfterCancel() {
            Territory territory = territory().paint(ME, JONGNO).build();
            territory.cancelVisit(ME, JONGNO.code());
            assertThat(checkIn(territory, ME, JONGNO, onboarding(minutes(1))).visit().generation()).isEqualTo(2);
        }

        @Test
        @DisplayName("회차는 멤버마다 따로 센다")
        void perMember() {
            Territory territory = territory().paint(ME, JONGNO).build();
            territory.cancelVisit(ME, JONGNO.code());
            checkIn(territory, ME, JONGNO, onboarding(minutes(1)));
            assertThat(checkIn(territory, FRIEND, JONGNO, onboarding(minutes(2))).visit().generation()).isEqualTo(1);
            assertThat(territory.changedGenerations()).hasSize(2);
        }

        @Test
        @DisplayName("회차 기록이 없던 예전 방문은 그 방문의 회차부터 이어진다")
        void continuesFromLegacyVisit() {
            Territory first = territory().paint(ME, JONGNO).build();
            Territory restored = Territory.restore(MAP, first.allVisits());
            restored.cancelVisit(ME, JONGNO.code());
            assertThat(checkIn(restored, ME, JONGNO, onboarding(minutes(1))).visit().generation()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("선점")
    class Claim {

        @Test
        @DisplayName("지역마다 먼저 칠한 방문이 선점이고 지역 순서대로 보인다")
        void claimPerRegion() {
            Territory territory = territory().paint(FRIEND, JUNG).paint(ME, JONGNO).paint(ME, JUNG).build();
            assertThat(territory.claims()).extracting(visit -> visit.regionCode().value() + "=" + visit.checkedInBy().value())
                .containsExactly(JONGNO.code().value() + "=" + ME.value(), JUNG.code().value() + "=" + FRIEND.value());
            assertThat(List.copyOf(territory.claims())).hasSize(2);
        }

        @Test
        @DisplayName("같은 지역 안에서 칠한 순서가 선점 순서가 된다")
        void claimOrder() {
            Territory territory = territory().paint(ME, JONGNO).paint(FRIEND, JONGNO).paint(THIRD, JONGNO).build();
            assertThat(territory.claimOrderOf(territory.find(JONGNO.code(), ME).orElseThrow())).isEqualTo(1);
            assertThat(territory.claimOrderOf(territory.find(JONGNO.code(), THIRD).orElseThrow())).isEqualTo(3);
        }

        @Nested
        @DisplayName("선점자가 체크인을 취소할 때")
        class ClaimerCancels {

            @Test
            @DisplayName("선점이 다음으로 칠한 멤버에게 넘어간다")
            void passesToNext() {
                Territory territory = territory().paint(ME, JONGNO).paint(FRIEND, JONGNO).paint(THIRD, JONGNO).build();
                CancelResult result = territory.cancelVisit(ME, JONGNO.code());
                assertThat(result.transferredClaim()).hasValueSatisfying(transfer -> {
                    assertThat(transfer.from()).isEqualTo(ME);
                    assertThat(transfer.to()).isEqualTo(FRIEND);
                    assertThat(transfer.reason()).isEqualTo(ClaimTransfer.Reason.CANCELLED);
                });
                assertThat(territory.claimOf(JONGNO.code()).orElseThrow().checkedInBy()).isEqualTo(FRIEND);
            }

            @Test
            @DisplayName("혼자 칠한 지역이면 넘길 사람이 없다")
            void aloneNoTransfer() {
                Territory territory = territory().paint(ME, JUNG).build();
                assertThat(territory.cancelVisit(ME, JUNG.code()).transferredClaim()).isEmpty();
            }
        }

        @Test
        @DisplayName("선점자가 아닌 멤버의 취소로는 선점이 움직이지 않는다")
        void nonClaimerCancel() {
            Territory territory = territory().paint(ME, JONGNO).paint(FRIEND, JONGNO).build();
            assertThat(territory.cancelVisit(FRIEND, JONGNO.code()).transferredClaim()).isEmpty();
            assertThat(territory.claimOf(JONGNO.code()).orElseThrow().checkedInBy()).isEqualTo(ME);
        }
    }

    @Nested
    @DisplayName("지도장 이의")
    class Dispute {

        @Test
        @DisplayName("지도장은 방문에 이의를 표시하고 다시 풀 수 있다")
        void markAndClear() {
            Territory territory = territory().paint(FRIEND, JONGNO).build();
            assertThat(territory.dispute(JONGNO.code(), FRIEND, true).disputed()).isTrue();
            assertThat(territory.dispute(JONGNO.code(), FRIEND, false).disputed()).isFalse();
        }

        @Test
        @DisplayName("이의 표시된 방문을 모아 볼 수 있다")
        void listsDisputed() {
            Territory territory = territory().paint(FRIEND, JONGNO).paint(ME, JUNG).build();
            territory.dispute(JONGNO.code(), FRIEND, true);
            assertThat(territory.disputedVisits()).extracting(Visit::regionCode).containsExactly(JONGNO.code());
        }

        @Test
        @DisplayName("이의가 붙어도 영토의 선점과 칠한 지역은 그대로다")
        void doesNotChangeTerritory() {
            Territory territory = territory().paint(FRIEND, JONGNO).paint(ME, JONGNO).build();
            territory.dispute(JONGNO.code(), FRIEND, true);
            assertThat(territory.claimOf(JONGNO.code()).orElseThrow().checkedInBy()).isEqualTo(FRIEND);
            assertThat(territory.claimedRegions()).hasSize(1);
            assertThat(territory.visitsOf(FRIEND)).hasSize(1);
        }

        @Test
        @DisplayName("탈퇴로 숨겨진 방문에는 이의를 걸 수 없다")
        void hiddenRefused() {
            Territory territory = territory().paint(FRIEND, JONGNO).build();
            territory.hideMember(FRIEND, minutes(1));
            assertThat(refusal(() -> territory.dispute(JONGNO.code(), FRIEND, true))).isEqualTo(ExplorationError.VISIT_NOT_FOUND);
        }

        @Test
        @DisplayName("아무도 칠하지 않은 지역에는 이의를 걸 수 없다")
        void unpaintedRefused() {
            Territory territory = Territory.empty(MAP);
            assertThatThrownBy(() -> territory.dispute(RegionCode.of("KR-11999"), ME, true)).isInstanceOf(ExplorationException.class);
        }
    }

    @Nested
    @DisplayName("다른 멤버가 보는 방문")
    class Privacy {

        Territory shared() {
            Territory territory = Territory.empty(MAP);
            territory.checkIn(FRIEND, JONGNO, VisitDate.of(onboarding(minutes(0)).today()), Memo.of("비밀"),
                new PhotoRef("https://example.com/b.jpg"), onboarding(minutes(0)));
            territory.checkIn(ME, JUNG, VisitDate.of(onboarding(minutes(1)).today()), Memo.of("내 메모"), null, onboarding(minutes(1)));
            return territory;
        }

        VisitView viewOf(List<VisitView> views, com.kobi.territory.common.model.ExplorerId who) {
            return views.stream().filter(view -> view.checkedInBy().equals(who)).findFirst().orElseThrow();
        }

        @Test
        @DisplayName("다른 멤버 방문의 메모와 사진은 비워 보여준다")
        void hidesOthersMemoAndPhoto() {
            VisitView friends = viewOf(shared().viewedBy(ME), FRIEND);
            assertThat(friends.memo().isEmpty()).isTrue();
            assertThat(friends.photo()).isNull();
        }

        @Test
        @DisplayName("다른 멤버 방문도 색칠과 선점 여부는 보인다")
        void showsColoring() {
            Territory territory = shared();
            assertThat(viewOf(territory.viewedBy(ME), FRIEND).claim()).isTrue();
            assertThat(territory.claimCountOf(FRIEND)).isEqualTo(1);
        }

        @Test
        @DisplayName("내 방문의 메모는 나에게 그대로 보인다")
        void showsMine() {
            assertThat(viewOf(shared().viewedBy(ME), ME).memo().value()).isEqualTo("내 메모");
        }
    }

    @Nested
    @DisplayName("탈퇴 유예")
    class Leave {

        /** 종로구: 나 선점 + 친구, 가평: 나 혼자, 중구: 친구 혼자. */
        Territory shared() {
            return territory().paint(ME, JONGNO).paint(FRIEND, JONGNO).paint(ME, GAPYEONG).paint(FRIEND, JUNG).build();
        }

        @Nested
        @DisplayName("멤버가 탈퇴하면")
        class OnLeave {

            @Test
            @DisplayName("그 멤버의 방문이 숨겨진다")
            void hides() {
                Territory territory = shared();
                HideResult result = territory.hideMember(ME, minutes(10));
                assertThat(result.hidden()).containsExactlyInAnyOrder(JONGNO.code(), GAPYEONG.code());
                assertThat(territory.visitsOf(ME)).isEmpty();
            }

            @Test
            @DisplayName("숨겨질 뿐 유예가 끝나기 전까지 지워지지 않는다")
            void notDeletedYet() {
                Territory territory = shared();
                territory.hideMember(ME, minutes(10));
                assertThat(territory.allVisits()).hasSize(4);
            }

            @Test
            @DisplayName("그가 선점한 지역은 다음으로 칠한 멤버에게 넘어간다")
            void claimPasses() {
                HideResult result = shared().hideMember(ME, minutes(10));
                assertThat(result.claimTransfers()).singleElement().satisfies(transfer -> {
                    assertThat(transfer.region()).isEqualTo(JONGNO);
                    assertThat(transfer.to()).isEqualTo(FRIEND);
                    assertThat(transfer.reason()).isEqualTo(ClaimTransfer.Reason.LEFT);
                });
            }

            @Test
            @DisplayName("그 혼자 칠했던 지역은 지도에서 사라진다")
            void aloneRegionsGone() {
                Territory territory = shared();
                assertThat(territory.hideMember(ME, minutes(10)).regionsGone()).containsExactly(GAPYEONG.code());
                assertThat(territory.claimedRegions()).extracting(RegionSnapshot::code)
                    .containsExactlyInAnyOrder(JONGNO.code(), JUNG.code());
            }

            @Test
            @DisplayName("사라진 지역을 다른 멤버가 칠하면 그 멤버가 선점한다")
            void goneRegionClaimable() {
                Territory territory = shared();
                territory.hideMember(ME, minutes(10));
                assertThat(territory.factsFor(FRIEND, GAPYEONG).firstClaim()).isTrue();
            }

            @Test
            @DisplayName("같은 탈퇴를 다시 처리해도 더 숨길 것이 없다")
            void idempotent() {
                Territory territory = shared();
                territory.hideMember(ME, minutes(10));
                assertThat(territory.hideMember(ME, minutes(11)).hidden()).isEmpty();
            }

            @Test
            @DisplayName("탈퇴 처리가 늦게 와도 재가입 뒤 새로 칠한 방문은 숨기지 않는다")
            void lateLeaveSparesNewVisits() {
                Territory territory = Territory.empty(MAP);
                checkIn(territory, ME, JONGNO, onboarding(minutes(0)));
                checkIn(territory, ME, JUNG, onboarding(minutes(5)));
                assertThat(territory.hideMember(ME, minutes(2)).hidden()).containsExactly(JONGNO.code());
                assertThat(territory.visitsOf(ME)).extracting(Visit::regionCode).containsExactly(JUNG.code());
            }
        }

        @Nested
        @DisplayName("유예 안에 다시 합류하면")
        class OnRejoin {

            @Test
            @DisplayName("숨겼던 방문이 돌아온다")
            void restores() {
                Territory territory = shared();
                territory.hideMember(ME, minutes(10));
                RestoreResult result = territory.restoreMember(ME, minutes(20));
                assertThat(result.restored()).containsExactlyInAnyOrder(JONGNO.code(), GAPYEONG.code());
                assertThat(territory.visitsOf(ME)).hasSize(2);
            }

            @Test
            @DisplayName("지도에서 사라졌던 지역이 다시 칠해진다")
            void regionsBack() {
                Territory territory = shared();
                territory.hideMember(ME, minutes(10));
                assertThat(territory.restoreMember(ME, minutes(20)).regionsBack()).containsExactly(GAPYEONG.code());
                assertThat(territory.claimOf(GAPYEONG.code()).orElseThrow().checkedInBy()).isEqualTo(ME);
            }

            @Test
            @DisplayName("넘어간 선점은 돌아오지 않는다")
            void claimStaysAway() {
                Territory territory = shared();
                territory.hideMember(ME, minutes(10));
                territory.restoreMember(ME, minutes(20));
                assertThat(territory.claimOf(JONGNO.code()).orElseThrow().checkedInBy()).isEqualTo(FRIEND);
            }

            @Test
            @DisplayName("같은 복구를 다시 처리해도 더 돌아올 것이 없다")
            void idempotent() {
                Territory territory = shared();
                territory.hideMember(ME, minutes(10));
                territory.restoreMember(ME, minutes(20));
                assertThat(territory.restoreMember(ME, minutes(21)).restored()).isEmpty();
            }
        }

        @Nested
        @DisplayName("유예가 끝나면")
        class OnPurge {

            @Test
            @DisplayName("숨긴 방문만 지운다")
            void purgesHiddenOnly() {
                Territory territory = shared();
                territory.hideMember(ME, minutes(10));
                assertThat(territory.purgeHidden(ME)).hasSize(2);
                assertThat(territory.allVisits()).extracting(Visit::checkedInBy).containsOnly(FRIEND);
            }

            @Test
            @DisplayName("다시 처리해도 더 지울 것이 없다")
            void idempotent() {
                Territory territory = shared();
                territory.hideMember(ME, minutes(10));
                territory.purgeHidden(ME);
                assertThat(territory.purgeHidden(ME)).isEmpty();
            }
        }
    }

    @Nested
    @DisplayName("익명 탐험가의 개인 영토를 계정 영토로 옮길 때")
    class Absorb {

        Territory accountTerritory() {
            return territory().paint(ME, JONGNO, LocalDate.of(2026, 9, 1), "계정메모")
                .paint(ME, JUNG, LocalDate.of(2026, 3, 1), "계정중구").build();
        }

        Territory anonymousTerritory() {
            return territory(ANON_MAP).paint(FRIEND, JONGNO, LocalDate.of(2025, 1, 1), "익명메모")
                .paint(FRIEND, JUNG, LocalDate.of(2026, 5, 1), "익명중구")
                .paint(FRIEND, GAPYEONG, LocalDate.of(2026, 9, 30), "").build();
        }

        @Test
        @DisplayName("옮기기 전에 몇 곳이 옮겨지고 그중 몇 곳이 새 지역인지 알려준다")
        void summary() {
            assertThat(accountTerritory().mergeSummary(anonymousTerritory(), FRIEND, ME)).isEqualTo(new MergeSummary(3, 1));
        }

        @Test
        @DisplayName("계정에 없던 지역은 새로 옮겨진다")
        void addsNewRegions() {
            Territory account = accountTerritory();
            AbsorbResult result = account.absorb(anonymousTerritory(), FRIEND, ME);
            assertThat(result.added()).containsExactly(GAPYEONG.code());
            assertThat(result.moved()).isEqualTo(2);
            assertThat(account.find(GAPYEONG.code(), ME).orElseThrow().generation()).isEqualTo(1);
            assertThat(account.visits()).hasSize(3);
        }

        @Test
        @DisplayName("같은 지역은 방문일이 더 이른 쪽이 남고 메모와 사진도 그쪽 것이다")
        void earlierVisitWins() {
            Territory account = accountTerritory();
            assertThat(account.absorb(anonymousTerritory(), FRIEND, ME).replaced()).containsExactly(JONGNO.code());
            Visit jongno = account.find(JONGNO.code(), ME).orElseThrow();
            assertThat(jongno.visitDate().value()).isEqualTo(LocalDate.of(2025, 1, 1));
            assertThat(jongno.memo().value()).isEqualTo("익명메모");
            assertThat(jongno.photo().url()).endsWith("익명메모");
        }

        @Test
        @DisplayName("계정 쪽 방문일이 더 이르면 계정 방문이 그대로 남는다")
        void accountEarlierKept() {
            Territory account = accountTerritory();
            account.absorb(anonymousTerritory(), FRIEND, ME);
            assertThat(account.find(JUNG.code(), ME).orElseThrow().memo().value()).isEqualTo("계정중구");
        }

        @Test
        @DisplayName("바꿔 남긴 방문은 계정 탐험가의 다음 회차가 된다")
        void nextGeneration() {
            Territory account = accountTerritory();
            account.absorb(anonymousTerritory(), FRIEND, ME);
            assertThat(account.find(JONGNO.code(), ME).orElseThrow().generation()).isEqualTo(2);
        }

        @Test
        @DisplayName("옮긴 방문은 계정 탐험가의 것이고 익명 영토는 그대로 둔다")
        void ownershipAndSourceUntouched() {
            Territory account = accountTerritory();
            Territory anonymous = anonymousTerritory();
            account.absorb(anonymous, FRIEND, ME);
            assertThat(account.visitsOf(FRIEND)).isEmpty();
            assertThat(anonymous.visitsOf(FRIEND)).hasSize(3);
        }

        @Test
        @DisplayName("방문일이 같으면 계정 쪽이 남는다")
        void tieKeepsAccount() {
            Territory account = territory().paint(ME, JONGNO, LocalDate.of(2026, 1, 1), "계정").build();
            Territory anonymous = territory(ANON_MAP).paint(FRIEND, JONGNO, LocalDate.of(2026, 1, 1), "익명").build();
            assertThat(account.absorb(anonymous, FRIEND, ME).moved()).isZero();
            assertThat(account.find(JONGNO.code(), ME).orElseThrow().memo().value()).isEqualTo("계정");
        }

        @Test
        @DisplayName("다시 옮겨도 결과가 같다")
        void idempotent() {
            Territory account = Territory.empty(MAP);
            Territory anonymous = territory(ANON_MAP).paint(FRIEND, JONGNO, LocalDate.of(2025, 1, 1), "메모").build();
            account.absorb(anonymous, FRIEND, ME);
            assertThat(account.absorb(anonymous, FRIEND, ME).moved()).isZero();
            assertThat(account.visits()).hasSize(1);
            assertThat(account.find(JONGNO.code(), ME).orElseThrow().generation()).isEqualTo(1);
        }

        @Test
        @DisplayName("빈 익명 영토에서는 아무것도 옮기지 않는다")
        void emptySource() {
            Territory account = Territory.empty(MAP);
            assertThat(account.mergeSummary(Territory.empty(ANON_MAP), FRIEND, ME)).isEqualTo(new MergeSummary(0, 0));
            assertThat(account.absorb(Territory.empty(ANON_MAP), FRIEND, ME).added()).isEmpty();
        }

        @Test
        @DisplayName("옮긴 뒤 익명 영토를 정리하면 숨긴 방문까지 모두 지워진다")
        void releaseClearsAll() {
            Territory anonymous = territory(ANON_MAP).paint(FRIEND, JONGNO).paint(FRIEND, JUNG).build();
            anonymous.hideMember(FRIEND, NOON.plusSeconds(3600));
            assertThat(anonymous.releaseMember(FRIEND)).extracting(Visit::regionCode)
                .containsExactlyInAnyOrder(JONGNO.code(), JUNG.code());
            assertThat(anonymous.allVisits()).isEmpty();
        }

        @Test
        @DisplayName("정리를 다시 해도 안전하다")
        void releaseIdempotent() {
            Territory anonymous = territory(ANON_MAP).paint(FRIEND, JONGNO).build();
            anonymous.releaseMember(FRIEND);
            assertThat(anonymous.releaseMember(FRIEND)).isEmpty();
        }
    }

    @Nested
    @DisplayName("공유 지도의 익명 탐험가 방문을 계정 탐험가에게 넘길 때")
    class Reassign {

        @Test
        @DisplayName("익명 탐험가의 방문이 모두 계정 탐험가의 것이 된다")
        void allReassigned() {
            Territory shared = territory().paint(FRIEND, JONGNO).paint(THIRD, JUNG).paint(FRIEND, JUNG).build();
            assertThat(shared.reassignMember(FRIEND, ACCOUNT).reassigned()).containsExactlyInAnyOrder(JONGNO.code(), JUNG.code());
            assertThat(shared.visitsOf(FRIEND)).isEmpty();
            assertThat(shared.visitsOf(ACCOUNT)).hasSize(2);
        }

        @Test
        @DisplayName("선점 순서와 메모가 그대로라 선점자가 같은 사람으로 이어진다")
        void claimRankKept() {
            Territory shared = territory().paint(FRIEND, JONGNO, LocalDate.of(2026, 9, 1), "익명")
                .paint(THIRD, JONGNO, LocalDate.of(2026, 8, 1), "").build();
            Instant claimRank = shared.claimOf(JONGNO.code()).orElseThrow().claimRankAt();
            shared.reassignMember(FRIEND, ACCOUNT);
            Visit claim = shared.claimOf(JONGNO.code()).orElseThrow();
            assertThat(claim.checkedInBy()).isEqualTo(ACCOUNT);
            assertThat(claim.claimRankAt()).isEqualTo(claimRank);
            assertThat(claim.memo().value()).isEqualTo("익명");
        }

        @Test
        @DisplayName("다른 멤버의 선점은 바뀌지 않는다")
        void othersClaimUntouched() {
            Territory shared = territory().paint(THIRD, JUNG).paint(FRIEND, JUNG).build();
            shared.reassignMember(FRIEND, ACCOUNT);
            assertThat(shared.claimOf(JUNG.code()).orElseThrow().checkedInBy()).isEqualTo(THIRD);
        }

        @Test
        @DisplayName("탈퇴로 숨겨진 방문도 숨긴 채로 넘어간다")
        void hiddenStaysHidden() {
            Territory shared = territory().paint(FRIEND, JONGNO).paint(THIRD, JUNG).build();
            shared.hideMember(FRIEND, minutes(10));
            shared.reassignMember(FRIEND, ACCOUNT);
            assertThat(shared.visitsOf(ACCOUNT)).isEmpty();
            assertThat(shared.allVisits()).extracting(Visit::checkedInBy).containsExactlyInAnyOrder(ACCOUNT, THIRD);
        }

        @Test
        @DisplayName("이미 넘긴 뒤 다시 처리하면 바뀌는 것이 없다")
        void idempotent() {
            Territory shared = territory().paint(FRIEND, JONGNO).build();
            shared.reassignMember(FRIEND, ACCOUNT);
            assertThat(shared.reassignMember(FRIEND, ACCOUNT).changed()).isFalse();
        }

        @Nested
        @DisplayName("계정 탐험가도 같은 지역을 칠했을 때")
        class BothPainted {

            /** 종로구: 계정이 먼저(방문일도 이름), 중구: 익명이 먼저(계정 방문일은 훨씬 이름). */
            Territory shared() {
                return territory().paint(ACCOUNT, JONGNO, LocalDate.of(2025, 1, 1), "계정")
                    .paint(FRIEND, JONGNO, LocalDate.of(2026, 9, 1), "익명")
                    .paint(FRIEND, JUNG, LocalDate.of(2026, 9, 1), "익명중구")
                    .paint(ACCOUNT, JUNG, LocalDate.of(2020, 1, 1), "계정중구").build();
            }

            @Test
            @DisplayName("계정 쪽이 먼저 칠했으면 익명 방문은 버려진다")
            void accountFirstKept() {
                Territory shared = shared();
                assertThat(shared.reassignMember(FRIEND, ACCOUNT).dropped()).containsExactly(JONGNO.code());
                assertThat(shared.find(JONGNO.code(), ACCOUNT).orElseThrow().memo().value()).isEqualTo("계정");
            }

            @Test
            @DisplayName("방문일이 아니라 선점 순서가 이른 쪽이 남는다")
            void earlierClaimWinsOverEarlierDate() {
                Territory shared = shared();
                assertThat(shared.reassignMember(FRIEND, ACCOUNT).replaced()).containsExactly(JUNG.code());
                Visit jung = shared.find(JUNG.code(), ACCOUNT).orElseThrow();
                assertThat(jung.memo().value()).isEqualTo("익명중구");
                assertThat(jung.visitDate().value()).isEqualTo(LocalDate.of(2026, 9, 1));
                assertThat(shared.visits()).hasSize(2);
            }

            @Test
            @DisplayName("남긴 익명 방문은 계정 탐험가의 다음 회차가 된다")
            void nextGeneration() {
                Territory shared = shared();
                shared.reassignMember(FRIEND, ACCOUNT);
                assertThat(shared.find(JUNG.code(), ACCOUNT).orElseThrow().generation()).isEqualTo(2);
            }
        }
    }

    @Nested
    @DisplayName("재계산을 위해 칠한 순서대로 다시 볼 때")
    class History {

        @Test
        @DisplayName("처리 시각 순으로 그때의 시·도 첫 발과 몇 번째 영토를 다시 계산한다")
        void recomputesFacts() {
            Territory territory = territory().paint(ME, JUNG).paint(FRIEND, JUNG).paint(ME, JONGNO).build();
            var history = territory.history();
            assertThat(history).extracting(result -> result.visit().regionCode().value() + "/" + result.visit().checkedInBy().equals(ME))
                .containsExactly("KR-11020/true", "KR-11020/false", "KR-11010/true");
            assertThat(history.get(0).facts()).isEqualTo(new VisitFacts(false, 1, true, true));
            assertThat(history.get(1).facts()).isEqualTo(new VisitFacts(false, 1, true, false));
            assertThat(history.get(2).facts()).isEqualTo(new VisitFacts(false, 2, false, true));
        }

        @Test
        @DisplayName("선점은 칠한 순서가 아니라 지금의 선점자를 따른다 — 재가입 뒤에도")
        void claimFollowsCurrent() {
            Territory territory = territory().paint(ME, JONGNO).paint(FRIEND, JONGNO).build();
            territory.hideMember(ME, minutes(2));
            territory.restoreMember(ME, minutes(3));
            assertThat(territory.history()).extracting(result -> result.visit().checkedInBy() + "=" + result.facts().firstClaim())
                .containsExactly(ME + "=false", FRIEND + "=true");
        }
    }

    @Nested
    @DisplayName("기록된 영토도 같은 방문 규칙을 따른다")
    class Restore {

        @Test
        @DisplayName("같은 멤버의 같은 지역 방문이 둘인 기록은 거절된다")
        void duplicateRefused() {
            Visit visit = new Visit(JONGNO, ME, VisitDate.of(TODAY), Memo.EMPTY, null, Verification.NONE, NOON);
            Visit dup = new Visit(JONGNO, ME, VisitDate.of(TODAY), Memo.EMPTY, null, Verification.NONE, NOON);
            assertThatThrownBy(() -> Territory.restore(MAP, List.of(visit, dup))).isInstanceOf(IllegalStateException.class);
        }
    }
}
