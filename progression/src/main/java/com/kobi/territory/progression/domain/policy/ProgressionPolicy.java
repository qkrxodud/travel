package com.kobi.territory.progression.domain.policy;

import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 진행 규칙 묶음(정책 VO). domain 은 카탈로그·설정을 모르므로 application 이 카탈로그 정의(levels·badges·titles·streak-rules)와
 * 보상 함수, 시·도별 현행 지역 명부, 시간대로 조립해 넘긴다. 숫자를 도메인에 박지 않는다.
 *
 * @param provinceRoster  시·도 코드 → 현행 지역(시·도 100% 판정 — 폐지 지역 제외, 8단계)
 * @param zone            "달"(스트릭·월간 퀘스트)을 판단하는 시간대
 * @param streakRules     보호권·마일스톤(8단계)
 * @param monthlyQuestIds 월간 퀘스트 id 전부 — 한 달에 모두 보상 받으면 보호권(8단계)
 */
public record ProgressionPolicy(LevelCurve curve, XpRewards rewards, Badges badges, TitleRules titleRules,
                                ProvinceRoster provinceRoster, int totalRegions, ZoneId zone, StreakRules streakRules,
                                Set<String> monthlyQuestIds) {
    public ProgressionPolicy {
        Objects.requireNonNull(curve, "curve");
        Objects.requireNonNull(rewards, "rewards");
        Objects.requireNonNull(zone, "zone");
        Objects.requireNonNull(badges, "badges");
        Objects.requireNonNull(titleRules, "titleRules");
        Objects.requireNonNull(provinceRoster, "provinceRoster");
        Objects.requireNonNull(streakRules, "streakRules");
        monthlyQuestIds = Set.copyOf(monthlyQuestIds);
    }

    /** 시·도 코드 → 현행 지역 수(표시 순서). */
    public Map<String, Integer> provinceTotals() {
        return provinceRoster.totals();
    }
}
