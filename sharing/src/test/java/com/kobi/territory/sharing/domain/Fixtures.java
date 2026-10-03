package com.kobi.territory.sharing.domain;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.sharing.domain.showcase.ProvinceInfo;
import com.kobi.territory.sharing.domain.showcase.PublicVisits;
import com.kobi.territory.sharing.domain.showcase.RegionAtlas;
import com.kobi.territory.sharing.domain.showcase.RegionInfo;
import com.kobi.territory.sharing.domain.showcase.Showcase;
import com.kobi.territory.sharing.domain.showcase.ShowcaseProgress;
import com.kobi.territory.sharing.domain.showcase.ShowcaseScene;
import com.kobi.territory.sharing.domain.showcase.VisitFact;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 공유 테스트의 등장인물·지도책·방문과 준비 문장(Spring 없음).
 * <p>
 * 지도책은 서울(종로·중구)과 경북(울릉·포항) 두 시·도 네 지역이다. 공개 요약은 {@code showcase("kim").level(3).painted("KR-11010")}처럼
 * "누가 몇 레벨이고 어디를 칠했다"를 적는다.
 */
public final class Fixtures {

    public static final ExplorerId OWNER = ExplorerId.of("aaaaaaaa-0000-0000-0000-00000000000a");
    public static final ExplorerId FRIEND = ExplorerId.of("bbbbbbbb-0000-0000-0000-00000000000b");
    public static final ExplorerId STRANGER = ExplorerId.of("cccccccc-0000-0000-0000-00000000000c");
    public static final ExplorerId UNSET = ExplorerId.of("dddddddd-0000-0000-0000-00000000000d");
    /** 개인 지도 */
    public static final String PERSONAL_MAP = "eeeeeeee-0000-0000-0000-00000000000e";
    public static final Instant T0 = Instant.parse("2026-10-03T03:00:00Z");

    public static final String JONGNO = "KR-11010";
    public static final String JUNG = "KR-11020";
    public static final String ULLEUNG = "KR-37430";
    public static final String POHANG = "KR-37010";

    public static final RegionAtlas ATLAS = RegionAtlas.of(List.of(
        new RegionInfo(JONGNO, "종로구", "KR-11", "서울", Rarity.COMMON),
        new RegionInfo(JUNG, "중구", "KR-11", "서울", Rarity.COMMON),
        new RegionInfo(ULLEUNG, "울릉군", "KR-37", "경북", Rarity.LEGEND),
        new RegionInfo(POHANG, "포항시", "KR-37", "경북", Rarity.COMMON)),
        List.of(new ProvinceInfo("KR-11", "서울", 2), new ProvinceInfo("KR-37", "경북", 2)));

    private Fixtures() {}

    /** order 번째로 칠한 방문(방문일 date). */
    public static VisitFact visit(String code, String date, int order) {
        return new VisitFact(code, LocalDate.parse(date), T0.plusSeconds(order));
    }

    /** 방문들을 지도책 기준 공개 방문으로. */
    public static PublicVisits visits(VisitFact... facts) {
        return PublicVisits.of(List.of(facts), ATLAS);
    }

    /** 공개 요약 준비 문장 — 기본은 Lv.1 "초보", 가방 1개, 칠한 곳 없음. handle 이 null 이면 익명 탐험가. */
    public static ShowcaseStory showcase(String handle) {
        return new ShowcaseStory(handle);
    }

    public static final class ShowcaseStory {
        private final String handle;
        private int level = 1;
        private String title = "초보";
        private int owned = 1;
        private final List<VisitFact> facts = new ArrayList<>();

        private ShowcaseStory(String handle) {
            this.handle = handle;
        }

        public ShowcaseStory level(int level) {
            this.level = level;
            return this;
        }

        public ShowcaseStory title(String title) {
            this.title = title;
            return this;
        }

        public ShowcaseStory owned(int owned) {
            this.owned = owned;
            return this;
        }

        /** 이 지역들을 2026-10-01 에 차례로 칠했다. */
        public Showcase painted(String... codes) {
            for (String code : codes) facts.add(visit(code, "2026-10-01", facts.size()));
            return build();
        }

        public Showcase build() {
            return new Showcase(handle, ATLAS, PublicVisits.of(facts, ATLAS), new ShowcaseProgress(level, title, 1, 0, 9),
                new ShowcaseScene(0, List.of(), owned));
        }
    }
}
