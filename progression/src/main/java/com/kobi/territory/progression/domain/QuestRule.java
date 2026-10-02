package com.kobi.territory.progression.domain;

import com.kobi.territory.common.model.Rarity;
import java.time.YearMonth;
import java.util.Objects;

/**
 * 퀘스트 규칙(카탈로그 quests.json → application 이 변환). 지표(metric)가 체크인 사실 하나를 셀지 판단한다.
 *
 * @param param PROVINCES_WITH_MIN_REGIONS 의 시·도당 최소 지역 수
 */
public record QuestRule(String id, Scope scope, Metric metric, int param, int target, int xp) {

    public enum Scope { MONTHLY, ALWAYS }

    public enum Metric {
        NEW_REGIONS, NON_COMMON_REGIONS, FIRST_IN_PROVINCE, SET_REGIONS, LEGEND_REGIONS, PROVINCES_WITH_MIN_REGIONS
    }

    public QuestRule {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(metric, "metric");
        if (target < 1) throw new IllegalArgumentException("target >= 1");
    }

    /** 이 체크인 사실이 이 퀘스트에서 셀 대상인가. */
    boolean counts(QuestFact fact) {
        return switch (metric) {
            case NEW_REGIONS, PROVINCES_WITH_MIN_REGIONS -> true;
            case NON_COMMON_REGIONS -> fact.rarity() != Rarity.COMMON;
            case FIRST_IN_PROVINCE -> fact.firstInProvince();
            case SET_REGIONS -> fact.inAnySet();
            case LEGEND_REGIONS -> fact.rarity() == Rarity.LEGEND;
        };
    }

    /** 이 퀘스트가 지금(current 달) 속하는 보드. */
    public QuestPeriod periodAt(YearMonth current) {
        return scope == Scope.ALWAYS ? QuestPeriod.ALL : QuestPeriod.of(current);
    }

    boolean belongsTo(QuestPeriod period) {
        return (scope == Scope.ALWAYS) == period.always();
    }
}
