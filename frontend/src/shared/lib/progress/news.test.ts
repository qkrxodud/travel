/**
 * 게임 소식 — 진행 값이 바뀌었을 때 새로 생긴 보호권 사용·연속 탐험 마일스톤·시·도 정복만 고른다(판정은 서버 값).
 * 이야기 순서: 보호권 사용 → 마일스톤 달성 → 시·도 정복.
 */
import { describe, expect, it } from 'vitest';
import type { MilestoneResponse, ProgressResponse, ProvinceProgressResponse } from '../../../api/types/progression';
import { conqueredProvinceCodes, gameNews } from './news';

type GameProgress = Pick<ProgressResponse, 'streakFreeze' | 'milestones' | 'provinces'>;

const milestone = (months: number, reached: boolean): MilestoneResponse =>
  ({ months, xp: 50, freezes: 1, titleId: `streak-${months}`, titleName: '꾸준한 탐험가', reached, reachedAt: reached ? '2026-12-01T00:00:00Z' : null, remainingMonths: reached ? 0 : 1 });
const province = (code: string, conquered: boolean): ProvinceProgressResponse =>
  ({ code, name: code === 'KR-29' ? '세종' : '서울', visited: 1, total: 1, percent: 100, complete: conquered, conquered, conqueredAt: conquered ? '2026-10-04T00:00:00Z' : null });
const THIS_MONTH = { month: '2027-01', questsRewarded: 0, questsRequired: 4, reward: 1, earned: false, granted: 0 };
const progress = (overrides: Partial<GameProgress> = {}): GameProgress =>
  ({ streakFreeze: { held: 0, max: 2, lastUsed: null, thisMonth: THIS_MONTH }, milestones: [milestone(3, false), milestone(6, false)], provinces: [province('KR-11', false), province('KR-29', false)], ...overrides });

describe('보호권 사용', () => {
  it('보호권을 새로 쓴 기록이 생기면 그 기록을 알린다', () => {
    const lastUsed = { month: '2027-01', count: 1, at: '2027-01-05T00:00:00Z' };
    expect(gameNews(progress(), progress({ streakFreeze: { held: 0, max: 2, lastUsed, thisMonth: THIS_MONTH } })).freezeUsed).toEqual(lastUsed);
  });

  it('이미 알던 사용 기록이면 다시 알리지 않는다', () => {
    const used = progress({ streakFreeze: { held: 1, max: 2, lastUsed: { month: '2027-01', count: 1, at: '2027-01-05T00:00:00Z' }, thisMonth: THIS_MONTH } });
    expect(gameNews(used, { ...used, streakFreeze: { ...used.streakFreeze, held: 0 } }).freezeUsed).toBeNull();
  });
});

describe('연속 탐험 마일스톤 달성', () => {
  it('이번에 새로 달성한 마일스톤만 고른다', () => {
    const news = gameNews(progress(), progress({ milestones: [milestone(3, true), milestone(6, false)] }));
    expect(news.milestones.map(reached => reached.months)).toEqual([3]);
  });

  it('이미 달성했던 마일스톤은 다시 고르지 않는다', () => {
    const reached = progress({ milestones: [milestone(3, true), milestone(6, false)] });
    expect(gameNews(reached, reached).milestones).toEqual([]);
  });
});

describe('시·도 정복', () => {
  it('이번에 새로 정복한 시·도만 고른다', () => {
    const news = gameNews(progress(), progress({ provinces: [province('KR-11', false), province('KR-29', true)] }));
    expect(news.conquered.map(conquered => conquered.name)).toEqual(['세종']);
  });

  it('정복 기록이 있는 시·도 코드를 모은다(진행 값이 아직 없으면 비어 있다)', () => {
    expect([...conqueredProvinceCodes([province('KR-11', true), province('KR-29', false)])]).toEqual(['KR-11']);
    expect(conqueredProvinceCodes(undefined).size).toBe(0);
  });
});
