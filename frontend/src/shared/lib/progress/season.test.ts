/**
 * 계절 한정 테마 문구 — 회차·기간·남은 초·진행·보상은 서버 값이고, 화면은 남은 기간·기간·배지·이름 문구만 만든다.
 * 이야기 순서: 남은 기간 → 회차 기간 → 지도 탭 배지 → 다음·지난 회차 → 친구 소식의 회차 이름 → 완성 알림.
 */
import { describe, expect, it } from 'vitest';
import type { SeasonRoundResponse, SeasonsResponse } from '../../../api/types/progression';
import { newlyCompletedRounds, nextRoundText, openRound, pastRoundText, seasonBadgeText, seasonPeriodText, seasonRemainingText, seasonRoundName } from './season';

const DAY = 86400;
const round = (overrides: Partial<SeasonRoundResponse> = {}): SeasonRoundResponse => ({
  roundId: 'autumn-2026', seasonId: 'autumn', name: '2026 단풍 명소', emoji: '🍁', year: 2026,
  startsAt: '2026-09-30T15:00:00Z', endsAt: '2026-11-30T15:00:00Z', remainingSeconds: 58 * DAY, open: true,
  have: 3, total: 10, completed: false, completedAt: null, rewarded: false, xp: 150, titleId: 'season-autumn', titleName: '단풍 사냥꾼',
  backgroundItemId: 'season:autumn-2026', regions: [], ...overrides,
});
const seasons = (overrides: Partial<SeasonsResponse> = {}): SeasonsResponse =>
  ({ mapId: 'personal', now: '2026-10-04T03:00:00Z', current: [round()], next: null, history: [], ...overrides });

describe('회차 남은 기간', () => {
  it('이틀 넘게 남으면 남은 날 수만 알려 준다', () => {
    expect(seasonRemainingText(58 * DAY + 5 * 3600)).toBe('58일 남음');
    expect(seasonRemainingText(2 * DAY)).toBe('2일 남음');
  });

  it('이틀이 안 남으면 시간으로, 한 시간이 안 남으면 분으로 알려 준다', () => {
    expect(seasonRemainingText(2 * DAY - 1)).toBe('47시간 남음');
    expect(seasonRemainingText(3 * 3600 + 59 * 60)).toBe('3시간 남음');
    expect(seasonRemainingText(42 * 60 + 10)).toBe('42분 남음');
  });

  it('1분도 안 남으면 곧 끝난다고, 다 지났으면 끝났다고 알려 준다', () => {
    expect(seasonRemainingText(30)).toBe('곧 끝나요');
    expect(seasonRemainingText(0)).toBe('끝났어요');
    expect(seasonRemainingText(-10)).toBe('끝났어요');
  });
});

describe('회차 기간', () => {
  it('서울 날짜로 첫날과 마지막 날을 보여 준다(닫히는 순간은 마지막 날 다음 날 0시)', () => {
    expect(seasonPeriodText(round())).toBe('10월 1일 ~ 11월 30일');
    expect(seasonPeriodText({ startsAt: '2027-03-19T15:00:00Z', endsAt: '2027-04-30T15:00:00Z' })).toBe('3월 20일 ~ 4월 30일');
  });
});

describe('지도 탭 배지', () => {
  it('계절 기간이면 지금 회차를 고르고, 기간이 아니면 배지를 숨긴다', () => {
    expect(openRound(seasons())?.roundId).toBe('autumn-2026');
    expect(openRound(seasons({ current: [] }))).toBeNull();
    expect(openRound(undefined)).toBeNull();
  });

  it('회차 이름과 기간 안에 모은 수, 남은 기간을 한 줄로 보여 준다', () => {
    expect(seasonBadgeText(round())).toBe('🍁 2026 단풍 명소 3/10 · 58일 남음');
  });

  it('완성한 회차는 남은 기간 대신 완성이라고 보여 준다', () => {
    expect(seasonBadgeText(round({ have: 10, completed: true }))).toBe('🍁 2026 단풍 명소 10/10 · 완성');
  });
});

describe('다음 회차와 지난 회차', () => {
  it('다음 회차는 첫날을 서울 날짜로 알려 준다', () => {
    expect(nextRoundText({ roundId: 'spring-2027', seasonId: 'spring', name: '2027 벚꽃 명소', emoji: '🌸', startsAt: '2027-03-19T15:00:00Z', endsAt: '2027-04-30T15:00:00Z' }))
      .toBe('다음 회차 🌸 2027 벚꽃 명소 · 3월 20일부터');
  });

  it('지난 회차는 모은 수와 완성 여부를 남긴다(미완성 기록도 보인다)', () => {
    expect(pastRoundText(round({ open: false, have: 7 }))).toBe('🍁 2026 단풍 명소 · 7/10 · 미완성');
    expect(pastRoundText(round({ open: false, have: 10, completed: true }))).toBe('🍁 2026 단풍 명소 · 10/10 · 완성');
  });
});

describe('친구 소식의 회차 이름', () => {
  it('서버가 알려 준 회차면 그 이름을 쓴다', () => {
    expect(seasonRoundName('autumn-2026', seasons())).toBe('2026 단풍 명소');
  });

  it('모르는 해의 같은 계절 회차면 계절 이름에 그 해를 붙인다', () => {
    expect(seasonRoundName('autumn-2025', seasons())).toBe('2025 단풍 명소');
  });

  it('계절도 모르면 회차 id 그대로 부른다', () => {
    expect(seasonRoundName('spring-2027', seasons())).toBe('spring-2027');
    expect(seasonRoundName('spring-2027', undefined)).toBe('spring-2027');
  });
});

describe('회차 완성 알림', () => {
  it('이번에 새로 완성해 내가 보상을 받은 회차만 알린다', () => {
    const before = seasons();
    expect(newlyCompletedRounds(before, seasons({ current: [round({ have: 10, completed: true, rewarded: true })] })).map(done => done.roundId)).toEqual(['autumn-2026']);
  });

  it('이미 알던 완성이거나 완성 뒤에 함께해 보상이 없으면 알리지 않는다', () => {
    const done = seasons({ current: [round({ completed: true, rewarded: true })] });
    expect(newlyCompletedRounds(done, done)).toEqual([]);
    expect(newlyCompletedRounds(seasons(), seasons({ current: [round({ completed: true, rewarded: false })] }))).toEqual([]);
  });
});
