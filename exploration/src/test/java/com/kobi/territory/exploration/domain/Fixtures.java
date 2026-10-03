package com.kobi.territory.exploration.domain;

import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.domain.map.CountryCode;
import com.kobi.territory.exploration.domain.map.ExpeditionMap;
import com.kobi.territory.exploration.domain.map.InviteCode;
import com.kobi.territory.exploration.domain.map.MapId;
import com.kobi.territory.exploration.domain.map.MapKind;
import com.kobi.territory.exploration.domain.map.MapSettings;
import com.kobi.territory.exploration.domain.map.MapVisibility;
import com.kobi.territory.exploration.domain.policy.CheckInPolicy;
import com.kobi.territory.exploration.domain.territory.CheckInContext;
import com.kobi.territory.exploration.domain.territory.CheckInPreview;
import com.kobi.territory.exploration.domain.territory.CheckInResult;
import com.kobi.territory.exploration.domain.territory.CheckInRewards;
import com.kobi.territory.exploration.domain.territory.Memo;
import com.kobi.territory.exploration.domain.territory.PhotoRef;
import com.kobi.territory.exploration.domain.territory.RegionSnapshot;
import com.kobi.territory.exploration.domain.territory.Territory;
import com.kobi.territory.exploration.domain.territory.VisitDate;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

/**
 * 탐험 단위 테스트의 등장인물·지역·시각과 준비 문장(Spring 없음).
 * <p>
 * 영토는 {@link #territory()}로 시작해 {@code .paint(나, 종로구)}처럼 "누가 어디를 칠했다"를 차례로 적는다. 칠한 순서가 곧 처리 시각
 * 순서(1분 간격)라 선점·회차가 적은 순서대로 정해진다.
 */
public final class Fixtures {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /** 2026-10-02 12:00 KST */
    public static final Instant NOON = Instant.parse("2026-10-02T03:00:00Z");
    public static final LocalDate TODAY = LocalDate.of(2026, 10, 2);
    /** 탈퇴 유예 7일 */
    public static final Duration GRACE = Duration.ofDays(7);
    /** 온보딩 예외 72시간 */
    public static final Duration ONBOARDING = Duration.ofHours(72);

    // ---- 탐험가 ----
    public static final ExplorerId ME = ExplorerId.of("11111111-1111-1111-1111-111111111111");
    public static final ExplorerId FRIEND = ExplorerId.of("22222222-2222-2222-2222-222222222222");
    public static final ExplorerId THIRD = ExplorerId.of("55555555-5555-5555-5555-555555555555");
    public static final ExplorerId FOURTH = ExplorerId.of("66666666-6666-6666-6666-666666666666");
    public static final ExplorerId FIFTH = ExplorerId.of("77777777-7777-7777-7777-777777777777");
    /** 구글 계정에 연결된 탐험가(익명 탐험가가 합쳐지는 쪽) */
    public static final ExplorerId ACCOUNT = ExplorerId.of("88888888-8888-8888-8888-888888888888");

    // ---- 지도 ----
    public static final MapId MAP = MapId.of("33333333-3333-3333-3333-333333333333");
    /** 익명 탐험가의 개인 지도 */
    public static final MapId ANON_MAP = MapId.of("44444444-4444-4444-4444-444444444444");
    public static final InviteCode CODE = new InviteCode("ABCDEFGH");

    // ---- 지역 ----
    public static final RegionSnapshot JONGNO = region("KR-11010", Rarity.COMMON, "KR-11");
    public static final RegionSnapshot JUNG = region("KR-11020", Rarity.COMMON, "KR-11");
    public static final RegionSnapshot GAPYEONG = region("KR-31370", Rarity.RARE, "KR-31");
    public static final RegionSnapshot ULLEUNG = region("KR-37430", Rarity.LEGEND, "KR-37");

    /** 보상 대역(카탈로그 보상 규칙과 같은 값: 기본 10/20/50, 시·도 첫 발 15, 선점 10). 실제 규칙은 catalog 테스트가 검증한다. */
    public static final CheckInRewards REWARDS = (rarity, firstInProvince, firstClaim) -> {
        List<CheckInPreview.XpLine> lines = new ArrayList<>();
        lines.add(new CheckInPreview.XpLine(CheckInPreview.XpSource.REGION_BASE,
            Map.of(Rarity.COMMON, 10, Rarity.RARE, 20, Rarity.LEGEND, 50).get(rarity)));
        if (firstInProvince) lines.add(new CheckInPreview.XpLine(CheckInPreview.XpSource.PROVINCE_FIRST, 15));
        if (firstClaim) lines.add(new CheckInPreview.XpLine(CheckInPreview.XpSource.FIRST_CLAIM, 10));
        return lines;
    };

    /** 기본 규칙: 하루 5곳, 온보딩 72시간, 사진 선택 */
    public static final CheckInPolicy POLICY = new CheckInPolicy(5, ONBOARDING, false);
    /** 사진 필수 지도의 규칙 */
    public static final CheckInPolicy PHOTO_REQUIRED = new CheckInPolicy(5, ONBOARDING, true);

