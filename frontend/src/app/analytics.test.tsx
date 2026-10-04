/**
 * 화면 이벤트를 어디서 남기는지 — 첫 화면은 한 번, 탭은 바뀔 때마다, 오류 토스트는 코드만.
 * (체크인 모달 열기·저장·취소와 공유 버튼은 E2E analytics.spec 이 실제 전송 본문으로 확인한다)
 */
import { cleanup, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { analytics } from '../api/analytics';
import { ApiError } from '../api/client';
import { toastError, useToastStore } from '../store/toastStore';
import { useUiStore } from '../store/uiStore';
import { useAnalytics } from './useAnalytics';

let tracked: unknown[][];

beforeEach(() => {
  tracked = [];
  vi.spyOn(analytics, 'track').mockImplementation((...args: unknown[]) => void tracked.push(args));
  useUiStore.setState({ tab: 'map' });
});

afterEach(() => {
  cleanup();
  useToastStore.setState({ toasts: [] });
});

describe('앱을 열면', () => {
  it('첫 화면 노출을 들어온 갈래와 함께 한 번 남기고, 처음 보이는 탭을 남긴다', () => {
    renderHook(() => useAnalytics());
    expect(tracked).toEqual([['app_open', { entry: 'direct' }], ['tab_view', { tab: 'map' }]]);
  });

  it('같은 페이지에서 화면이 다시 그려져도 첫 화면 노출은 다시 남기지 않는다', () => {
    renderHook(() => useAnalytics());
    expect(tracked.map(([name]) => name)).toEqual(['tab_view']);
  });
});

describe('탭을 옮기면', () => {
  it('바뀐 탭만 남기고, 같은 탭을 다시 누른 것은 남기지 않는다', () => {
    renderHook(() => useAnalytics());
    tracked = [];
    useUiStore.getState().setTab('bag');
    useUiStore.getState().setTab('bag');
    useUiStore.getState().showOnMap('11010');
    expect(tracked).toEqual([['tab_view', { tab: 'bag' }], ['tab_view', { tab: 'map' }]]);
  });

  it('화면을 떠나면 더는 남기지 않는다', () => {
    const { unmount } = renderHook(() => useAnalytics());
    unmount();
    tracked = [];
    useUiStore.getState().setTab('quests');
    expect(tracked).toEqual([]);
  });
});

describe('오류 토스트가 보이면', () => {
  it('서버 오류 코드만 남기고 메시지 문장은 남기지 않는다', () => {
    toastError(new ApiError(409, 'DUPLICATE_VISIT', '이미 칠한 곳이에요 — 메모: 물회'));
    expect(tracked).toEqual([['error_toast', { code: 'DUPLICATE_VISIT' }]]);
  });

  it('서버에 닿지 못한 실패는 NETWORK_ERROR 로 남긴다', () => {
    toastError(new TypeError('Failed to fetch'));
    expect(tracked).toEqual([['error_toast', { code: 'NETWORK_ERROR' }]]);
  });
});
