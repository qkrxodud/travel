package com.kobi.territory.catalog.domain;

import java.util.Objects;

/**
 * 퀘스트 정의. MONTHLY 는 달마다 새 보드(본인 체크인 기준, 소급 없음), ALWAYS 는 상시 도전(보드 'ALL' 하나).
 *
 * @param param  지표 보조 값(PROVINCES_WITH_MIN_REGIONS 의 시·도당 최소 지역 수), 그 외 0
 * @param target 달성 목표
 * @param xp     보상 받기(claim) 시 XP
 * @param title  보상 칭호 이름(상시 도전만), 없으면 null
 */
public record QuestDefinition(String id, Scope scope, String ico, String name, String desc, Metric metric, int param,
                              int target, int xp, String title) {

    public enum Scope { MONTHLY, ALWAYS }

    /** 퀘스트 진행 지표. 지역 단위로 세며 같은 지역은 한 번만 센다. */
    public enum Metric {
        NEW_REGIONS, NON_COMMON_REGIONS, FIRST_IN_PROVINCE, SET_REGIONS, LEGEND_REGIONS, PROVINCES_WITH_MIN_REGIONS
    }

    public QuestDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(metric, "metric");
        Objects.requireNonNull(name, "name");
        if (target < 1) throw new IllegalStateException("target >= 1: " + id);
        if (xp < 0) throw new IllegalStateException("xp >= 0: " + id);
        if (metric == Metric.PROVINCES_WITH_MIN_REGIONS && param < 1) throw new IllegalStateException("param >= 1: " + id);
    }
}
