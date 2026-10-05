package com.kobi.territory.catalog.domain.lineup;

import static com.kobi.territory.catalog.domain.lineup.LineupFixtures.그날_정오;
import static com.kobi.territory.catalog.domain.lineup.LineupFixtures.벚꽃_열두_지역;
import static com.kobi.territory.catalog.domain.lineup.LineupFixtures.봄;
import static com.kobi.territory.catalog.domain.lineup.LineupFixtures.봄2027;
import static com.kobi.territory.catalog.domain.lineup.LineupFixtures.봄_고르기;
import static com.kobi.territory.catalog.domain.lineup.LineupFixtures.서른날전부터_7일마다_자동확정;
import static com.kobi.territory.catalog.domain.lineup.LineupFixtures.읽음;
import static com.kobi.territory.catalog.domain.lineup.LineupFixtures.진해;
import static com.kobi.territory.catalog.domain.lineup.LineupFixtures.축제;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.common.error.TerritoryException;
import com.kobi.territory.common.model.RegionCode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 계절 회차 지역 목록(봄 2027 — 2027-03-20 시작): 모으면 후보, 확정하면 그 회차에 쓰는 목록. 회차가 열리면 고정되고, 실패하면 지금 목록을 지킨다.
 * 자동 수집은 시작 30일 전부터 7일마다, 관리자가 확정한 회차는 덮지 않는다.
 */
@DisplayName("계절 회차 지역 목록")
class SeasonLineupTest {

    private static final Instant 시작_전 = 그날_정오(2027, 2, 25);
    private static final Instant 시작_순간 = 봄2027.startsAt();
    private static final FestivalFetch.Failed 키_거절 = new FestivalFetch.Failed(FetchFailure.KEY_REJECTED, "코드 30");

    private static SeasonLineup 새_회차() {
        return SeasonLineup.start(봄2027);
    }

    private static SeasonLineup 열곳을_모은_회차(Instant at) {
        SeasonLineup lineup = 새_회차();
        lineup.record(읽음(벚꽃_열두_지역()), 봄_고르기(), at);
        return lineup;
    }

    @Nested
    @DisplayName("아무것도 모으지 않았을 때")
    class Untouched {

        @Test
        @DisplayName("기본 목록(AI 추정)을 쓴다")
        void defaults() {
            LineupRegions inEffect = 새_회차().inEffect(봄.regions());

            assertThat(inEffect.codes()).containsExactlyElementsOf(봄.regions());
            assertThat(inEffect.provenance()).isEqualTo("ai-estimate");
            assertThat(inEffect.hasEvidence()).isFalse();
        }
    }

    @Nested
    @DisplayName("모으면")
    class Collect {

        @Test
        @DisplayName("후보가 되고 확정 전까지 쓰는 목록은 그대로다")
        void candidateOnly() {
            SeasonLineup lineup = 열곳을_모은_회차(시작_전);

            assertThat(lineup.candidate().regions().count(LineupProvenance.TOURAPI)).isEqualTo(10);
            assertThat(lineup.inEffect(봄.regions()).codes()).containsExactlyElementsOf(봄.regions());
            assertThat(lineup.lastAttempt().outcome()).isEqualTo(CollectionAttempt.Outcome.COLLECTED);
        }

        @Test
        @DisplayName("근거 지역이 모자라 채웠으면 일부만 모은 것으로 남는다")
        void partial() {
            SeasonLineup lineup = 새_회차();
            lineup.record(읽음(List.of(축제("1", "진해 벚꽃 축제", "03-27", "04-05", 진해))), 봄_고르기(), 시작_전);

            assertThat(lineup.lastAttempt().outcome()).isEqualTo(CollectionAttempt.Outcome.PARTIAL);
            assertThat(lineup.alerts()).anyMatch(warning -> warning.contains("AI 추정 목록으로 채웠습니다"));
        }
    }

    @Nested
    @DisplayName("확정하면")
    class Confirm {

