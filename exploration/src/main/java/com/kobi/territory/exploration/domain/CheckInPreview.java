package com.kobi.territory.exploration.domain;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import java.util.ArrayList;
import java.util.List;

/**
 * 체크인 미리보기 도메인 서비스 — 순수 함수(현재 영토 + 지역 → 받을 보상).
 * 체크인 모달의 "획득 XP +N"을 계산한다. 2단계 진행 핸들러가 같은 규칙을 재사용해야 예상/실제가 맞는다.
 *
 * 1단계 범위: 지역 기본 XP(희귀도) + 시·도 첫 방문 보너스 + 선점 보너스, 지역 아이템 1개.
 * 세트 완성·퀘스트 보상은 정의가 2단계에 들어오므로 아직 포함하지 않는다.
 */
public final class CheckInPreview {

    private CheckInPreview() {}

    public static Result preview(Territory territory, ExplorerId member, RegionSnapshot region, RewardTable rewards) {
        VisitFacts facts = territory.factsFor(member, region);
        if (facts.alreadyVisited()) {
            return new Result(region, facts, List.of(), 0, List.of());
        }
        List<XpLine> lines = new ArrayList<>();
        lines.add(new XpLine(XpSource.REGION_BASE, rewards.base(region.rarity())));
        if (facts.firstInProvince()) lines.add(new XpLine(XpSource.PROVINCE_FIRST, rewards.provinceFirstBonus()));
        if (facts.firstClaim()) lines.add(new XpLine(XpSource.FIRST_CLAIM, rewards.claimBonus()));
        int total = lines.stream().mapToInt(XpLine::amount).sum();
        return new Result(region, facts, List.copyOf(lines), total, List.of(regionItemId(region.code())));
    }

    /** 지역 아이템 itemId 규칙(§2-5): region:{code} */
    public static String regionItemId(RegionCode code) {
        return "region:" + code.value();
    }

    public enum XpSource { REGION_BASE, PROVINCE_FIRST, FIRST_CLAIM }

    public record XpLine(XpSource source, int amount) {}

    public record Result(RegionSnapshot region, VisitFacts facts, List<XpLine> lines, int totalXp, List<String> itemIds) {}
}
