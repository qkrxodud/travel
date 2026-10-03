/**
 * 퀘스트 탭 — 달마다 이어 가는 탐험 스트릭과, 달성한 퀘스트의 보상 받기. 진행 수·달성·받음 여부는 서버 값이다.
 * 이야기 순서: 스트릭 띠 → 스트릭 안내 → 스트릭 보호권 → 연속 탐험 마일스톤 → 퀘스트 한 줄 → 보상 받기.
 */
import { act, cleanup, fireEvent, render, renderHook, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { progressionApi } from '../../api/progression';
import type { MilestoneResponse, MonthlyFreezeResponse, QuestResponse, StreakFreezeResponse, StreakResponse } from '../../api/types/progression';
import { freezeBadge, freezeProgressText } from '../../shared/lib/progress/freeze';
import { progressKeys } from '../../shared/queries/progress';
import { settleInterval, SETTLE_INTERVAL_MS, useSyncStore } from '../../store/syncStore';
import { useToastStore } from '../../store/toastStore';
import { useUiStore } from '../../store/uiStore';
import { serverState } from '../../test/serverState';
import { QuestRow } from './components/QuestRow';
import { milestoneRows, monthShift, nextMilestoneText, percentOf, streakRun, streakStrip, streakText } from './model/streak';
import { useClaimQuest } from './queries';

vi.mock('../../api/progression', () => ({ progressionApi: { claim: vi.fn(async () => ({ questId: 'q1', xp: 50 })) } }));

const quest = (overrides: Partial<QuestResponse>): QuestResponse => ({
  id: 'q1', scope: 'MONTHLY', ico: '🗺️', name: '이달의 세 곳', desc: '이번 달 3곳 칠하기', current: 1, target: 3, xp: 50, title: null,
  achieved: false, claimed: false, claimedAt: null, claimable: false, ...overrides,
} as QuestResponse);

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  useSyncStore.setState({ settleUntil: 0, announce: false });
  useUiStore.setState({ sampleMode: false });
  useToastStore.setState({ toasts: [] });
});

const streak = (overrides: Partial<StreakResponse>): StreakResponse => ({ months: 0, lastMonth: null, activeThisMonth: false, freezesNeeded: 0, frozenMonths: [], ...overrides });
const freeze = (overrides: Partial<StreakFreezeResponse> = {}): StreakFreezeResponse => ({
  held: 0, max: 2, lastUsed: null, thisMonth: { month: '2026-10', questsRewarded: 0, questsRequired: 4, reward: 1, earned: false, granted: 0 }, ...overrides,
});
const milestone = (months: number, overrides: Partial<MilestoneResponse> = {}): MilestoneResponse => ({
  months, xp: months * 50 / 3, freezes: 1, titleId: `streak-${months}`, titleName: '꾸준한 탐험가', reached: false, reachedAt: null, remainingMonths: months, ...overrides,
});

describe('스트릭 띠', () => {
  it('서버가 알려 준 이번 달까지 최근 12개월을 오래된 달부터 늘어놓고 이번 달을 표시한다', () => {
    const cells = streakStrip(streak({ months: 2, lastMonth: '2026-10', activeThisMonth: true }), '2026-10');
    expect(cells).toHaveLength(12);
    expect(cells[0]).toMatchObject({ month: '2025-11', label: '11월', on: false, now: false });
    expect(cells[11]).toMatchObject({ month: '2026-10', now: true });
  });

  it('서버 시계가 브라우저보다 앞서 있어도 서버의 이번 달을 기준으로 늘어놓는다', () => {
    const cells = streakStrip(streak({ months: 1, lastMonth: '2027-01', activeThisMonth: true }), '2027-01');
    expect(cells[11]).toMatchObject({ month: '2027-01', on: true, now: true });
  });

  it('서버가 알려 준 마지막 달에서 끝나는 n개월을 연속 구간으로 칠한다', () => {
    const cells = streakStrip(streak({ months: 2, lastMonth: '2026-10', activeThisMonth: true }), '2026-10');
    expect(cells.slice(-3).map(cell => cell.on)).toEqual([false, true, true]);
  });

  it('연속 구간은 해를 넘어 이어지고, 스트릭이 없으면 비어 있다', () => {
    expect([...streakRun(streak({ months: 3, lastMonth: '2026-01' }))]).toEqual(['2026-01', '2025-12', '2025-11']);
    expect(streakRun(streak({})).size).toBe(0);
  });

  it('달 이동은 해를 넘어도 맞는 달을 가리킨다', () => {
    const today = new Date(2026, 9, 3);
    expect(monthShift(today, 0)).toBe('2026-10');
    expect(monthShift(today, -11)).toBe('2025-11');
    expect(monthShift(today, 3)).toBe('2027-01');
  });

  describe('보호권으로 빈 달을 메웠을 때', () => {
    // 6월 칠함 → 7월 빈 달(보호권 1) → 8·9월 칠함 → 10·11월 빈 달(보호권 2) → 12월 칠함: 연속 4개월(빈 달은 세지 않는다)
    const run = streak({ months: 4, lastMonth: '2026-12', activeThisMonth: true, frozenMonths: ['2026-07', '2026-10', '2026-11'] });

    it('서버가 알려 준 메운 달을 모두 얼음으로 보여 준다(가장 최근에 쓴 것만이 아니다)', () => {
      const cells = streakStrip(run, '2026-12');
      expect(cells.filter(cell => cell.frozen).map(cell => cell.month)).toEqual(['2026-07', '2026-10', '2026-11']);
    });

    it('연속 개월은 실제로 칠한 달만 세고, 메운 달을 건너뛰어 그 앞 달까지 이어 칠한다', () => {
      expect([...streakRun(run)].sort()).toEqual(['2026-06', '2026-08', '2026-09', '2026-12']);
      const cells = streakStrip(run, '2026-12');
      expect(cells.slice(-8).map(cell => (cell.frozen ? '🧊' : cell.on ? 'on' : '-'))).toEqual(['-', 'on', '🧊', 'on', 'on', '🧊', '🧊', 'on']);
    });
  });
});

