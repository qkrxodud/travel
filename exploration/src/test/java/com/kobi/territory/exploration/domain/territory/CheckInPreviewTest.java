package com.kobi.territory.exploration.domain.territory;

import static com.kobi.territory.exploration.domain.Fixtures.FRIEND;
import static com.kobi.territory.exploration.domain.Fixtures.GAPYEONG;
import static com.kobi.territory.exploration.domain.Fixtures.JONGNO;
import static com.kobi.territory.exploration.domain.Fixtures.JUNG;
import static com.kobi.territory.exploration.domain.Fixtures.MAP;
import static com.kobi.territory.exploration.domain.Fixtures.ME;
import static com.kobi.territory.exploration.domain.Fixtures.NOON;
import static com.kobi.territory.exploration.domain.Fixtures.REWARDS;
import static com.kobi.territory.exploration.domain.Fixtures.ULLEUNG;
import static com.kobi.territory.exploration.domain.Fixtures.checkIn;
import static com.kobi.territory.exploration.domain.Fixtures.onboarding;
import static com.kobi.territory.exploration.domain.Fixtures.territory;
import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.exploration.domain.territory.CheckInPreview.XpLine;
import com.kobi.territory.exploration.domain.territory.CheckInPreview.XpSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("체크인 미리보기")
class CheckInPreviewTest {

    static CheckInPreview.Result preview(Territory territory, RegionSnapshot region) {
        return CheckInPreview.preview(territory, ME, region, REWARDS);
    }

    @Nested
    @DisplayName("빈 영토에서 일반 지역을 고르면")
    class EmptyTerritory {

        @Test
        @DisplayName("기본 10, 시·도 첫 발 15, 선점 10 경험치를 보여준다")
        void allBonuses() {
            var preview = preview(Territory.empty(MAP), JONGNO);
            assertThat(preview.lines()).containsExactly(
                new XpLine(XpSource.REGION_BASE, 10),
                new XpLine(XpSource.PROVINCE_FIRST, 15),
                new XpLine(XpSource.FIRST_CLAIM, 10));
            assertThat(preview.totalXp()).isEqualTo(35);
        }

        @Test
        @DisplayName("그 지역 특산물을 받을 것이라고 보여준다")
        void regionItem() {
            assertThat(preview(Territory.empty(MAP), JONGNO).itemIds()).containsExactly("region:KR-11010");
        }

        @Test
        @DisplayName("첫 번째 영토가 된다고 보여준다")
        void firstNth() {
            assertThat(preview(Territory.empty(MAP), JONGNO).facts().nth()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("희귀 지역의 기본 경험치는 20이다")
    void rareBase() {
        assertThat(preview(Territory.empty(MAP), GAPYEONG).lines().get(0).amount()).isEqualTo(20);
    }

    @Test
    @DisplayName("전설 지역의 기본 경험치는 50이다")
    void legendBase() {
        assertThat(preview(Territory.empty(MAP), ULLEUNG).totalXp()).isEqualTo(50 + 15 + 10);
    }

    @Test
    @DisplayName("같은 시·도의 두 번째 지역에는 시·도 첫 발 보너스가 없다")
    void noProvinceBonus() {
        var preview = preview(territory().paint(ME, JONGNO).build(), JUNG);
        assertThat(preview.lines()).extracting(XpLine::source).containsExactly(XpSource.REGION_BASE, XpSource.FIRST_CLAIM);
        assertThat(preview.totalXp()).isEqualTo(20);
        assertThat(preview.facts().nth()).isEqualTo(2);
    }

    @Test
    @DisplayName("다른 멤버가 선점한 지역에는 선점 보너스가 없다")
    void noClaimBonus() {
        var preview = preview(territory().paint(FRIEND, JONGNO).build(), JONGNO);
        assertThat(preview.facts().firstClaim()).isFalse();
        assertThat(preview.totalXp()).isEqualTo(10 + 15);
    }

    @Nested
    @DisplayName("이미 칠한 지역을 고르면")
    class AlreadyPainted {

        @Test
        @DisplayName("받을 경험치가 없다")
        void noXp() {
            var preview = preview(territory().paint(ME, JONGNO).build(), JONGNO);
            assertThat(preview.facts().alreadyVisited()).isTrue();
            assertThat(preview.totalXp()).isZero();
            assertThat(preview.lines()).isEmpty();
        }

        @Test
        @DisplayName("받을 아이템도 없다")
        void noItem() {
            assertThat(preview(territory().paint(ME, JONGNO).build(), JONGNO).itemIds()).isEmpty();
        }
    }

    @Test
    @DisplayName("미리 본 사실 값은 실제로 칠했을 때와 같다")
    void matchesActual() {
        Territory territory = territory().paint(ME, JONGNO).paint(FRIEND, GAPYEONG).build();
        for (var region : new RegionSnapshot[] {JUNG, GAPYEONG, ULLEUNG}) {
            var preview = preview(territory, region);
            var actual = checkIn(territory, ME, region, onboarding(NOON));
            assertThat(actual.facts()).isEqualTo(preview.facts());
        }
    }

    @Test
    @DisplayName("미리보기는 영토를 바꾸지 않는다")
    void readOnly() {
        Territory territory = Territory.empty(MAP);
        preview(territory, JONGNO);
        assertThat(territory.visits()).isEmpty();
    }
}
