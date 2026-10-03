package com.kobi.territory.catalog.domain.item;

import com.kobi.territory.catalog.domain.CatalogError;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.common.model.RegionCode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 아이템 정의(참조 데이터, 3단계부터 DB item_definition — 운영이 POST /admin/items 로 추가한다).
 * 지역 아이템 itemId 는 {@code region:{regionCode}}, 세트 배경은 {@code set:{setId}}.
 * 상호·브랜드명은 일반명사화한 이름만 둔다(리스크 #9). 생성 시 운영 폼 입력 검증을 한다(어기면 INVALID_ITEM_DEFINITION).
 *
 * @param theme       배경(BG) 아이템의 풍경 테마, 그 외 null
 * @param look        엔진 룩(형태+색), 배경은 null
 * @param grantRule   지급 규칙
 * @param validPeriod 지급 유효 기간(상시면 {@link ValidPeriod#ALWAYS})
 * @param createdAt   정의가 생긴 시각(이관 데이터는 이관 시각). 이슈 아이템은 이보다 앞선 체크인에 소급 지급하지 않는다(리더 결정 Q-R2-1)
 */
public record ItemDefinition(
    String itemId,
    String name,
    String emoji,
    ItemSlot slot,
    Rarity tier,
    String theme,
    Look look,
    GrantRule grantRule,
    ValidPeriod validPeriod,
    Instant createdAt
) {
    private static final Pattern ITEM_ID = Pattern.compile("^[a-z][a-z0-9_-]{0,15}:[A-Za-z0-9_-]{1,40}$");
    private static final Pattern THEME = Pattern.compile("^[a-z][a-z0-9_-]{0,19}$");
    static final int NAME_MAX = 40;
    /** 이관 데이터 id 접두어 — V3_1 지역 특산물·세트 배경, V4_1 초대 보상, V6 시·도 정복(conquest:)·연속 탐험 마일스톤(streak:). */
    static final List<String> MIGRATED_PREFIXES = List.of("region:", "set:", "invite:", "conquest:", "streak:");
    static final int EMOJI_MAX = 16;

    public ItemDefinition {
        if (itemId == null || !ITEM_ID.matcher(itemId).matches()) {
            throw invalid("아이템 id 형식이 올바르지 않습니다(예: event:hanbok-2026): " + itemId);
        }
        if (name == null || name.isBlank() || name.length() > NAME_MAX) throw invalid("이름은 1~" + NAME_MAX + "자입니다.");
        if (emoji == null || emoji.isBlank() || emoji.length() > EMOJI_MAX) throw invalid("이모지는 1~" + EMOJI_MAX + "자입니다.");
        if (slot == null) throw invalid("슬롯이 필요합니다.");
        if (tier == null) throw invalid("희귀도가 필요합니다.");
        if (theme != null && !THEME.matcher(theme).matches()) throw invalid("배경 테마 형식이 올바르지 않습니다: " + theme);
        if (grantRule == null) throw invalid("지급 규칙이 필요합니다.");
        validPeriod = validPeriod == null ? ValidPeriod.ALWAYS : validPeriod;
        if (createdAt == null) throw invalid("정의 생성 시각이 필요합니다.");
        if (grantRule.type() == GrantRule.Type.PERIOD_CHECK_IN && !validPeriod.bounded()) {
            throw invalid("기간 내 체크인 아이템은 유효 기간의 시작·끝이 모두 필요합니다.");
        }
    }

    /** 지역 특산물 아이템 id. */
    public static String regionItemId(RegionCode code) {
        return "region:" + code.value();
    }

    /**
     * 이관 데이터(V3_1 — 지역 특산물 region:·세트 배경 set:, V4_1 — 초대 보상 invite:)의 id 인지. 운영 추가는 이 접두어를
     * 쓸 수 없다(dev 초기화가 운영 추가분만 골라 지우는 기준).
     */
    public boolean migrated() {
        return MIGRATED_PREFIXES.stream().anyMatch(itemId::startsWith);
    }

    /** 지역 방문 규칙이면 그 지역(표시용 출처), 아니면 null. */
    public RegionCode regionCode() {
        return grantRule instanceof GrantRule.RegionVisit regionVisit ? regionVisit.region() : null;
    }

    /**
     * 처리 시각 processedAt(그 날짜 day, 서버 시간대)의 체크인으로 지급되는지. 이슈 아이템(기간·시·도)은 정의가 생긴 뒤의
     * 체크인에만 — 소급 지급 없음(Q-R2-1, 재계산도 같은 기준).
     */
    public boolean grantedByCheckIn(RegionCode region, String provinceCode, LocalDate day, Instant processedAt) {
        return grantRule.matchesCheckIn(region, provinceCode) && validPeriod.contains(day)
            && (!grantRule.issue() || !processedAt.isBefore(createdAt));
    }

    /**
     * 완성 시각 completedAt(그 날짜 day)의 테마(세트) 완성으로 지급되는지. 운영이 추가한 THEME_COMPLETE 아이템은 정의가 생긴 뒤의
     * 완성에만 — 소급 지급 없음(P3-R3-3·Q1 결정: 이벤트 누적·합류 경로·재계산이 모두 같은 기준). 이관 데이터(세트 배경 set:)는 처음부터
     * 있던 보상이라 예외.
     */
    public boolean grantedByThemeCompletion(String themeId, LocalDate day, Instant completedAt) {
        return grantRule.matchesThemeCompletion(themeId) && validPeriod.contains(day)
            && (migrated() || !completedAt.isBefore(createdAt));
    }

    /**
     * 정복 시각 conqueredAt(그 날짜 day)의 시·도 정복으로 지급되는지(8단계). 운영이 나중에 추가한 정복 아이템은 정의가 생긴 뒤의 정복에만
     * (테마 완성과 같은 기준 — 소급 없음), 이관 데이터(conquest:)는 처음부터 있던 보상이라 예외.
     */
    public boolean grantedByProvinceConquest(String provinceCode, LocalDate day, Instant conqueredAt) {
        return grantRule.matchesProvinceConquest(provinceCode) && validPeriod.contains(day)
            && (migrated() || !conqueredAt.isBefore(createdAt));
    }

    /** 도달 시각 reachedAt(그 날짜 day)의 연속 탐험 마일스톤으로 지급되는지(8단계, 기준은 시·도 정복과 같다). */
    public boolean grantedByStreakMilestone(int months, LocalDate day, Instant reachedAt) {
        return grantRule.matchesStreakMilestone(months) && validPeriod.contains(day)
            && (migrated() || !reachedAt.isBefore(createdAt));
    }

    /** 이 날짜의 초대 합류에서 그 쪽(HOST·GUEST)이 받는지(한정 아이템 — 유효 기간으로 연다·닫는다). */
    public boolean grantedByInvitation(GrantRule.InvitationSide side, LocalDate day) {
        return grantRule.matchesInvitation(side) && validPeriod.contains(day);
    }

    private static RuntimeException invalid(String message) {
        return CatalogError.INVALID_ITEM_DEFINITION.exception(message);
    }

    /** 엔진 룩: 형태 + 주색·보조색(#rrggbb). */
    public record Look(String type, String primary, String secondary) {
        private static final Pattern TYPE = Pattern.compile("^[a-z]{1,20}$");
        private static final Pattern COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");

        public Look {
            if (type == null || !TYPE.matcher(type).matches()) throw invalid("룩 형태가 올바르지 않습니다: " + type);
            if (primary == null || !COLOR.matcher(primary).matches()) throw invalid("주색은 #rrggbb 형식입니다: " + primary);
            if (secondary == null || !COLOR.matcher(secondary).matches()) throw invalid("보조색은 #rrggbb 형식입니다: " + secondary);
        }
    }
}
