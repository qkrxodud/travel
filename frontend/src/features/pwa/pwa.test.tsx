/**
 * PWA·웹 푸시(12단계) — 첫 체크인 뒤에만 "알림 받을래요?"를 묻고, 예일 때만 브라우저 권한을 묻는다. 두 번 이상 온 사람에게 홈 화면 설치를
 * 권하고, 프로필 탭에서 종류별로 끄거나 이 기기 알림을 모두 끈다(= 구독 해지). 알림 규칙(하루 1개·조용한 시간)은 서버가 정한 값 그대로 보인다.
 * 이야기 순서: 알림 질문 → 예를 누른 뒤 → 질문 화면 → 다시 열 때 → 홈 화면 설치 → 알림 설정.
 */
import { act, cleanup, fireEvent, render, renderHook, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { track } from '../../api/analytics';
import { notificationApi } from '../../api/notification';
import type { PreferencesResponse, SubscriptionRequest } from '../../api/types/notification';
import { pushBrowser } from '../../shared/lib/pwa/pushBrowser';
import { useMyTerritory } from '../../shared/queries/territory';
import { EMPTY_PROMPT, usePwaStore, type PromptMemory } from '../../store/pwaStore';
import { useUiStore } from '../../store/uiStore';
import { serverState } from '../../test/serverState';
import { NotificationSettingsCard } from './components/NotificationSettingsCard';
import { PushPrompt } from './components/PushPrompt';
import { coolingDown, consentStep, dismissed, type ConsentInput } from './model/consent';
import { installOffer, recordVisit, type InstallInput } from './model/install';
import { deviceStatus, devicesText, kindRows, rulesText, toggled } from './model/settings';
import { pushKeys, useEnablePush, useSubscriptionResync } from './queries';

vi.mock('../../api/analytics', () => ({ track: vi.fn(), errorToastCode: vi.fn(() => 'INVALID_PUSH_SUBSCRIPTION') }));
vi.mock('../../api/notification', () => ({
  notificationApi: {
    vapidPublicKey: vi.fn(async () => ({ publicKey: 'BKEY' })),
    subscribe: vi.fn(async () => ({ created: true, devices: 1, evicted: 0 })),
    unsubscribe: vi.fn(async () => null),
    preferences: vi.fn(),
    savePreferences: vi.fn(),
  },
}));
vi.mock('../../shared/lib/pwa/pushBrowser', () => ({
  pushBrowser: {
    support: vi.fn(() => 'supported'),
    permission: vi.fn(() => 'default'),
    requestPermission: vi.fn(async () => 'granted'),
    current: vi.fn(async () => null),
    subscribe: vi.fn(),
    unsubscribe: vi.fn(async () => 'https://fcm.googleapis.com/fcm/send/abc'),
  },
}));
vi.mock('../../shared/queries/territory', () => ({ useMyTerritory: vi.fn() }));
vi.mock('../../store/toastStore', () => ({ toast: vi.fn(), toastError: vi.fn() }));

const NOW = new Date('2026-10-05T03:00:00Z');
const DAY_MS = 24 * 60 * 60 * 1000;
const SUBSCRIPTION: SubscriptionRequest = { endpoint: 'https://fcm.googleapis.com/fcm/send/abc', expirationTime: null, keys: { p256dh: 'B'.repeat(87), auth: 'a'.repeat(22) } };
const PREFERENCES: PreferencesResponse = {
  mystery: true, streak: true, season: true, devices: 1, quietHours: { start: '22:00', end: '08:00', timeZone: 'Asia/Seoul' }, dailyLimit: 1,
};

const ask = (overrides: Partial<ConsentInput> = {}): ConsentInput => ({
  checkedIn: true, support: 'supported', permission: 'default', subscribed: false, memory: EMPTY_PROMPT, now: NOW, ...overrides,
});
const daysAgo = (days: number) => new Date(NOW.getTime() - days * DAY_MS).toISOString();

function paintedRegions(count: number) {
  const visits = new Map(Array.from({ length: count }, (_unused, index) => [`1101${index}`, {}]));
  vi.mocked(useMyTerritory).mockReturnValue({ visits } as unknown as ReturnType<typeof useMyTerritory>);
}

beforeEach(() => {
  usePwaStore.setState({ prompt: EMPTY_PROMPT, visits: { count: 1, lastSeenAt: null }, installDismissedAt: null });
  useUiStore.setState({ sampleMode: false, tab: 'map' });
  vi.mocked(pushBrowser.support).mockReturnValue('supported');
  vi.mocked(pushBrowser.permission).mockReturnValue('default');
  vi.mocked(pushBrowser.requestPermission).mockResolvedValue('granted');
  vi.mocked(pushBrowser.current).mockResolvedValue(null);
  vi.mocked(pushBrowser.subscribe).mockResolvedValue(SUBSCRIPTION);
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('"알림 받을래요?"를 언제 묻는지', () => {
  it('첫 체크인 전에는 묻지 않고, 칠한 곳이 생기면 묻는다', () => {
    expect(consentStep(ask({ checkedIn: false }))).toBe('hidden');
    expect(consentStep(ask())).toBe('ask');
  });

  it('이 브라우저가 이미 알림을 받고 있거나 아직 확인 중이면 묻지 않는다', () => {
    expect(consentStep(ask({ subscribed: true }))).toBe('hidden');
    expect(consentStep(ask({ subscribed: null }))).toBe('hidden');
  });

  it('브라우저에서 알림을 막았거나 이미 답했으면 다시 묻지 않는다', () => {
    expect(consentStep(ask({ permission: 'denied' }))).toBe('hidden');
    expect(consentStep(ask({ memory: { ...EMPTY_PROMPT, answer: 'granted' } }))).toBe('hidden');
    expect(consentStep(ask({ memory: { ...EMPTY_PROMPT, answer: 'denied' } }))).toBe('hidden');
  });

  it('알림을 못 받는 브라우저에는 묻지 않는다', () => {
    expect(consentStep(ask({ support: 'unsupported' }))).toBe('hidden');
  });

  it('iPhone 사파리 탭에서는 권한을 묻는 대신 홈 화면에 추가하라고 조용히 한 번 알려 준다', () => {
    expect(consentStep(ask({ support: 'ios-needs-install', subscribed: false }))).toBe('ios-install');
    expect(consentStep(ask({ support: 'ios-needs-install', memory: dismissed(EMPTY_PROMPT, NOW) }))).toBe('hidden');
  });

  it('"나중에"를 누르면 30일 동안 묻지 않고, 그 뒤 한 번 더 묻는다', () => {
    const once = dismissed(EMPTY_PROMPT, new Date(daysAgo(29)));
    expect(once).toEqual({ answer: 'dismissed', dismissals: 1, dismissedAt: daysAgo(29) });
    expect(consentStep(ask({ memory: once }))).toBe('hidden');
    expect(consentStep(ask({ memory: { ...once, dismissedAt: daysAgo(30) } }))).toBe('ask');
  });

  it('두 번 미루면 더는 화면에서 묻지 않는다(프로필 탭에서 켤 수 있다)', () => {
    const twice: PromptMemory = { answer: 'dismissed', dismissals: 2, dismissedAt: daysAgo(400) };
    expect(coolingDown(twice, NOW)).toBe(true);
    expect(consentStep(ask({ memory: twice }))).toBe('hidden');
  });
});

describe('질문에 예라고 답하면', () => {
  it('그때 처음 브라우저 권한을 묻고, 허용하면 서버 공개 키로 구독해 이 브라우저를 등록한다', async () => {
    const { wrapper } = serverState();
    const { result } = renderHook(() => useEnablePush(), { wrapper });
    let outcome: string | undefined;
    await act(async () => {
      outcome = await result.current.mutateAsync('prompt');
    });
    expect(outcome).toBe('subscribed');
    const order = [vi.mocked(pushBrowser.requestPermission), vi.mocked(notificationApi.vapidPublicKey), vi.mocked(pushBrowser.subscribe)]
      .map(mock => mock.mock.invocationCallOrder[0]);
    expect(order).toEqual([...order].sort((first, second) => (first ?? 0) - (second ?? 0)));
    expect(pushBrowser.subscribe).toHaveBeenCalledWith('BKEY');
    expect(notificationApi.subscribe).toHaveBeenCalledWith(SUBSCRIPTION);
    expect(track).toHaveBeenCalledWith('push_prompt', { result: 'granted' });
    expect(usePwaStore.getState().prompt.answer).toBe('granted');
  });

  it('브라우저 권한을 거절하면 구독하지 않고 거절했다고만 남긴다', async () => {
    vi.mocked(pushBrowser.requestPermission).mockResolvedValue('denied');
    const { wrapper } = serverState();
    const { result } = renderHook(() => useEnablePush(), { wrapper });
    await act(async () => {
      expect(await result.current.mutateAsync('prompt')).toBe('denied');
    });
    expect(pushBrowser.subscribe).not.toHaveBeenCalled();
    expect(notificationApi.subscribe).not.toHaveBeenCalled();
    expect(track).toHaveBeenCalledWith('push_prompt', { result: 'denied' });
  });

  it('프로필 탭에서 켠 것은 질문에 대한 답이 아니라 동의율에 세지 않는다', async () => {
    const { wrapper } = serverState();
    const { result } = renderHook(() => useEnablePush(), { wrapper });
    await act(async () => {
      await result.current.mutateAsync('settings');
    });
    expect(track).not.toHaveBeenCalled();
    expect(notificationApi.subscribe).toHaveBeenCalled();
  });
});

describe('지도 탭의 알림 질문', () => {
  it('칠한 곳이 없으면 보이지 않는다', async () => {
    paintedRegions(0);
    const { wrapper } = serverState();
    render(<PushPrompt />, { wrapper });
    await act(async () => undefined);
    expect(document.getElementById('push-prompt')).toBeNull();
    expect(track).not.toHaveBeenCalled();
  });

  it('첫 체크인 뒤 나타나고 한 번 보였다고 남긴다 — 이때는 아직 브라우저 권한을 묻지 않는다', async () => {
    paintedRegions(1);
    const { wrapper } = serverState();
    render(<PushPrompt />, { wrapper });
    expect(await screen.findByText('🔔 알림 받을래요?')).toBeTruthy();
    expect(track).toHaveBeenCalledWith('push_prompt', { result: 'shown' });
    expect(pushBrowser.requestPermission).not.toHaveBeenCalled();
  });

  it('예시 데이터만 칠해져 있으면 묻지 않는다', async () => {
    paintedRegions(45);
    useUiStore.setState({ sampleMode: true });
    const { wrapper } = serverState();
    render(<PushPrompt />, { wrapper });
    await act(async () => undefined);
    expect(document.getElementById('push-prompt')).toBeNull();
  });

  it('"나중에"를 누르면 사라지고 미뤘다고 남긴다', async () => {
    paintedRegions(1);
    const { wrapper } = serverState();
    render(<PushPrompt />, { wrapper });
    fireEvent.click(await screen.findByText('나중에'));
    expect(document.getElementById('push-prompt')).toBeNull();
    expect(track).toHaveBeenCalledWith('push_prompt', { result: 'dismissed' });
    expect(usePwaStore.getState().prompt).toMatchObject({ answer: 'dismissed', dismissals: 1 });
  });

  it('예를 눌러 켜지면 켰다고 알려 주고, 닫으면 다시 묻지 않는다', async () => {
    paintedRegions(1);
    const { wrapper } = serverState();
    render(<PushPrompt />, { wrapper });
    fireEvent.click(await screen.findByText('예'));
    expect(await screen.findByText('알림을 켰어요')).toBeTruthy();
    fireEvent.click(screen.getByText('닫기'));
    expect(document.getElementById('push-prompt')).toBeNull();
  });

  it('서버가 이 브라우저 구독을 받지 않으면 "이 브라우저에서는 알림을 켤 수 없어요"로 알리고 오류 코드만 남긴다', async () => {
    paintedRegions(1);
    vi.mocked(notificationApi.subscribe).mockRejectedValueOnce(Object.assign(new Error('bad'), { code: 'INVALID_PUSH_SUBSCRIPTION' }));
    const { wrapper } = serverState();
    render(<PushPrompt />, { wrapper });
    fireEvent.click(await screen.findByText('예'));
    expect(await screen.findByText('이 브라우저에서는 알림을 켤 수 없어요')).toBeTruthy();
    expect(track).toHaveBeenCalledWith('error_toast', { code: 'INVALID_PUSH_SUBSCRIPTION' });
  });
});

describe('앱을 다시 열 때', () => {
  it('이미 허용·구독된 브라우저면 같은 구독을 서버에 다시 보낸다(로그인으로 탐험가가 바뀐 브라우저를 옮긴다)', async () => {
    vi.mocked(pushBrowser.permission).mockReturnValue('granted');
    vi.mocked(pushBrowser.current).mockResolvedValue(SUBSCRIPTION);
    const { wrapper } = serverState();
    renderHook(() => useSubscriptionResync(), { wrapper });
    await waitFor(() => expect(notificationApi.subscribe).toHaveBeenCalledWith(SUBSCRIPTION));
  });

  it('권한이 없거나 구독이 없으면 아무것도 보내지 않는다(묻지도 않는다)', async () => {
    const { wrapper } = serverState();
    renderHook(() => useSubscriptionResync(), { wrapper });
    await act(async () => undefined);
    vi.mocked(pushBrowser.permission).mockReturnValue('granted');
    renderHook(() => useSubscriptionResync(), { wrapper });
    await act(async () => undefined);
    expect(notificationApi.subscribe).not.toHaveBeenCalled();
    expect(pushBrowser.requestPermission).not.toHaveBeenCalled();
  });
});

describe('홈 화면 설치 권유', () => {
  const offer = (overrides: Partial<InstallInput> = {}) => installOffer({
    visits: 2, checkedIn: true, standalone: false, installed: false, ios: false, canPrompt: true, dismissedAt: null, now: NOW, ...overrides,
  });

  it('30분 넘게 쉬었다 다시 열면 새 방문으로 센다', () => {
    const first = recordVisit({ count: 0, lastSeenAt: null }, NOW);
    expect(first.count).toBe(1);
    expect(recordVisit(first, new Date(NOW.getTime() + 29 * 60 * 1000)).count).toBe(1);
    expect(recordVisit(first, new Date(NOW.getTime() + 30 * 60 * 1000)).count).toBe(2);
  });

  it('두 번 이상 왔고 첫 체크인을 했을 때만 권한다', () => {
    expect(offer()).toBe('prompt');
    expect(offer({ visits: 1 })).toBe('none');
    expect(offer({ checkedIn: false })).toBe('none');
  });

  it('닫으면 14일 동안 숨긴다', () => {
    expect(offer({ dismissedAt: daysAgo(13) })).toBe('none');
    expect(offer({ dismissedAt: daysAgo(14) })).toBe('prompt');
  });

  it('이미 홈 화면 앱으로 열었거나 방금 설치했으면 권하지 않는다', () => {
    expect(offer({ standalone: true })).toBe('none');
    expect(offer({ installed: true })).toBe('none');
  });

  it('브라우저 설치 창이 없으면 iPhone 에는 "홈 화면에 추가" 안내를, 그 밖에는 아무것도 보이지 않는다', () => {
    expect(offer({ canPrompt: false, ios: true })).toBe('ios-guide');
    expect(offer({ canPrompt: false })).toBe('none');
  });
});

describe('프로필 탭 알림 설정', () => {
  it('이 기기 상태를 지원·홈 화면 필요·막힘·꺼짐·켜짐으로 나눈다', () => {
    expect(deviceStatus('unsupported', 'unsupported', false)).toBe('unsupported');
    expect(deviceStatus('ios-needs-install', 'unsupported', false)).toBe('ios-install');
    expect(deviceStatus('supported', 'denied', false)).toBe('blocked');
    expect(deviceStatus('supported', 'default', false)).toBe('off');
    expect(deviceStatus('supported', 'granted', false)).toBe('off');
    expect(deviceStatus('supported', 'granted', true)).toBe('on');
  });

  it('세 종류를 미스터리·스트릭·계절 순서로, 서버 설정 그대로 보인다', () => {
    expect(kindRows({ mystery: true, streak: false, season: true }).map(row => [row.kind, row.label, row.on])).toEqual([
      ['mystery', '이번 주 미스터리 지역', true], ['streak', '스트릭 지키기', false], ['season', '계절 테마 시작', true],
    ]);
  });

  it('한 종류만 바꿔도 세 값을 모두 보낸다(서버가 셋 다 요구한다)', () => {
    expect(toggled(PREFERENCES, 'streak', false)).toEqual({ mystery: true, streak: false, season: true });
  });

  it('조용한 시간과 하루 최대 개수는 서버 값으로 적는다', () => {
    expect(rulesText(PREFERENCES)).toBe('22:00~08:00에는 보내지 않고, 하루에 1개까지만 보내요.');
    expect(rulesText({ ...PREFERENCES, quietHours: { start: '23:00', end: '07:00', timeZone: 'Asia/Seoul' }, dailyLimit: 2 }))
      .toBe('23:00~07:00에는 보내지 않고, 하루에 2개까지만 보내요.');
  });

  it('알림 받는 기기가 없으면 설정만으로는 알림이 오지 않는다고 알린다', () => {
    expect(devicesText(0)).toBe('알림 받는 기기가 없어요 — 설정만으로는 알림이 오지 않아요');
    expect(devicesText(2)).toBe('알림 받는 기기 2대');
  });

  it('이 기기가 아직 알림을 받지 않으면 "이 기기에서 알림 켜기"를 보이고, 누르면 구독한다', async () => {
    useUiStore.setState({ tab: 'profile' });
    vi.mocked(notificationApi.preferences).mockResolvedValue({ ...PREFERENCES, devices: 0 });
    const { wrapper } = serverState();
    render(<NotificationSettingsCard />, { wrapper });
    expect((await screen.findByText(/알림 받는 기기가 없어요/)).textContent).toContain('22:00~08:00');
    await act(async () => {
      fireEvent.click(screen.getByText('이 기기에서 알림 켜기'));
    });
    await waitFor(() => expect(notificationApi.subscribe).toHaveBeenCalledWith(SUBSCRIPTION));
  });

  it('종류 하나를 끄면 그 종류만 꺼진 설정을 저장한다', async () => {
    useUiStore.setState({ tab: 'profile' });
    vi.mocked(pushBrowser.permission).mockReturnValue('granted');
    vi.mocked(pushBrowser.current).mockResolvedValue(SUBSCRIPTION);
    vi.mocked(notificationApi.savePreferences).mockResolvedValue({ ...PREFERENCES, mystery: false });
    const { wrapper } = serverState([[pushKeys.preferences(), PREFERENCES]]);
    vi.mocked(notificationApi.preferences).mockResolvedValue(PREFERENCES);
    render(<NotificationSettingsCard />, { wrapper });
    const mystery = await waitFor(() => {
      const box = document.querySelector<HTMLInputElement>('input[data-kind="mystery"]');
      if (!box) throw new Error('아직 없음');
      return box;
    });
    await act(async () => {
      fireEvent.click(mystery);
    });
    expect(notificationApi.savePreferences).toHaveBeenCalledWith({ mystery: false, streak: true, season: true });
    await waitFor(() => expect(document.querySelector<HTMLInputElement>('input[data-kind="mystery"]')?.checked).toBe(false));
  });

  it('"이 기기 알림 모두 끄기"는 브라우저 구독을 풀고 서버에서도 이 기기를 지운다', async () => {
    useUiStore.setState({ tab: 'profile' });
    vi.mocked(pushBrowser.permission).mockReturnValue('granted');
    vi.mocked(pushBrowser.current).mockResolvedValue(SUBSCRIPTION);
    vi.mocked(notificationApi.preferences).mockResolvedValue(PREFERENCES);
    const { wrapper } = serverState();
    render(<NotificationSettingsCard />, { wrapper });
    const off = await screen.findByText('이 기기 알림 모두 끄기');
    await act(async () => {
      fireEvent.click(off);
    });
    expect(pushBrowser.unsubscribe).toHaveBeenCalled();
    expect(notificationApi.unsubscribe).toHaveBeenCalledWith(SUBSCRIPTION.endpoint);
  });

  it('브라우저에서 알림이 막혀 있으면 켜기 버튼 대신 사이트 설정 안내를 보인다', async () => {
    useUiStore.setState({ tab: 'profile' });
    vi.mocked(pushBrowser.permission).mockReturnValue('denied');
    vi.mocked(notificationApi.preferences).mockResolvedValue(PREFERENCES);
    const { wrapper } = serverState();
    render(<NotificationSettingsCard />, { wrapper });
    expect((await screen.findByText(/사이트 설정에서 허용하면/)).textContent).toContain('막혀 있어요');
    expect(screen.queryByText('이 기기에서 알림 켜기')).toBeNull();
  });
});
