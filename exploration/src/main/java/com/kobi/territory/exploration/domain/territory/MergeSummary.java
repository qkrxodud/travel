package com.kobi.territory.exploration.domain.territory;

/**
 * 병합 안내("익명 기록 N곳을 계정으로 옮겼어요")용 요약.
 *
 * @param movedRegions 옮겨질 익명 기록(방문) 수
 * @param newRegions   그중 계정 영토에 없던 새 지역 수
 */
public record MergeSummary(int movedRegions, int newRegions) {
    public static final MergeSummary NONE = new MergeSummary(0, 0);
}