        @Test
        @DisplayName("후보가 그 회차에 쓰는 목록이 되고 근거를 함께 든다")
        void becomesInEffect() {
            SeasonLineup lineup = 열곳을_모은_회차(시작_전);
            lineup.confirm(ConfirmedBy.ADMIN, 시작_전);

            assertThat(lineup.inEffect(봄.regions()).provenance()).isEqualTo("tourapi");
            assertThat(lineup.inEffect(봄.regions()).codes()).contains(LineupFixtures.지역("KR-32010"));
            assertThat(lineup.confirmedBy()).isEqualTo(ConfirmedBy.ADMIN);
            assertThat(lineup.candidate()).isNull();
        }

        @Test
        @DisplayName("후보가 없으면 확정할 수 없다")
        void needsCandidate() {
            assertThatThrownBy(() -> 새_회차().confirm(ConfirmedBy.ADMIN, 시작_전))
                .isInstanceOfSatisfying(TerritoryException.class, refused -> assertThat(refused.code()).isEqualTo("SEASON_CANDIDATE_MISSING"));
        }
    }

    @Nested
    @DisplayName("회차가 열리면")
    class Locked {

        @Test
        @DisplayName("다시 모을 수 없다 — 갱신은 다음 회차부터")
        void noRecollect() {
            SeasonLineup lineup = 새_회차();

            assertThatThrownBy(() -> lineup.record(읽음(벚꽃_열두_지역()), 봄_고르기(), 시작_순간))
                .isInstanceOfSatisfying(TerritoryException.class, refused -> assertThat(refused.code()).isEqualTo("SEASON_ROUND_LOCKED"));
        }

        @Test
        @DisplayName("남은 후보도 확정할 수 없고 쓰던 목록이 그대로 간다")
        void noConfirm() {
            SeasonLineup lineup = 열곳을_모은_회차(시작_전);

            assertThatThrownBy(() -> lineup.confirm(ConfirmedBy.ADMIN, 시작_순간)).isInstanceOf(TerritoryException.class);
            assertThat(lineup.inEffect(봄.regions()).codes()).containsExactlyElementsOf(봄.regions());
            assertThat(lineup.lockedAt(시작_순간)).isTrue();
            assertThat(lineup.lockedAt(시작_순간.minusSeconds(1))).isFalse();
        }
    }

    @Nested
    @DisplayName("확정 없이 회차가 열리면")
    class Snapshot {

        @Test
        @DisplayName("그때 쓰던 기본 목록(AI 추정)이 확정본으로 고정되고, 남은 후보는 버린다")
        void freezesDefaults() {
            SeasonLineup lineup = 열곳을_모은_회차(시작_전);

            assertThat(lineup.awaitingSnapshotAt(시작_순간)).isTrue();
            assertThat(lineup.freezeOpened(봄.regions(), 시작_순간)).isTrue();
            assertThat(lineup.confirmedBy()).isEqualTo(ConfirmedBy.OPENING);
            assertThat(lineup.inEffect(봄.regions()).codes()).containsExactlyElementsOf(봄.regions());
            assertThat(lineup.candidate()).isNull();
        }

        @Test
        @DisplayName("고정한 뒤에는 계절 정의의 기본 목록이 바뀌어도 그 회차는 그대로다")
        void survivesDefinitionChange() {
            SeasonLineup lineup = 새_회차();
            lineup.freezeOpened(봄.regions(), 시작_순간);

            List<RegionCode> 바뀐_기본 = List.of(LineupFixtures.지역("KR-11010"));
            assertThat(lineup.inEffect(바뀐_기본).codes()).containsExactlyElementsOf(봄.regions());
            assertThat(lineup.freezeOpened(바뀐_기본, 시작_순간.plusSeconds(60))).isFalse();
        }

        @Test
        @DisplayName("이미 확정된 회차와 아직 열리지 않은 회차는 고정하지 않는다")
        void onlyUnconfirmedOpened() {
            SeasonLineup confirmed = 열곳을_모은_회차(시작_전);
            confirmed.confirm(ConfirmedBy.ADMIN, 시작_전);

            assertThat(confirmed.freezeOpened(봄.regions(), 시작_순간)).isFalse();
            assertThat(confirmed.confirmedBy()).isEqualTo(ConfirmedBy.ADMIN);
            assertThat(새_회차().freezeOpened(봄.regions(), 시작_전)).isFalse();
            assertThat(새_회차().awaitingSnapshotAt(시작_전)).isFalse();
        }
    }

