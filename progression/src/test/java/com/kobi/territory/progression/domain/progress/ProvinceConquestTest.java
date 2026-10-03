package com.kobi.territory.progression.domain.progress;

import static com.kobi.territory.progression.domain.Fixtures.가평군;
import static com.kobi.territory.progression.domain.Fixtures.다른지도;
import static com.kobi.territory.progression.domain.Fixtures.방문;
import static com.kobi.territory.progression.domain.Fixtures.새_진행;
import static com.kobi.territory.progression.domain.Fixtures.수원시;
import static com.kobi.territory.progression.domain.Fixtures.옛서울구;
import static com.kobi.territory.progression.domain.Fixtures.용산구;
import static com.kobi.territory.progression.domain.Fixtures.종로구;
import static com.kobi.territory.progression.domain.Fixtures.중구;
import static com.kobi.territory.progression.domain.Fixtures.진행규칙;
import static com.kobi.territory.progression.domain.Fixtures.초;
import static com.kobi.territory.progression.domain.Fixtures.취소한다;
import static com.kobi.territory.progression.domain.Fixtures.칠한다;
import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.progression.domain.policy.ProgressionPolicy;
import com.kobi.territory.progression.domain.policy.ProvinceRoster;
import com.kobi.territory.progression.domain.policy.XpSource;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 시·도 정복(8단계): 탐험가 단위로(모든 지도 합쳐) 한 시·도의 현행 지역을 모두 칠하면 한 번 — 300 XP. 취소해도 회수 없고, 다시 100%가 돼도
 * 다시 받지 않는다. 폐지된 지역은 세지 않는다. 미니 명부: 서울 = 종로구·중구·용산구(옛서울구는 폐지), 경기 = 가평군·수원시.
 */
@DisplayName("시·도 정복")
class ProvinceConquestTest {

    /** 서울 세 곳을 모두 칠한 탐험가. */
    private static ExplorerProgress 서울을_다_칠한() {
        ExplorerProgress progress = 새_진행();
        칠한다(progress, 방문(종로구));
        칠한다(progress, 방문(중구).처리시각(초(1)));
        칠한다(progress, 방문(용산구).처리시각(초(2)));
        return progress;
    }

    @Nested
    @DisplayName("한 시·도의 현행 지역을 모두 칠하면")
    class Conquer {

        @Test
        @DisplayName("정복 보상 300 XP를 받는다")
        void reward() {
            ExplorerProgress progress = 새_진행();
            칠한다(progress, 방문(종로구));
            칠한다(progress, 방문(중구).처리시각(초(1)));

            ProgressChange change = 칠한다(progress, 방문(용산구).처리시각(초(2)));

            assertThat(change.xpDelta()).isEqualTo(10 + 10 + 300);
            assertThat(change.provincesConquered()).containsExactly("KR-11");
        }

        @Test
        @DisplayName("여러 지도에 나눠 칠해도 탐험가 기준으로 정복한다")
        void acrossMaps() {
            ExplorerProgress progress = 새_진행();
            칠한다(progress, 방문(가평군));

            ProgressChange change = 칠한다(progress, 방문(수원시).지도(다른지도).처리시각(초(1)));

            assertThat(change.provincesConquered()).containsExactly("KR-31");
        }

        @Test
        @DisplayName("한 곳이라도 남으면 아직 정복이 아니다")
        void notYet() {
            ExplorerProgress progress = 새_진행();
            칠한다(progress, 방문(종로구));

            ProgressChange change = 칠한다(progress, 방문(중구).처리시각(초(1)));

            assertThat(change.provincesConquered()).isEmpty();
            assertThat(progress.ledger().entriesOf(XpSource.PROVINCE_CONQUEST)).isEmpty();
        }

        @Test
        @DisplayName("그 시·도의 '주인' 칭호와 시·도 뱃지를 함께 얻는다")
        void titleAndBadge() {
            ExplorerProgress progress = 서울을_다_칠한();

            assertThat(progress.titles()).containsKey("own-KR-11");
            assertThat(progress.badges()).containsKey("seoul");
        }
    }

    @Nested
    @DisplayName("폐지된 지역은")
    class RetiredRegions {

