package com.kobi.territory.progression.domain;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 진행 규칙 묶음(정책 VO). domain 은 카탈로그·설정을 모르므로 application 이 카탈로그 정의(levels·badges·titles)와
 * 보상 함수, 시간대로 조립해 넘긴다. 숫자를 도메인에 박지 않는다.
 *
 * @param provinceTotals 시·도 코드 → 전체(현행) 지역 수
 * @param zone           "달"(스트릭·월간 퀘스트)을 판단하는 시간대
 */
public record ProgressionPolicy(LevelCurve curve, XpRewards rewards, List<Badge> badges, List<TitleRule> titles,
                                Map<String, Integer> provinceTotals, int totalRegions, ZoneId zone) {
    public ProgressionPolicy {
        Objects.requireNonNull(curve, "curve");
        Objects.requireNonNull(rewards, "rewards");
        Objects.requireNonNull(zone, "zone");
        badges = List.copyOf(badges);
        titles = List.copyOf(titles);
        provinceTotals = Map.copyOf(provinceTotals);
    }
}
