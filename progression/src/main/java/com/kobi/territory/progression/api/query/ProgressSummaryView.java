package com.kobi.territory.progression.api.query;

/**
 * 진행 요약(공개 Query DTO).
 *
 * @param titleName    프로필에 보일 칭호 이름(고른 칭호, 없으면 레벨 칭호)
 * @param streakMonths 이번 달 기준 연속 개월
 * @param badgeCount   얻은 뱃지 수
 */
public record ProgressSummaryView(long xp, int level, String titleName, int streakMonths, int badgeCount) {}
