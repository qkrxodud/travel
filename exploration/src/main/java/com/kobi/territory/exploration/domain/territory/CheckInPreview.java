package com.kobi.territory.exploration.domain.territory;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import java.util.List;
import java.util.Optional;

/**
 * 체크인 미리보기 도메인 서비스 — 순수 함수(현재 영토 + 지역 → 받을 보상).
 * 체크인 모달의 "획득 XP +N"을 계산한다. 사실 값(VisitFacts)은 Territory가, XP 줄은 {@link CheckInRewards}
 * (카탈로그 보상 함수 — 진행이 실제 지급에 쓰는 것과 같은 함수, D1)가 만든다.
 * <p>
 * 의미: "이 지도 기준 최대 보상". 기본 XP는 탐험가당 지역당 1회, 시·도 보너스는 탐험가당 1회이므로 다른 지도에서
 * 이미 받았거나 취소 후 다시 칠하는 경우 실제 지급은 이보다 적을 수 있다. 세트 완성·퀘스트 보상은 포함하지 않는다.
 * 8단계: 고른 지역이 이번 주 미스터리 지역이면 미스터리 보너스 줄을 더한다(주마다 한 번이라 이번 주에 이미 받았으면 실제 지급은 없다).
 */
public final class CheckInPreview {

    private CheckInPreview() {}

    public static Result preview(Territory territory, ExplorerId member, RegionSnapshot region, CheckInRewards rewards) {
        return preview(territory, member, region, Optional.empty(), rewards);
    }

    /** @param mysteryRegion 처리 시각이 속한 주의 미스터리 지역(8단계, 모르면 비어 있음) */
    public static Result preview(Territory territory, ExplorerId member, RegionSnapshot region, Optional<RegionCode> mysteryRegion,
                                 CheckInRewards rewards) {
        VisitFacts facts = territory.factsFor(member, region);
        if (facts.alreadyVisited()) {
            return new Result(region, facts, List.of(), 0, List.of());
        }
        boolean mysteryOfWeek = mysteryRegion.filter(region.code()::equals).isPresent();
        List<XpLine> lines = List.copyOf(rewards.award(region.rarity(), facts.firstInProvince(), facts.firstClaim(),
            mysteryOfWeek));
        int total = lines.stream().mapToInt(XpLine::amount).sum();
        return new Result(region, facts, lines, total, List.of(regionItemId(region.code())));
    }

    /** 지역 아이템 itemId 규칙(§2-5): region:{code} */
    public static String regionItemId(RegionCode code) {
        return "region:" + code.value();
    }

    public enum XpSource { REGION_BASE, PROVINCE_FIRST, FIRST_CLAIM, MYSTERY_BONUS }

    public record XpLine(XpSource source, int amount) {}

    public record Result(RegionSnapshot region, VisitFacts facts, List<XpLine> lines, int totalXp, List<String> itemIds) {
        /** 컬렉션은 방어 복사(null 불가). */
        public Result {
            lines = List.copyOf(lines);
            itemIds = List.copyOf(itemIds);
        }
    }
}
