package com.kobi.territory.exploration.domain;

import static com.kobi.territory.exploration.domain.Fixtures.FRIEND;
import static com.kobi.territory.exploration.domain.Fixtures.GAPYEONG;
import static com.kobi.territory.exploration.domain.Fixtures.JONGNO;
import static com.kobi.territory.exploration.domain.Fixtures.JUNG;
import static com.kobi.territory.exploration.domain.Fixtures.KST;
import static com.kobi.territory.exploration.domain.Fixtures.ME;
import static com.kobi.territory.exploration.domain.Fixtures.NOON;
import static com.kobi.territory.exploration.domain.Fixtures.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.common.model.ExplorerId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 일급 컬렉션 Visits — Spring 없음. */
class VisitsTest {

    static Visit visit(RegionSnapshot region, ExplorerId who, int daysAgoDate, Instant at) {
        return new Visit(region, who, VisitDate.of(TODAY.minusDays(daysAgoDate)), Memo.EMPTY, null, Verification.NONE, at);
    }

    @Test
    void 지역_멤버로_찾고_없으면_VISIT_NOT_FOUND() {
        Visits vs = Visits.of(List.of(visit(JONGNO, ME, 0, NOON)));
        assertThat(vs.contains(JONGNO.code(), ME)).isTrue();
        assertThat(vs.contains(JONGNO.code(), FRIEND)).isFalse();
        assertThatThrownBy(() -> vs.require(JUNG.code(), ME))
            .satisfies(exception -> assertThat(((ExplorationException) exception).error()).isEqualTo(ExplorationError.VISIT_NOT_FOUND));
    }

    @Test
    void 같은_지역_멤버_추가와_복원은_거부한다() {
        Visits vs = Visits.of(List.of(visit(JONGNO, ME, 0, NOON)));
        assertThatThrownBy(() -> vs.add(visit(JONGNO, ME, 1, NOON)))
            .satisfies(exception -> assertThat(((ExplorationException) exception).error()).isEqualTo(ExplorationError.DUPLICATE_VISIT));
        assertThatThrownBy(() -> Visits.of(List.of(visit(JONGNO, ME, 0, NOON), visit(JONGNO, ME, 2, NOON))))
            .isInstanceOf(IllegalStateException.class);
        vs.add(visit(JONGNO, FRIEND, 0, NOON)); // 다른 멤버는 같은 지역 가능
        assertThat(vs.size()).isEqualTo(2);
    }

    @Test
    void 멤버별_시도_접촉_지역_선점_칠해진_지역() {
        Visits vs = Visits.of(List.of(visit(JONGNO, FRIEND, 0, NOON), visit(JONGNO, ME, 0, NOON.plusSeconds(5)),
            visit(GAPYEONG, ME, 0, NOON)));
        assertThat(vs.of(ME).size()).isEqualTo(2);
        assertThat(vs.of(ME).touches("KR-31")).isTrue();
        assertThat(vs.of(FRIEND).touches("KR-31")).isFalse();
        assertThat(vs.anyIn(JONGNO.code())).isTrue();
        assertThat(vs.anyIn(JUNG.code())).isFalse();
        assertThat(vs.claimOf(JONGNO.code())).get().extracting(Visit::checkedInBy).isEqualTo(FRIEND);
        assertThat(vs.regions()).containsExactly(JONGNO, GAPYEONG);
    }

    @Test
    void 처리_날짜별_건수는_시간대_기준() {
        Instant lateUtc = Instant.parse("2026-10-02T15:30:00Z"); // KST 10-03 00:30
        Visits vs = Visits.of(List.of(visit(JONGNO, ME, 5, NOON), visit(JUNG, ME, 5, lateUtc)));
        assertThat(vs.processedOn(TODAY, KST)).isEqualTo(1);
        assertThat(vs.processedOn(TODAY.plusDays(1), KST)).isEqualTo(1);
        assertThat(vs.processedOn(TODAY, java.time.ZoneOffset.UTC)).isEqualTo(2);
    }

    @Test
    void 최근순은_방문일_내림차순_같으면_처리시각_내림차순() {
        Visit threeDaysAgo = visit(JONGNO, ME, 3, NOON);
        Visit todayNoon = visit(JUNG, ME, 0, NOON);
        Visit todayLater = visit(GAPYEONG, ME, 0, NOON.plus(Duration.ofMinutes(1)));
        assertThat(Visits.of(List.of(threeDaysAgo, todayNoon, todayLater)).recentFirst()).containsExactly(todayLater, todayNoon, threeDaysAgo);
    }
}
