import { describe, expect, it } from 'vitest';
import { monthShift, percentOf, streakRun, streakStrip, streakText } from './streak';

describe('스트릭 띠', () => {
  const today = new Date(2026, 9, 3); // 2026-10-03

  it('n개월 앞뒤 달', () => {
    expect(monthShift(today, 0)).toBe('2026-10');
    expect(monthShift(today, -11)).toBe('2025-11');
    expect(monthShift(today, 3)).toBe('2027-01');
  });

  it('연속 구간 = lastMonth 에서 끝나는 n개월', () => {
    expect([...streakRun({ months: 3, lastMonth: '2026-01', activeThisMonth: false })]).toEqual(['2026-01', '2025-12', '2025-11']);
    expect(streakRun({ months: 0, lastMonth: null, activeThisMonth: false }).size).toBe(0);
  });

  it('최근 12개월(오래된 달부터), 연속·이번 달 표시', () => {
    const cells = streakStrip({ months: 2, lastMonth: '2026-10', activeThisMonth: true }, today, '2026-10');
    expect(cells).toHaveLength(12);
    expect(cells[0]).toMatchObject({ month: '2025-11', label: '11월', on: false, now: false });
    expect(cells.slice(-2).map(cell => cell.on)).toEqual([true, true]);
    expect(cells[11].now).toBe(true);
  });

  it('안내 문구·막대 폭', () => {
    expect(streakText({ months: 0, lastMonth: null, activeThisMonth: false })).toContain('스트릭이 시작돼요');
    expect(streakText({ months: 4, lastMonth: '2026-10', activeThisMonth: true })).toBe('4개월 연속 탐험 중. 다음 달에도 1곳이면 유지됩니다.');
    expect(streakText({ months: 4, lastMonth: '2026-09', activeThisMonth: false })).toContain('이번 달 아직 0곳');
    expect(percentOf(1, 3)).toBe(33);
  });
});
