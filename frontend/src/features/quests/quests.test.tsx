/**
 * 퀘스트 탭 — 달마다 이어 가는 탐험 스트릭과, 달성한 퀘스트의 보상 받기. 진행 수·달성·받음 여부는 서버 값이다.
 * 이야기 순서: 스트릭 띠 → 스트릭 안내 → 퀘스트 한 줄 → 보상 받기.
 */
import { act, cleanup, fireEvent, render, renderHook, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { progressionApi } from '../../api/progression';
import type { QuestResponse } from '../../api/types/progression';
import { progressKeys } from '../../shared/queries/progress';
import { settleInterval, SETTLE_INTERVAL_MS, useSyncStore } from '../../store/syncStore';
import { useToastStore } from '../../store/toastStore';
import { useUiStore } from '../../store/uiStore';
import { serverState } from '../../test/serverState';
import { QuestRow } from './components/QuestRow';
import { monthShift, percentOf, streakRun, streakStrip, streakText } from './model/streak';
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

describe('스트릭 띠', () => {
  const today = new Date(2026, 9, 3); // 2026-10-03

  it('최근 12개월을 오래된 달부터 늘어놓고 이번 달을 표시한다', () => {
    const cells = streakStrip({ months: 2, lastMonth: '2026-10', activeThisMonth: true }, today, '2026-10');
    expect(cells).toHaveLength(12);
    expect(cells[0]).toMatchObject({ month: '2025-11', label: '11월', on: false, now: false });
    expect(cells[11].now).toBe(true);
  });

  it('서버가 알려 준 마지막 달에서 끝나는 n개월을 연속 구간으로 칠한다', () => {
    const cells = streakStrip({ months: 2, lastMonth: '2026-10', activeThisMonth: true }, today, '2026-10');
    expect(cells.slice(-3).map(cell => cell.on)).toEqual([false, true, true]);
  });

  it('연속 구간은 해를 넘어 이어지고, 스트릭이 없으면 비어 있다', () => {
    expect([...streakRun({ months: 3, lastMonth: '2026-01', activeThisMonth: false })]).toEqual(['2026-01', '2025-12', '2025-11']);
    expect(streakRun({ months: 0, lastMonth: null, activeThisMonth: false }).size).toBe(0);
  });

  it('달 이동은 해를 넘어도 맞는 달을 가리킨다', () => {
    expect(monthShift(today, 0)).toBe('2026-10');
    expect(monthShift(today, -11)).toBe('2025-11');
    expect(monthShift(today, 3)).toBe('2027-01');
  });
});

describe('스트릭 안내', () => {
  it('스트릭이 없으면 이번 달 1곳으로 시작된다고 알려 준다', () => {
    expect(streakText({ months: 0, lastMonth: null, activeThisMonth: false })).toContain('스트릭이 시작돼요');
  });

  it('이번 달에도 칠했으면 연속 개월 수와 유지 조건을 알려 준다', () => {
    expect(streakText({ months: 4, lastMonth: '2026-10', activeThisMonth: true })).toBe('4개월 연속 탐험 중. 다음 달에도 1곳이면 유지됩니다.');
  });

  it('이번 달 아직 안 칠했으면 끊기기 전에 1곳 칠하라고 알려 준다', () => {
    expect(streakText({ months: 4, lastMonth: '2026-09', activeThisMonth: false })).toContain('이번 달 아직 0곳');
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
