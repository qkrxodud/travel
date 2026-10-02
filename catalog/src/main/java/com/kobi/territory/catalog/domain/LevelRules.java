package com.kobi.territory.catalog.domain;

import java.util.Comparator;
import java.util.List;

/**
 * 레벨 곡선 상수와 레벨 칭호(levels.json). 프로토타입 공식 level = floor((1 + √(1 + xp/divisor)) / 2).
 * 계산 자체는 진행 도메인(LevelCurve)이 정수 연산으로 한다 — 여기는 값만 둔다.
 */
public record LevelRules(int divisor, List<LevelTitle> titles) {
    public LevelRules {
        if (divisor < 1) throw new IllegalArgumentException("divisor >= 1");
        titles = titles.stream().sorted(Comparator.comparingInt(LevelTitle::level)).toList();
        if (titles.isEmpty() || titles.get(0).level() != 1) throw new IllegalStateException("레벨 1 칭호가 필요하다");
    }
}
