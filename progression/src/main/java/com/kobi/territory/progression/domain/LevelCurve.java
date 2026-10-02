package com.kobi.territory.progression.domain;

/**
 * 레벨 곡선 — 결정적 함수 level = floor((1 + √(1 + xp/d)) / 2) (프로토타입 공식, d 는 카탈로그 levels.json).
 * 부동소수 경계 오차를 피하려고 같은 식의 정수 형태로 계산한다: 레벨 L 의 하한 XP = 4·d·L·(L−1).
 */
public record LevelCurve(int divisor) {

    public LevelCurve {
        if (divisor < 1) throw new IllegalArgumentException("divisor >= 1");
    }

    /** 레벨 L 에 도달하는 최소 XP. L=1 → 0. */
    public long threshold(int level) {
        if (level < 1) throw new IllegalArgumentException("level >= 1");
        return 4L * divisor * level * (level - 1);
    }

    public int levelOf(long xp) {
        int level = 1;
        while (threshold(level + 1) <= xp) level++;
        return level;
    }
}
