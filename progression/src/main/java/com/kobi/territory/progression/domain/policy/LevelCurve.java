package com.kobi.territory.progression.domain.policy;

/**
 * 레벨 곡선 — 결정적 함수 level = floor((1 + √(1 + xp/d)) / 2) (프로토타입 공식, d 는 카탈로그 levels.json).
 * 부동소수 경계 오차를 피하려고 같은 식의 정수 형태로 계산한다: 레벨 L 의 하한 XP = 4·d·L·(L−1).
 * 다음 값을 계산하는 값 객체라 class(class vs record 기준).
 */
public final class LevelCurve {

    private final int divisor;

    private LevelCurve(int divisor) {
        if (divisor < 1) throw new IllegalArgumentException("divisor >= 1");
        this.divisor = divisor;
    }

    public static LevelCurve withDivisor(int divisor) {
        return new LevelCurve(divisor);
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

    @Override
    public boolean equals(Object other) {
        return other instanceof LevelCurve curve && divisor == curve.divisor;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(divisor);
    }

    @Override
    public String toString() {
        return "LevelCurve[divisor=" + divisor + "]";
    }
}
