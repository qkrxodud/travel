/**
 * 지역 카탈로그 — 서버 /catalog/* 응답을 화면이 찾기 쉬운 색인으로 바꾼다. 희귀도·XP·시·도 지역 수는 서버 값 그대로다.
 */
import { describe, expect, it } from 'vitest';
import { CATALOG } from '../../../test/fixtures';
import { labelOfCode, TIER_LABEL, toTier } from './catalog';

describe('지역 카탈로그', () => {
  it('지역을 화면 코드로 찾고 "시·도 지역" 이름으로 부른다', () => {
    expect(labelOfCode(CATALOG, '31370')).toBe('경기 가평군');
    expect(labelOfCode(CATALOG, 'KR-31370')).toBe('경기 가평군');
  });

  it('모르는 지역은 코드 그대로 부른다', () => {
    expect(labelOfCode(CATALOG, 'KR-99999')).toBe('KR-99999');
  });

  it('시·도는 서버가 정한 표시 순서로 늘어놓고 시·도마다 지역 수를 안다', () => {
    expect(CATALOG.provinces).toEqual(['서울', '경기', '경북']);
    expect(CATALOG.provinceTotal.get('서울')).toBe(2);
    expect(CATALOG.provinceByCode.get('KR-37')).toBe('경북');
  });

  it('지역마다 받을 아이템과 희귀도별 XP·시·도 첫 방문 보너스를 서버 규칙 그대로 둔다', () => {
    expect(CATALOG.itemByRegion.get('37430')?.name).toBe('울릉 독도 바다 풍경');
    expect(CATALOG.xpByTier).toEqual({ common: 10, rare: 20, legend: 50 });
    expect(CATALOG.provinceFirstBonus).toBe(15);
  });

  it('희귀도는 일반·희귀·전설이고, 모르면 일반으로 본다', () => {
    expect([toTier('LEGEND'), toTier('RARE'), toTier('COMMON'), toTier(null)]).toEqual(['legend', 'rare', 'common', 'common']);
    expect(TIER_LABEL.legend).toBe('전설');
  });
});