        @Test
        @DisplayName("칠하지 않아도 정복을 막지 않는다")
        void notRequired() {
            assertThat(서울을_다_칠한().provincesConquered()).containsOnlyKeys("KR-11");
        }

        @Test
        @DisplayName("칠했어도 현행 지역이 남으면 정복으로 치지 않는다")
        void notCounted() {
            ExplorerProgress progress = 새_진행();
            칠한다(progress, 방문(옛서울구));
            칠한다(progress, 방문(종로구).처리시각(초(1)));

            ProgressChange change = 칠한다(progress, 방문(중구).처리시각(초(2)));

            assertThat(change.provincesConquered()).isEmpty();
            assertThat(progress.titles()).doesNotContainKey("own-KR-11");
        }
    }

    @Nested
    @DisplayName("한 번 정복하면")
    class AfterConquest {

        @Test
        @DisplayName("칠한 곳을 취소해도 정복 보상은 되돌리지 않는다")
        void notRevoked() {
            ExplorerProgress progress = 서울을_다_칠한();

            취소한다(progress, 방문(용산구).처리시각(초(3)));

            assertThat(progress.ledger().has(RefIds.conquest(progress.explorerId(), "KR-11"))).isTrue();
            assertThat(progress.provincesConquered()).containsOnlyKeys("KR-11");
        }

        @Test
        @DisplayName("취소했다가 다시 다 칠해도 다시 받지 않는다")
        void onlyOnce() {
            ExplorerProgress progress = 서울을_다_칠한();
            취소한다(progress, 방문(용산구).처리시각(초(3)));

            ProgressChange again = 칠한다(progress, 방문(용산구).처리시각(초(4)).회차(2));

            assertThat(again.provincesConquered()).isEmpty();
            assertThat(progress.ledger().entriesOf(XpSource.PROVINCE_CONQUEST)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("시·도별 현황은")
    class Coverage {

        @Test
        @DisplayName("현행 지역 기준 칠한 수·전체·정복 시각을 시·도마다 보여 준다")
        void perProvince() {
            ExplorerProgress progress = 서울을_다_칠한();
            칠한다(progress, 방문(가평군).처리시각(초(5)));

            assertThat(progress.provinceCoverage(진행규칙))
                .extracting(coverage -> coverage.provinceCode() + ":" + coverage.covered() + "/" + coverage.total() + ":"
                    + coverage.percent() + ":" + coverage.conquered())
                .containsExactlyInAnyOrder("KR-11:3/3:100:true", "KR-31:1/2:50:false");
        }

        @Test
        @DisplayName("취소로 100%가 깨져도 정복 기록(왕관)은 남는다")
        void crownStays() {
            ExplorerProgress progress = 서울을_다_칠한();
            취소한다(progress, 방문(중구).처리시각(초(3)));

            ProvinceCoverage seoul = progress.provinceCoverage(진행규칙).stream()
                .filter(coverage -> coverage.provinceCode().equals("KR-11")).findFirst().orElseThrow();

            assertThat(seoul.complete()).isFalse();
            assertThat(seoul.conquered()).isTrue();
        }
    }

    @Test
    @DisplayName("정복 보상이 생기기 전에 이미 다 칠해 둔 시·도는 다음 체크인 때 정복 보상을 받는다")
    void alreadyCompleteBeforeRule() {
        ProgressionPolicy 정복규칙전 = new ProgressionPolicy(진행규칙.curve(), 진행규칙.rewards(), 진행규칙.badges(), 진행규칙.titleRules(),
            ProvinceRoster.of(Map.of()), 진행규칙.totalRegions(), 진행규칙.zone(), 진행규칙.streakRules(), 진행규칙.monthlyQuestIds());
        ExplorerProgress progress = 새_진행();
        progress.applyVisit(방문(종로구).사실(), 정복규칙전);
        progress.applyVisit(방문(중구).처리시각(초(1)).사실(), 정복규칙전);
        progress.applyVisit(방문(용산구).처리시각(초(2)).사실(), 정복규칙전);
        assertThat(progress.provincesConquered()).isEmpty();

        ProgressChange next = 칠한다(progress, 방문(가평군).처리시각(초(3)));

        assertThat(next.provincesConquered()).containsExactly("KR-11");
    }
}
