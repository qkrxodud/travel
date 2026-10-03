/**
 * 화면 상태 — 알림(토스트), 서버가 늦게 반영하는 동안의 다시 읽기, 지금 보는 탭·지도·고른 지역. 서버 값은 여기 두지 않는다.
 * 이야기 순서: 알림 → 늦은 반영 기다리기 → 지금 보는 화면.
 */
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/client';
import { settleInterval, SETTLE_INTERVAL_MS, SETTLE_WINDOW_MS, territorySettleInterval, useSyncStore } from './syncStore';
import { errorTitle, toast, toastError, useToastStore } from './toastStore';
import { useUiStore } from './uiStore';

afterEach(() => {
  vi.useRealTimers();
  useToastStore.setState({ toasts: [] });
  useSyncStore.setState({ settleUntil: 0, territoryUntil: 0, announce: false });
  useUiStore.setState({ tab: 'map', mapId: null, selected: null, highlight: null, bagFilter: 'all' });
});

describe('알림', () => {
  it('알림은 3.2초 동안 보였다가 사라진다', () => {
    vi.useFakeTimers();
    toast('✓', '영토에서 제거', '종로구');
    expect(useToastStore.getState().toasts).toMatchObject([{ icon: '✓', title: '영토에서 제거', sub: '종로구' }]);
    vi.advanceTimersByTime(3199);
    expect(useToastStore.getState().toasts).toHaveLength(1);
    vi.advanceTimersByTime(1);
    expect(useToastStore.getState().toasts).toHaveLength(0);
  });

  it('실패하면 오류 종류에 맞는 제목과 서버 안내 문구로 알린다', () => {
    toastError(new ApiError(422, 'DAILY_CAP_EXCEEDED', '하루 5곳까지 칠할 수 있어요'));
    expect(useToastStore.getState().toasts[0]).toMatchObject({ icon: '!', title: '오늘은 여기까지', sub: '하루 5곳까지 칠할 수 있어요' });
  });

  it('화면이 따로 정한 안내 문구가 있으면 그것을 보여 준다', () => {
    toastError(new ApiError(404, 'PROFILE_NOT_FOUND', '없음'), '비교할 수 없어요');
    expect(useToastStore.getState().toasts[0]).toMatchObject({ title: '프로필 없음', sub: '비교할 수 없어요' });
  });

  it('제목을 정하지 않은 오류는 요청 실패라고 부른다', () => {
    expect(errorTitle('HTTP_500')).toBe('요청 실패');
    expect(errorTitle(undefined)).toBe('요청 실패');
  });
});

describe('늦은 반영 기다리기', () => {
  it('변경 직후 4.5초 동안만 진행·도감·가방을 0.5초마다 다시 읽는다', () => {
    vi.useFakeTimers();
    useSyncStore.getState().begin(true);
    expect(settleInterval()).toBe(SETTLE_INTERVAL_MS);
    vi.advanceTimersByTime(SETTLE_WINDOW_MS);
    expect(settleInterval()).toBe(false);
  });

  it('평소에는 영토를 주기적으로 다시 읽지 않고, 로그인으로 방문을 옮기는 동안만 1초마다 읽는다', () => {
    vi.useFakeTimers();
    expect(territorySettleInterval()).toBe(false);
    useSyncStore.getState().beginTerritory();
    expect(territorySettleInterval()).toBe(SETTLE_INTERVAL_MS * 2);
    expect(useSyncStore.getState().announce).toBe(false);
    vi.advanceTimersByTime(SETTLE_WINDOW_MS);
    expect(territorySettleInterval()).toBe(false);
  });
});

describe('지금 보는 화면', () => {
  it('다른 지도로 옮기면 고른 지역과 강조를 지우고, 다음에 열어도 그 지도를 기억한다', () => {
    useUiStore.setState({ selected: '11010', highlight: '11010' });
    useUiStore.getState().setMapId('crew');
    expect(useUiStore.getState()).toMatchObject({ mapId: 'crew', selected: null, highlight: null });
    expect(localStorage.getItem('territory-map-id')).toBe('crew');
    useUiStore.getState().setMapId(null);
    expect(localStorage.getItem('territory-map-id')).toBeNull();
  });

  it('지역을 새로 고르면 이전 강조는 지운다', () => {
    useUiStore.setState({ highlight: '37430' });
    useUiStore.getState().select('11010');
    expect(useUiStore.getState()).toMatchObject({ selected: '11010', highlight: null });
  });

  it('탭을 바꾸면 주소에도 남겨 새로고침해도 같은 탭이 열린다', () => {
    useUiStore.getState().setTab('quests');
    expect(useUiStore.getState().tab).toBe('quests');
    expect(location.hash).toBe('#quests');
  });

  it('가방에서 고른 칸은 다음에 열어도 기억한다', () => {
    useUiStore.getState().setBagFilter('hat');
    expect(JSON.parse(localStorage.getItem('territory-mvp-v3') ?? '{}')).toEqual({ bagFilter: 'hat' });
  });
});
