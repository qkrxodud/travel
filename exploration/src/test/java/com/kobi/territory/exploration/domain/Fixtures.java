package com.kobi.territory.exploration.domain;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.common.model.RegionCode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

/** 단위 테스트 공용 픽스처(Spring 없음). */
final class Fixtures {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /** 2026-10-02 12:00 KST */
    static final Instant NOON = Instant.parse("2026-10-02T03:00:00Z");
    static final LocalDate TODAY = LocalDate.of(2026, 10, 2);

    static final ExplorerId ME = ExplorerId.of("11111111-1111-1111-1111-111111111111");
    static final ExplorerId FRIEND = ExplorerId.of("22222222-2222-2222-2222-222222222222");
    static final MapId MAP = MapId.of("33333333-3333-3333-3333-333333333333");

    static final RegionSnapshot JONGNO = region("KR-11010", Rarity.COMMON, "KR-11");
    static final RegionSnapshot JUNG = region("KR-11020", Rarity.COMMON, "KR-11");
    static final RegionSnapshot GAPYEONG = region("KR-31370", Rarity.RARE, "KR-31");
    static final RegionSnapshot ULLEUNG = region("KR-37430", Rarity.LEGEND, "KR-37");

    static final RewardTable REWARDS = new RewardTable(
        Map.of(Rarity.COMMON, 10, Rarity.RARE, 20, Rarity.LEGEND, 50), 15, 10);

    static final CheckInPolicy POLICY = new CheckInPolicy(5, Duration.ofHours(72), false);

    private Fixtures() {}

    static RegionSnapshot region(String code, Rarity rarity, String province) {
        return new RegionSnapshot(RegionCode.of(code), rarity, province);
    }

    /** 서울 25개 구 중 n번째(KR-110n0) 같은 가짜 일반 지역. */
    static RegionSnapshot seoul(int i) {
        return region(String.format("KR-11%03d", 100 + i), Rarity.COMMON, "KR-11");
    }

    /** 가입 직후(온보딩 중) 컨텍스트. */
    static CheckInContext onboarding(Instant now) {
        return new CheckInContext(POLICY, now.minus(Duration.ofHours(1)), now, KST);
    }

    /** 가입 후 73시간 지난(온보딩 종료) 컨텍스트. */
    static CheckInContext veteran(Instant now) {
        return new CheckInContext(POLICY, now.minus(Duration.ofHours(73)), now, KST);
    }

    static CheckInResult checkIn(Territory t, ExplorerId who, RegionSnapshot r, CheckInContext ctx) {
        return t.checkIn(who, r, VisitDate.of(ctx.today()), Memo.EMPTY, null, ctx);
    }
}
