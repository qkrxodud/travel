package com.kobi.territory.exploration.domain;

import static com.kobi.territory.exploration.domain.Fixtures.JONGNO;
import static com.kobi.territory.exploration.domain.Fixtures.MAP;
import static com.kobi.territory.exploration.domain.Fixtures.ME;
import static com.kobi.territory.exploration.domain.Fixtures.NOON;
import static com.kobi.territory.exploration.domain.Fixtures.TODAY;
import static com.kobi.territory.exploration.domain.Fixtures.onboarding;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** VisitPatch(부분 수정 의미)·MapSelector(mapId 생략 = 개인 지도) — Spring 없음. */
class VisitPatchAndSelectorTest {

    @Test
    void 생략한_항목은_유지하고_빈_문자열은_지운다() {
        Territory t = Territory.empty(MAP);
        t.checkIn(ME, JONGNO, VisitDate.of(TODAY.minusDays(9)), Memo.of("원래"), new PhotoRef("https://p/1.jpg"), onboarding(NOON));

        Visit v = t.editVisit(ME, JONGNO.code(), VisitPatch.of(null, "바뀜", null), onboarding(NOON));
        assertThat(v.visitDate().value()).isEqualTo(TODAY.minusDays(9));
        assertThat(v.memo().value()).isEqualTo("바뀜");
        assertThat(v.photo().url()).isEqualTo("https://p/1.jpg");

        v = t.editVisit(ME, JONGNO.code(), VisitPatch.of(TODAY, "", ""), onboarding(NOON));
        assertThat(v.visitDate().value()).isEqualTo(TODAY);
        assertThat(v.memo().isEmpty()).isTrue();
        assertThat(v.photo()).isNull();
    }

    @Test
    void mapId_생략은_개인_지도() {
        assertThat(MapSelector.of((String) null).personal()).isTrue();
        assertThat(MapSelector.of(" ")).isEqualTo(MapSelector.PERSONAL);
        MapSelector s = MapSelector.of(MAP.value());
        assertThat(s.personal()).isFalse();
        assertThat(s.explicit()).contains(MAP);
        assertThat(s.notFound().error()).isEqualTo(ExplorationError.MAP_NOT_FOUND);
        assertThat(MapSelector.PERSONAL.notFound().getMessage()).contains("개인 지도");
    }

    @Test
    void 정복률은_Territory가_칠해진_지역으로_계산한다() {
        Territory t = Territory.empty(MAP);
        t.checkIn(ME, JONGNO, VisitDate.of(TODAY), Memo.EMPTY, null, onboarding(NOON));
        var rate = t.conquest(new java.util.LinkedHashMap<>(java.util.Map.of("KR-11", 25)));
        assertThat(rate.visited()).isEqualTo(1);
        assertThat(rate.provinces().get(0).visited()).isEqualTo(1);
    }
}
