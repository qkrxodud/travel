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

    /**
     * 보상 포트 대역(카탈로그 RewardRules 와 같은 값: 기본 10/20/50, 시·도 첫 발 15, 선점 10). 실제 함수는 catalog 테스트가 검증한다.
     */
    static final CheckInRewards REWARDS = (rarity, firstInProvince, firstClaim) -> {
        java.util.List<CheckInPreview.XpLine> lines = new java.util.ArrayList<>();
        lines.add(new CheckInPreview.XpLine(CheckInPreview.XpSource.REGION_BASE,
            Map.of(Rarity.COMMON, 10, Rarity.RARE, 20, Rarity.LEGEND, 50).get(rarity)));
        if (firstInProvince) lines.add(new CheckInPreview.XpLine(CheckInPreview.XpSource.PROVINCE_FIRST, 15));
        if (firstClaim) lines.add(new CheckInPreview.XpLine(CheckInPreview.XpSource.FIRST_CLAIM, 10));
        return lines;
    };

    static final CheckInPolicy POLICY = new CheckInPolicy(5, Duration.ofHours(72), false);

    private Fixtures() {}

    static RegionSnapshot region(String code, Rarity rarity, String province) {
        return new RegionSnapshot(RegionCode.of(code), rarity, province);
    }

    /** 서울 25개 구 중 n번째(KR-110n0) 같은 가짜 일반 지역. */
    static RegionSnapshot seoul(int ordinal) {
        return region(String.format("KR-11%03d", 100 + ordinal), Rarity.COMMON, "KR-11");
    }

    /** 가입 직후(온보딩 중) 컨텍스트. */
    static CheckInContext onboarding(Instant now) {
        return new CheckInContext(POLICY, now.minus(Duration.ofHours(1)), now, KST);
    }

    /** 가입 후 73시간 지난(온보딩 종료) 컨텍스트. */
    static CheckInContext veteran(Instant now) {
        return new CheckInContext(POLICY, now.minus(Duration.ofHours(73)), now, KST);
    }

    static CheckInResult checkIn(Territory territory, ExplorerId who, RegionSnapshot region, CheckInContext ctx) {
        return territory.checkIn(who, region, VisitDate.of(ctx.today()), Memo.EMPTY, null, ctx);
    }
}
