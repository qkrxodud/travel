package com.kobi.territory.progression.api.query;

/**
 * 연속 탐험 상태(공개 Query DTO, 12단계).
 *
 * @param month                 기준 달(yyyy-MM, 서비스 시간대의 이번 달)
 * @param streakMonths          이번 달 기준 연속 개월 수(보호권으로 이어 갈 수 있으면 유지)
 * @param checkedInThisMonth    이번 달에 이미 새 지역을 칠했는지
 * @param freezesHeld           가진 보호권 수
 * @param freezesNeededIfMissed 이번 달을 놓치면 다음 달에 필요한 보호권 수
 * @param atRisk                이번 달을 놓치면 끊길 수 있는 연속이 있다(아직 이번 달에 칠하지 않음 + 이어지는 연속 있음)
 */
public record StreakStandingView(String explorerId, String month, int streakMonths, boolean checkedInThisMonth, int freezesHeld,
                                 int freezesNeededIfMissed, boolean atRisk) {}
