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

    /** 칭호 전체(레벨 → 세트 → 상시 도전 → 연속 탐험 마일스톤 → 시·도 주인 순). */
    List<TitleView> titles();

    /** 연속 탐험 규칙(8단계): 보호권 보유 상한·월간 퀘스트 완주 보상, 마일스톤. */
    StreakRulesView streakRules();

    record LevelTitleView(int level, String name) {}

    /** @param regionCodes KR-xxxxx */
    record SetView(String id, String name, String desc, String title, List<String> regionCodes, String backgroundName) {}

    /** @param type REGION_COUNT | PROVINCES_COMPLETE | PROVINCE_GROUPS_TOUCHED | LEGEND_COUNT | ALL_PROVINCES_TOUCHED |
     *              SETS_COMPLETED | STREAK_MONTHS | CONQUEST_RATIO | MYSTERY_FOUND(8단계) */
    record BadgeConditionView(String type, int min, List<String> provinces, List<List<String>> groups, double ratio) {}

    record BadgeView(String id, String ico, String name, String desc, BadgeConditionView condition) {}

    /** @param scope MONTHLY | ALWAYS, @param metric NEW_REGIONS | NON_COMMON_REGIONS | FIRST_IN_PROVINCE | SET_REGIONS |
     *              LEGEND_REGIONS | PROVINCES_WITH_MIN_REGIONS */
    record QuestView(String id, String scope, String ico, String name, String desc, String metric, int param, int target,
                     int xp, String title) {}

    /** @param source LEVEL | SET | QUEST | PROVINCE | STREAK(8단계), @param ref 레벨 숫자·세트 id·퀘스트 id·시·도 코드·개월 수 */
    record TitleView(String id, String name, String how, String source, String ref) {}

    /** 연속 탐험 마일스톤 하나: 처음 도달하면 XP·칭호(titleId)·보호권(freezes, 보유 상한 안)·한정 아이템(STREAK_MILESTONE). */
    record MilestoneView(int months, int xp, int freezes, String titleId, String titleName) {}

    /**
     * @param freezeMaxHeld        보호권 최대 보유 수
     * @param monthlyQuestsFreezes 한 달의 월간 퀘스트를 모두 보상 받으면 받는 보호권 수
     * @param milestones           개월 수 오름차순
     */
    record StreakRulesView(int freezeMaxHeld, int monthlyQuestsFreezes, List<MilestoneView> milestones) {
        public StreakRulesView {
            milestones = List.copyOf(milestones);
        }
    }
}