describe('스트릭 안내', () => {
  it('스트릭이 없으면 이번 달 1곳으로 시작된다고 알려 준다', () => {
    expect(streakText(streak({}))).toContain('스트릭이 시작돼요');
  });

  it('이번 달에도 칠했으면 연속 개월 수와 유지 조건을 알려 준다', () => {
    expect(streakText(streak({ months: 4, lastMonth: '2026-10', activeThisMonth: true }))).toBe('4개월 연속 탐험 중. 다음 달에도 1곳이면 유지됩니다.');
  });

  it('이번 달 아직 안 칠했으면 끊기기 전에 1곳 칠하라고 알려 준다', () => {
    expect(streakText(streak({ months: 4, lastMonth: '2026-09' }))).toContain('이번 달 아직 0곳');
  });

  it('빈 달이 있어도 가진 보호권으로 메울 수 있으면 이번 달에 칠할 때 보호권을 쓴다고 알려 준다', () => {
    expect(streakText(streak({ months: 3, lastMonth: '2026-08', freezesNeeded: 1 }), freeze({ held: 1 })))
      .toBe('3개월째. 빈 달이 1개월 — 이번 달에 1곳 칠하면 보호권 1개로 이어져요.');
  });

  it('보호권이 모자라면 이번 달에 칠할 때 1개월부터 다시 시작된다고 알려 준다', () => {
    expect(streakText(streak({ months: 0, lastMonth: '2026-06', freezesNeeded: 3 }), freeze({ held: 2 }))).toContain('1개 모자라요');
  });
});

describe('스트릭 보호권', () => {
  const thisMonth = (overrides: Partial<MonthlyFreezeResponse> = {}): MonthlyFreezeResponse =>
    ({ month: '2026-10', questsRewarded: 0, questsRequired: 4, reward: 1, earned: false, granted: 0, ...overrides });

  it('헤더에는 가진 보호권 수를 얼음으로 보여 주고, 하나도 없으면 흐리게 보여 준다', () => {
    expect(freezeBadge(freeze({ held: 2 }))).toMatchObject({ text: '🧊×2', empty: false });
    expect(freezeBadge(freeze({ held: 0 }))).toMatchObject({ text: '🧊×0', empty: true });
    expect(freezeBadge(undefined)).toMatchObject({ text: '🧊×0', empty: true });
  });

  it('얻는 방법은 서버가 정한 퀘스트 수·주는 수·최대 수로 알려 준다', () => {
    expect(freezeBadge(freeze({ max: 2 })).hint).toContain('월간 퀘스트 보상 4개를 모두 받으면 1개(최대 2개)');
    expect(freezeBadge(freeze({ max: 3, thisMonth: thisMonth({ questsRequired: 5, reward: 2 }) })).hint).toContain('월간 퀘스트 보상 5개를 모두 받으면 2개(최대 3개)');
  });

  it('이번 달 월간 퀘스트 보상을 몇 개 받았는지와 모두 받으면 받을 보호권 수를 보여 준다', () => {
    expect(freezeProgressText(freeze({ thisMonth: thisMonth({ questsRewarded: 1 }) }))).toBe('이번 달 보상 1/4 받음 — 모두 받으면 보호권 1개');
  });

  it('이번 달 몫을 채워 보호권이 늘었으면 몇 개 받았는지 알려 준다', () => {
    expect(freezeProgressText(freeze({ held: 1, thisMonth: thisMonth({ questsRewarded: 4, earned: true, granted: 1 }) })))
      .toBe('이번 달 보상 4/4 받음 — 이번 달 보호권 1개를 받았어요');
  });

  it('이번 달 몫을 채웠어도 보호권이 가득이라 늘지 않았으면 그렇게 알려 준다', () => {
    expect(freezeProgressText(freeze({ held: 2, thisMonth: thisMonth({ questsRewarded: 4, earned: true, granted: 0 }) })))
      .toContain('보호권이 가득이라 더 받지 않았어요');
  });

  it('아직 몫을 채우지 않았는데 이미 최대로 갖고 있으면 더 받을 자리가 없다고 알려 준다', () => {
    expect(freezeProgressText(freeze({ held: 2, thisMonth: thisMonth({ questsRewarded: 2 }) }))).toContain('최대(2개)');
  });
});