    @Nested
    @DisplayName("모으기에 실패하면")
    class Failure {

        @Test
        @DisplayName("확정된 목록과 후보는 그대로 두고 까닭을 경고로 남긴다")
        void keepsCurrent() {
            SeasonLineup lineup = 열곳을_모은_회차(시작_전);
            lineup.confirm(ConfirmedBy.AUTO, 시작_전);
            LineupRegions before = lineup.inEffect(봄.regions());

            lineup.record(키_거절, 봄_고르기(), 시작_전.plusSeconds(60));

            assertThat(lineup.inEffect(봄.regions())).isEqualTo(before);
            assertThat(lineup.lastAttempt().failed()).isTrue();
            assertThat(lineup.lastAttempt().outcome()).isEqualTo(CollectionAttempt.Outcome.KEY_REJECTED);
            assertThat(lineup.alerts()).anyMatch(warning -> warning.contains("서비스 키를 거절"))
                .anyMatch(warning -> warning.contains("지금 확정된 목록을 그대로"));
        }

        @Test
        @DisplayName("확정된 목록이 없으면 AI 추정 목록을 그대로 쓴다고 알린다")
        void keepsDefaults() {
            SeasonLineup lineup = 새_회차();
            lineup.record(new FestivalFetch.Failed(FetchFailure.QUOTA_EXCEEDED, ""), 봄_고르기(), 시작_전);

            assertThat(lineup.alerts()).anyMatch(warning -> warning.contains("AI 추정 목록을 그대로"));
            assertThat(lineup.candidate()).isNull();
        }
    }

    @Nested
    @DisplayName("자동 수집은")
    class Automatic {

        private final CollectionSchedule 자동 = 서른날전부터_7일마다_자동확정();

        @Test
        @DisplayName("수집 기간 전에는 한 번도 모은 적 없을 때만 미리보기를 모은다")
        void previewBeforeWindow() {
            Instant 가을 = 그날_정오(2026, 10, 5);

            assertThat(새_회차().planAt(가을, 자동)).isEqualTo(CollectionPlan.PREVIEW);
            assertThat(열곳을_모은_회차(가을).planAt(가을.plus(Duration.ofDays(30)), 자동)).isEqualTo(CollectionPlan.NONE);
        }

        @Test
        @DisplayName("시작 30일 전부터는 모으고 확정한다 — 미리보기를 이미 모았어도")
        void collectInWindow() {
            Instant 수집기간 = 그날_정오(2027, 2, 25);

            assertThat(열곳을_모은_회차(그날_정오(2026, 10, 5)).planAt(수집기간, 자동)).isEqualTo(CollectionPlan.COLLECT_AND_CONFIRM);
        }

        @Test
        @DisplayName("수집 기간 안에서 최근에 모았으면 7일이 지나야 다시 모은다")
        void recollectInterval() {
            SeasonLineup lineup = 열곳을_모은_회차(그날_정오(2027, 2, 25));

            assertThat(lineup.planAt(그날_정오(2027, 3, 1), 자동)).isEqualTo(CollectionPlan.NONE);
            assertThat(lineup.planAt(그날_정오(2027, 3, 4), 자동)).isEqualTo(CollectionPlan.COLLECT_AND_CONFIRM);
        }

        @Test
        @DisplayName("자동 확정이 꺼져 있으면 모으기만 한다")
        void collectOnly() {
            CollectionSchedule 수동 = new CollectionSchedule(30, Duration.ofDays(7), false, 10, LineupFixtures.서울);

            assertThat(새_회차().planAt(그날_정오(2027, 2, 25), 수동)).isEqualTo(CollectionPlan.COLLECT);
        }

