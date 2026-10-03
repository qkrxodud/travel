package com.kobi.territory.wardrobe.domain;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.wardrobe.domain.inventory.CheckInGrant;
import com.kobi.territory.wardrobe.domain.inventory.Inventory;
import com.kobi.territory.wardrobe.domain.item.GrantKind;
import com.kobi.territory.wardrobe.domain.item.ItemSlot;
import com.kobi.territory.wardrobe.domain.item.ItemSpec;
import com.kobi.territory.wardrobe.domain.scene.StylePolicy;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 꾸미기 테스트의 등장인물·지도·지역·아이템과 준비 문장(Spring 없음).
 * <p>
 * 가방은 {@link #bag()}로 시작하고, 체크인은 {@code checkIn(PERSONAL_MAP, JONGNO).round(2).at(4).giving(LANTERN)}처럼
 * "어느 지도에서 어디를 몇 번째로 칠했고 무엇을 받았다"를 적는다.
 */
public final class Fixtures {

    // ---- 탐험가 ----
    public static final ExplorerId ME = ExplorerId.of("11111111-1111-1111-1111-111111111111");
    public static final ExplorerId HOST = ExplorerId.of("22222222-2222-2222-2222-222222222222");
    public static final ExplorerId OTHER_HOST = ExplorerId.of("33333333-3333-3333-3333-333333333333");
    /** 구글 계정에 연결된 탐험가(익명 탐험가가 합쳐지는 쪽) */
    public static final ExplorerId ACCOUNT = ExplorerId.of("44444444-4444-4444-4444-444444444444");

    // ---- 지도 ----
    public static final String PERSONAL_MAP = "aaaaaaaa-0000-0000-0000-000000000001";
    public static final String SHARED_MAP = "bbbbbbbb-0000-0000-0000-000000000002";
    public static final String OTHER_MAP = "cccccccc-0000-0000-0000-000000000003";

    // ---- 지역 ----
    public static final RegionCode JONGNO = RegionCode.of("KR-11010");
    public static final RegionCode BUSAN = RegionCode.of("KR-21090");
    public static final RegionCode GURYE = RegionCode.of("KR-36330");

    // ---- 아이템 ----
    /** 종로 특산물(손, 일반) */
    public static final ItemSpec LANTERN = regionItem("KR-11010", ItemSlot.HAND, Rarity.COMMON);
    /** 부산 특산물(장식, 일반) */
    public static final ItemSpec PARASOL = regionItem("KR-21090", ItemSlot.PROP, Rarity.COMMON);
    /** 구례 특산물(모자, 희귀) */
    public static final ItemSpec BEANIE = regionItem("KR-36330", ItemSlot.HAT, Rarity.RARE);
    /** 기간 한정 이슈 아이템(모자, 희귀) */
    public static final ItemSpec HANBOK = eventItem("hanbok", ItemSlot.HAT, Rarity.RARE);
    /** 초대받은 쪽 보상 */
    public static final ItemSpec GUEST_TICKET = new ItemSpec("invite:guest-ticket", ItemSlot.BADGE, Rarity.RARE, GrantKind.INVITATION);
    /** 운영이 직접 준 아이템 */
    public static final ItemSpec GIFT = new ItemSpec("event:gift", ItemSlot.HAT, Rarity.RARE, GrantKind.MANUAL);

    /** 꾸미기 점수: 일반 1 · 희귀 3 · 전설 8 */
    public static final StylePolicy STYLE = new StylePolicy(Map.of(Rarity.COMMON, 1, Rarity.RARE, 3, Rarity.LEGEND, 8));

    /** 이야기의 시작 시각. */
    public static final Instant T0 = Instant.parse("2026-10-02T03:00:00Z");

    private Fixtures() {}

    /** 시작에서 n분 뒤. */
    public static Instant at(int minutes) {
        return T0.plusSeconds(60L * minutes);
    }

    /** 빈 내 가방. */
    public static Inventory bag() {
        return Inventory.empty(ME, T0);
    }

    public static ItemSpec regionItem(String code, ItemSlot slot, Rarity tier) {
        return new ItemSpec("region:" + code, slot, tier, GrantKind.REGION_VISIT);
    }

    /** 테마(세트) 완성 배경(전설). */
    public static ItemSpec themeBackground(String themeId) {
        return new ItemSpec("set:" + themeId, ItemSlot.BG, Rarity.LEGEND, GrantKind.THEME_COMPLETE);
    }

    /** 기간 내 체크인으로 받는 이슈 아이템. */
    public static ItemSpec eventItem(String id, ItemSlot slot, Rarity tier) {
        return new ItemSpec("event:" + id, slot, tier, GrantKind.PERIOD_CHECK_IN);
    }

    /** 체크인 한 건 — 기본은 1회차, 시작 시각, 받는 아이템 없음. */
    public static CheckInStory checkIn(String mapId, RegionCode region) {
        return new CheckInStory(mapId, region);
    }

    /** "어느 지도에서 어디를 몇 번째로 칠했고 무엇을 받았다" 준비 문장. */
    public static final class CheckInStory {
        private final String mapId;
        private final RegionCode region;
        private int round = 1;
        private int minute;

        private CheckInStory(String mapId, RegionCode region) {
            this.mapId = mapId;
            this.region = region;
        }

        /** 같은 (지도, 지역)의 체크인 회차(0 = 회차를 모르는 예전 소식). */
        public CheckInStory round(int round) {
            this.round = round;
            return this;
        }

        public CheckInStory at(int minute) {
            this.minute = minute;
            return this;
        }

        public CheckInGrant giving(ItemSpec... items) {
            return new CheckInGrant(mapId, region, round, List.of(items), Fixtures.at(minute));
        }
    }
}
