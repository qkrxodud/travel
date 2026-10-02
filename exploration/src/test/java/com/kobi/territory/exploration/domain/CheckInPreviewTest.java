package com.kobi.territory.exploration.domain;

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
import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.exploration.domain.CheckInPreview.XpLine;
import com.kobi.territory.exploration.domain.CheckInPreview.XpSource;
import org.junit.jupiter.api.Test;

class CheckInPreviewTest {

    @Test
    void 빈_영토에서_일반_지역은_기본10_시도첫방문15_선점10() {
        var p = CheckInPreview.preview(Territory.empty(MAP), ME, JONGNO, REWARDS);
        assertThat(p.lines()).containsExactly(
            new XpLine(XpSource.REGION_BASE, 10),
            new XpLine(XpSource.PROVINCE_FIRST, 15),
            new XpLine(XpSource.FIRST_CLAIM, 10));
        assertThat(p.totalXp()).isEqualTo(35);
        assertThat(p.itemIds()).containsExactly("region:KR-11010");
        assertThat(p.facts().nth()).isEqualTo(1);
    }

    @Test
    void 희귀도별_기본_XP() {
        assertThat(CheckInPreview.preview(Territory.empty(MAP), ME, GAPYEONG, REWARDS).lines().get(0).amount()).isEqualTo(20);
        assertThat(CheckInPreview.preview(Territory.empty(MAP), ME, ULLEUNG, REWARDS).totalXp()).isEqualTo(50 + 15 + 10);
    }

    @Test
    void 같은_시도_두번째는_시도_보너스가_없다() {
        Territory t = Territory.empty(MAP);
        checkIn(t, ME, JONGNO, onboarding(NOON));
        var p = CheckInPreview.preview(t, ME, JUNG, REWARDS);
        assertThat(p.lines()).extracting(XpLine::source).containsExactly(XpSource.REGION_BASE, XpSource.FIRST_CLAIM);
        assertThat(p.totalXp()).isEqualTo(20);
        assertThat(p.facts().nth()).isEqualTo(2);
    }

    @Test
    void 다른_멤버가_선점한_지역은_선점_보너스가_없다() {
        Territory t = Territory.empty(MAP);
        checkIn(t, FRIEND, JONGNO, onboarding(NOON));
        var p = CheckInPreview.preview(t, ME, JONGNO, REWARDS);
        assertThat(p.facts().firstClaim()).isFalse();
        assertThat(p.totalXp()).isEqualTo(10 + 15);
    }

    @Test
    void 이미_방문한_지역은_보상이_없다() {
        Territory t = Territory.empty(MAP);
        checkIn(t, ME, JONGNO, onboarding(NOON));
        var p = CheckInPreview.preview(t, ME, JONGNO, REWARDS);
        assertThat(p.facts().alreadyVisited()).isTrue();
        assertThat(p.totalXp()).isZero();
        assertThat(p.lines()).isEmpty();
        assertThat(p.itemIds()).isEmpty();
    }

    @Test
    void 미리보기의_사실과_실제_체크인_결과가_같다() {
        Territory t = Territory.empty(MAP);
        checkIn(t, ME, JONGNO, onboarding(NOON));
        checkIn(t, FRIEND, GAPYEONG, onboarding(NOON));
        for (var region : new RegionSnapshot[] {JUNG, GAPYEONG, ULLEUNG}) {
            var preview = CheckInPreview.preview(t, ME, region, REWARDS);
            var actual = checkIn(t, ME, region, onboarding(NOON));
            assertThat(actual.facts()).isEqualTo(preview.facts());
        }
    }

    @Test
    void 미리보기는_영토를_바꾸지_않는다() {
        Territory t = Territory.empty(MAP);
        CheckInPreview.preview(t, ME, JONGNO, REWARDS);
        assertThat(t.visits()).isEmpty();
    }
}