describe('연속 탐험 마일스톤', () => {
  it('마일스톤마다 서버가 알려 준 남은 개월로 진행 막대를 채우고 보상을 적는다', () => {
    const [three, six] = milestoneRows([milestone(3, { xp: 50, remainingMonths: 1 }), milestone(6, { xp: 100, titleName: '반년의 발자국', remainingMonths: 4 })]);
    expect(three).toMatchObject({ label: '3개월', percent: 67, reached: false, status: '1개월 남음', reward: '+50 XP · 칭호 「꾸준한 탐험가」 · 보호권 +1' });
    expect(six).toMatchObject({ percent: 33, status: '4개월 남음' });
  });

  it('달성한 마일스톤은 막대를 다 채우고 달성으로 표시한다', () => {
    expect(milestoneRows([milestone(3, { reached: true, remainingMonths: 0 })])[0]).toMatchObject({ percent: 100, reached: true, status: '✓ 달성' });
  });

  it('보호권을 주지 않는 마일스톤은 보상에 보호권을 적지 않는다', () => {
    expect(milestoneRows([milestone(24, { xp: 400, freezes: 0, titleName: '두 해의 전설' })])[0].reward).toBe('+400 XP · 칭호 「두 해의 전설」');
  });

  it('다음 마일스톤까지 남은 달을 알려 주고, 모두 달성했으면 그렇게 알려 준다', () => {
    expect(nextMilestoneText(milestone(6, { remainingMonths: 2 }))).toBe('다음 마일스톤 6개월까지 2개월 남았어요.');
    expect(nextMilestoneText(null)).toBe('연속 탐험 마일스톤을 모두 달성했어요.');
  });
});

describe('퀘스트 한 줄', () => {
  const show = (overrides: Partial<QuestResponse>) => render(<QuestRow quest={quest(overrides)} />, { wrapper: serverState().wrapper });

  it('진행 중이면 지금 수와 목표 수, 진행 막대를 보여 준다', () => {
    show({ current: 1, target: 3 });
    expect(screen.getByText('1/3')).toBeTruthy();
    expect(percentOf(1, 3)).toBe(33);
  });

  it('달성했고 아직 안 받았으면 받기 버튼을 보여 준다', () => {
    show({ current: 3, achieved: true, claimable: true });
    expect(screen.getByRole('button', { name: '받기' })).toBeTruthy();
  });

  it('이미 받았으면 받음으로 보여 주고 다시 받을 수 없다', () => {
    show({ current: 3, achieved: true, claimed: true });
    expect(screen.getByText('받음')).toBeTruthy();
    expect(screen.queryByRole('button', { name: '받기' })).toBeNull();
  });

  it('칭호를 주는 퀘스트는 설명에 칭호 이름을 붙인다', () => {
    show({ title: '방랑자' });
    expect(screen.getByText('이번 달 3곳 칠하기 · 칭호 「방랑자」')).toBeTruthy();
  });
});

describe('보상 받기', () => {
  it('받으면 받은 XP 를 알려 준다', async () => {
    render(<QuestRow quest={quest({ current: 3, achieved: true, claimable: true })} />, { wrapper: serverState().wrapper });
    fireEvent.click(screen.getByRole('button', { name: '받기' }));
    await waitFor(() => expect(useToastStore.getState().toasts.map(toast => toast.sub)).toContain('+50 XP'));
    expect(vi.mocked(progressionApi.claim)).toHaveBeenCalledWith('q1');
  });

  it('서버가 XP 를 늦게 반영하므로 잠깐 동안 진행을 짧은 주기로 다시 읽고 레벨 업을 알린다', async () => {
    const { wrapper, stale } = serverState([[progressKeys.progress(), { xp: 0 }]]);
    const { result } = renderHook(() => useClaimQuest(), { wrapper });
    await act(() => result.current.mutateAsync('q1'));
    expect(stale(progressKeys.progress())).toBe(true);
    expect(settleInterval()).toBe(SETTLE_INTERVAL_MS);
    expect(useSyncStore.getState().announce).toBe(true);
  });

  it('예시 데이터를 보는 중에는 레벨 업을 알리지 않는다', async () => {
    useUiStore.setState({ sampleMode: true });
    const { result } = renderHook(() => useClaimQuest(), { wrapper: serverState().wrapper });
    await act(() => result.current.mutateAsync('q1'));
    expect(useSyncStore.getState().announce).toBe(false);
  });
});