        @Test
        @DisplayName("관리자가 확정한 회차와 이미 열린 회차는 손대지 않는다")
        void leavesAlone() {
            SeasonLineup confirmedByAdmin = 열곳을_모은_회차(시작_전);
            confirmedByAdmin.confirm(ConfirmedBy.ADMIN, 시작_전);

            assertThat(confirmedByAdmin.planAt(그날_정오(2027, 3, 10), 자동)).isEqualTo(CollectionPlan.NONE);
            assertThat(새_회차().planAt(시작_순간, 자동)).isEqualTo(CollectionPlan.NONE);
        }

        @Test
        @DisplayName("근거 지역이 열 곳이면 자동 확정한다")
        void autoConfirmsFull() {
            SeasonLineup lineup = 열곳을_모은_회차(시작_전);

            assertThat(lineup.confirmAutomatically(CollectionPlan.COLLECT_AND_CONFIRM, 자동, 시작_전)).isTrue();
            assertThat(lineup.confirmedBy()).isEqualTo(ConfirmedBy.AUTO);
        }

        @Test
        @DisplayName("근거 지역이 모자라면 확정하지 않고 지금 목록을 지킨다(관리자는 확정할 수 있다)")
        void keepsWhenShort() {
            SeasonLineup lineup = 새_회차();
            lineup.record(읽음(List.of(축제("1", "진해 벚꽃 축제", "03-27", "04-05", 진해))), 봄_고르기(), 시작_전);

            assertThat(lineup.confirmAutomatically(CollectionPlan.COLLECT_AND_CONFIRM, 자동, 시작_전)).isFalse();
            assertThat(lineup.inEffect(봄.regions()).provenance()).isEqualTo("ai-estimate");
            lineup.confirm(ConfirmedBy.ADMIN, 시작_전);
            assertThat(lineup.inEffect(봄.regions()).provenance()).isEqualTo("mixed");
        }

        @Test
        @DisplayName("같은 날 한 번 모았으면(실패했어도) 그날은 다시 모으지 않는다")
        void oncePerDay() {
            Instant 아침 = 그날_정오(2027, 2, 25).minus(Duration.ofHours(3));
            SeasonLineup lineup = 새_회차();
            lineup.record(키_거절, 봄_고르기(), 아침);

            assertThat(lineup.planAt(아침.plus(Duration.ofHours(6)), 자동)).isEqualTo(CollectionPlan.NONE);
            assertThat(lineup.planAt(아침.plus(Duration.ofDays(1)), 자동)).isEqualTo(CollectionPlan.COLLECT_AND_CONFIRM);
        }

        @Test
        @DisplayName("어제 늦게 실패했으면 오늘 새벽 수집 때 다시 모은다(24시간을 기다리지 않는다)")
        void calendarDays() {
            Instant 어젯밤 = LineupFixtures.그날_정오(2027, 2, 25).plus(Duration.ofHours(11));
            SeasonLineup lineup = 새_회차();
            lineup.record(키_거절, 봄_고르기(), 어젯밤);

            assertThat(lineup.planAt(LineupFixtures.그날_정오(2027, 2, 26).minus(Duration.ofHours(7)), 자동))
                .isEqualTo(CollectionPlan.COLLECT_AND_CONFIRM);
        }

        @Test
        @DisplayName("키가 없어 부르지 못한 시도는 세지 않는다 — 키를 넣으면 같은 날이라도 바로 모은다")
        void keyAddedSameDay() {
            Instant 가을 = 그날_정오(2026, 10, 5);
            SeasonLineup lineup = 새_회차();
            lineup.record(new FestivalFetch.Failed(FetchFailure.NOT_CONFIGURED, ""), 봄_고르기(), 가을);

            assertThat(lineup.planAt(가을.plus(Duration.ofHours(1)), 자동)).isEqualTo(CollectionPlan.PREVIEW);
        }

        @Test
        @DisplayName("미리보기로 모은 것은 확정하지 않는다")
        void previewNeverConfirms() {
            SeasonLineup lineup = 열곳을_모은_회차(그날_정오(2026, 10, 5));

            assertThat(lineup.confirmAutomatically(CollectionPlan.PREVIEW, 자동, 그날_정오(2026, 10, 5))).isFalse();
        }
    }
}
