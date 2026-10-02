package com.kobi.territory.catalog.api.query;

import java.util.List;

/** 진행(2단계) 정의 데이터 공개 Query: 레벨 곡선·칭호, 도감 세트 9, 뱃지 12, 퀘스트(월간·상시), 칭호 전체. */
public interface ProgressionRules {

    /** 레벨 공식 상수 d: level = floor((1 + √(1 + xp/d)) / 2). */
    int levelDivisor();

    List<LevelTitleView> levelTitles();

    List<SetView> sets();

    List<BadgeView> badges();

    List<QuestView> quests();

    /** 칭호 전체(레벨 → 세트 → 상시 도전 → 시·도 주인 순). */
    List<TitleView> titles();

    record LevelTitleView(int level, String name) {}

    /** @param regionCodes KR-xxxxx */
    record SetView(String id, String name, String desc, String title, List<String> regionCodes, String backgroundName) {}

    /** @param type REGION_COUNT | PROVINCES_COMPLETE | PROVINCE_GROUPS_TOUCHED | LEGEND_COUNT | ALL_PROVINCES_TOUCHED |
     *              SETS_COMPLETED | STREAK_MONTHS | CONQUEST_RATIO */
    record BadgeConditionView(String type, int min, List<String> provinces, List<List<String>> groups, double ratio) {}

    record BadgeView(String id, String ico, String name, String desc, BadgeConditionView condition) {}

    /** @param scope MONTHLY | ALWAYS, @param metric NEW_REGIONS | NON_COMMON_REGIONS | FIRST_IN_PROVINCE | SET_REGIONS |
     *              LEGEND_REGIONS | PROVINCES_WITH_MIN_REGIONS */
    record QuestView(String id, String scope, String ico, String name, String desc, String metric, int param, int target,
                     int xp, String title) {}

    /** @param source LEVEL | SET | QUEST | PROVINCE, @param ref 레벨 숫자·세트 id·퀘스트 id·시·도 코드 */
    record TitleView(String id, String name, String how, String source, String ref) {}
}