    private Fixtures() {}

    public static RegionSnapshot region(String code, Rarity rarity, String province) {
        return new RegionSnapshot(RegionCode.of(code), rarity, province);
    }

    /** 행정구역 개편으로 없어진 지역. */
    public static RegionSnapshot retired(RegionSnapshot region) {
        return new RegionSnapshot(region.code(), region.rarity(), region.provinceCode(), true);
    }

    /** 서울의 n번째 가짜 일반 지역(KR-111nn). */
    public static RegionSnapshot seoul(int ordinal) {
        return region(String.format("KR-11%03d", 100 + ordinal), Rarity.COMMON, "KR-11");
    }

    /** 정오에서 n분 뒤. */
    public static Instant minutes(int n) {
        return NOON.plusSeconds(60L * n);
    }

    /** 정오에서 n시간 뒤. */
    public static Instant hours(int n) {
        return NOON.plus(Duration.ofHours(n));
    }

    // ---- 체크인 상황 ----

    /** 가입 직후(온보딩 중) — 하루 상한이 없다. */
    public static CheckInContext onboarding(Instant now) {
        return new CheckInContext(POLICY, now.minus(Duration.ofHours(1)), now, KST);
    }

    /** 가입 후 73시간이 지남 — 하루 상한이 적용된다. */
    public static CheckInContext veteran(Instant now) {
        return new CheckInContext(POLICY, now.minus(Duration.ofHours(73)), now, KST);
    }

    /** 사진 필수 지도에서 가입 직후. */
    public static CheckInContext photoRequired(Instant now) {
        return new CheckInContext(PHOTO_REQUIRED, now.minus(Duration.ofHours(1)), now, KST);
    }

    /** 가입 시각을 정해 둔 상황. */
    public static CheckInContext joinedAt(Instant joined, Instant now) {
        return new CheckInContext(POLICY, joined, now, KST);
    }

    /** 오늘 날짜·메모 없음·사진 없음으로 칠한다. */
    public static CheckInResult checkIn(Territory territory, ExplorerId who, RegionSnapshot region, CheckInContext ctx) {
        return territory.checkIn(who, region, VisitDate.of(ctx.today()), Memo.EMPTY, null, ctx);
    }

    // ---- 영토 이야기 ----

    /** 빈 공유 영토에서 시작한다. */
    public static TerritoryStory territory() {
        return new TerritoryStory(Territory.empty(MAP));
    }

    /** 빈 영토(지도 지정). */
    public static TerritoryStory territory(MapId mapId) {
        return new TerritoryStory(Territory.empty(mapId));
    }

    /** "누가 어디를 칠했다"를 차례로 적는 준비 문장. 한 번 칠할 때마다 처리 시각이 1분씩 흐른다(모두 온보딩 중). */
    public static final class TerritoryStory {
        private final Territory territory;
        private int minute;

        private TerritoryStory(Territory territory) {
            this.territory = territory;
        }

        public TerritoryStory paint(ExplorerId who, RegionSnapshot... regions) {
            for (RegionSnapshot region : regions) checkIn(territory, who, region, onboarding(minutes(minute++)));
            return this;
        }

        /** 방문일과 메모를 정해 칠한다. 메모가 있으면 같은 이름의 사진도 붙인다. */
        public TerritoryStory paint(ExplorerId who, RegionSnapshot region, LocalDate visitDate, String memo) {
            territory.checkIn(who, region, VisitDate.of(visitDate), Memo.of(memo),
                PhotoRef.ofNullable(memo.isEmpty() ? null : "https://photo.example/" + memo), onboarding(minutes(minute++)));
            return this;
        }

        /** 다음 체크인의 처리 시각(정오 기준 분). */
        public Instant nextMoment() {
            return minutes(minute);
        }

        public Territory build() {
            return territory;
        }
    }

    // ---- 탐험 지도 ----

    /** 내가 지도장인 공유 지도(기본 설정). */
    public static ExpeditionMap sharedMap() {
        return ExpeditionMap.create(MAP, ME, "부산 원정대", CountryCode.KR, CODE, MapKind.SHARED, MapSettings.defaults(5), NOON);
    }

    /** 내가 지도장인 공유 지도(공개 범위 지정). */
    public static ExpeditionMap sharedMap(MapVisibility visibility) {
        return ExpeditionMap.create(MAP, ME, "부산 원정대", CountryCode.KR, CODE, MapKind.SHARED,
            new MapSettings(false, 5, visibility), NOON);
    }

    /** 나 혼자인 개인 지도(기본 설정). */
    public static ExpeditionMap personalMap() {
        return ExpeditionMap.create(MAP, ME, "나의 영토", CountryCode.KR, CODE, MapKind.PERSONAL, MapSettings.defaults(5), NOON);
    }

    /** 거절된 이유. 거절되지 않으면 실패한다. */
    public static ExplorationError refusal(ThrowingCallable action) {
        ExplorationException refused = catchThrowableOfType(ExplorationException.class, action);
        if (refused == null) throw new AssertionError("거절되어야 한다");
        return refused.error();
    }
}
