/**
 * 웹 푸시(12단계) 쿼리·흐름. 서버 상태(설정)는 TanStack Query, 이 브라우저의 구독 여부도 비동기 값이라 같은 키 팩토리로 둔다.
 *
 * 켜기 흐름(계약 §0): 브라우저 권한 → (허용) 공개 키 → pushManager.subscribe → POST /push/subscriptions.
 * 앱을 열 때마다: 권한이 있고 구독이 있으면 같은 구독을 다시 보낸다(멱등 — 키 갱신·로그인으로 탐험가가 바뀐 브라우저 옮기기).
 */
import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { track } from '../../api/analytics';
import { apiClient } from '../../api/client';
import { notificationApi } from '../../api/notification';
import type { PreferencesRequest, PreferencesResponse } from '../../api/types/notification';
import { pushBrowser } from '../../shared/lib/pwa/pushBrowser';
import { serviceWorkerClient } from '../../shared/lib/pwa/serviceWorkerClient';
import { useMyTerritory } from '../../shared/queries/territory';
import { usePwaStore } from '../../store/pwaStore';
import { useUiStore } from '../../store/uiStore';
import { answered, type EnableOutcome } from './model/consent';
import { recordVisit } from './model/install';

export const pushKeys = {
  all: () => ['push'] as const,
  preferences: () => ['push', 'preferences'] as const,
  /** 이 브라우저의 구독 주소(서버가 아닌 브라우저 값) */
  device: () => ['push', 'device'] as const,
};

/** 이 브라우저가 구독돼 있는지(구독 주소 또는 null) */
export function useDeviceSubscription(enabled: boolean) {
  return useQuery({
    queryKey: pushKeys.device(),
    queryFn: async () => (await pushBrowser.current())?.endpoint ?? null,
    enabled,
    staleTime: Infinity,
    meta: { silent: true },
  });
}

/** GET /push/preferences — 프로필 탭을 열 때 */
export function usePreferences(enabled: boolean) {
  return useQuery({ queryKey: pushKeys.preferences(), queryFn: notificationApi.preferences, enabled, staleTime: 0, meta: { silent: true } });
}

export function useSavePreferences() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (preferences: PreferencesRequest) => notificationApi.savePreferences(preferences),
    onSuccess: saved => queryClient.setQueryData<PreferencesResponse>(pushKeys.preferences(), saved),
    onError: () => queryClient.invalidateQueries({ queryKey: pushKeys.preferences() }),
  });
}

async function refreshPush(queryClient: QueryClient): Promise<void> {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: pushKeys.device() }),
    queryClient.invalidateQueries({ queryKey: pushKeys.preferences() }),
  ]);
}

/**
 * 이 기기에서 알림 켜기. from = 'prompt'(화면 질문 "알림 받을래요?"의 예)면 브라우저 답을 push_prompt 로 남긴다
 * (동의율 = granted ÷ shown — 프로필 탭에서 켠 것은 질문에 대한 답이 아니라 세지 않는다).
 */
export function useEnablePush() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (from: 'prompt' | 'settings'): Promise<EnableOutcome> => {
      const permission = await pushBrowser.requestPermission();
      const granted = permission === 'granted';
      if (from === 'prompt') {
        const store = usePwaStore.getState();
        store.setPrompt(answered(store.prompt, granted ? 'granted' : 'denied'));
        track('push_prompt', { result: granted ? 'granted' : 'denied' });
      }
      if (!granted) return 'denied';
      const { publicKey } = await notificationApi.vapidPublicKey();
      const subscription = await pushBrowser.subscribe(publicKey);
      await notificationApi.subscribe(subscription);
      return 'subscribed';
    },
    onSettled: () => refreshPush(queryClient),
  });
}

/** 알림 모두 끄기 = 이 브라우저 구독 해지(브라우저 + 서버) */
export function useDisablePush() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      const endpoint = await pushBrowser.unsubscribe();
      if (endpoint) await notificationApi.unsubscribe(endpoint);
    },
    onSettled: () => refreshPush(queryClient),
  });
}

/** 첫 체크인을 했는지 — 지금 지도에 내가 칠한 곳이 한 곳이라도 있으면(예시 데이터는 빼고) */
export function useCheckedIn(): boolean {
  const { visits } = useMyTerritory();
  const sample = useUiStore(state => state.sampleMode);
  return !sample && !!visits && visits.size > 0;
}

/** 방문 기록(설치 배너 조건) — 화면을 열 때와 다시 볼 때 */
export function useVisitLog(): void {
  useEffect(() => {
    const touch = () => {
      const store = usePwaStore.getState();
      store.setVisits(recordVisit(store.visits, new Date()));
    };
    touch();
    const onVisible = () => {
      if (document.visibilityState === 'visible') touch();
    };
    document.addEventListener('visibilitychange', onVisible);
    return () => document.removeEventListener('visibilitychange', onVisible);
  }, []);
}

/**
 * 앱을 열 때·탐험가가 바뀔 때(로그아웃·새 발급)·서비스워커가 구독이 바뀌었다고 알릴 때: 이미 허용·구독된 브라우저면 같은 구독을
 * 서버에 다시 보낸다(조용히 — 서버가 이 브라우저를 지금 탐험가로 옮긴다).
 */
export function useSubscriptionResync(): void {
  const queryClient = useQueryClient();
  useEffect(() => {
    const resync = async () => {
      if (pushBrowser.support() !== 'supported' || pushBrowser.permission() !== 'granted') return;
      try {
        const subscription = await pushBrowser.current();
        if (subscription) await notificationApi.subscribe(subscription);
      } catch {
        // 다음에 앱을 열 때 다시 보낸다
      }
      await refreshPush(queryClient);
    };
    void resync();
    const stopWorker = serviceWorkerClient.onSubscriptionChange(() => void resync());
    const stopIdentity = apiClient.onIdentityReset(() => void resync());
    return () => {
      stopWorker();
      stopIdentity();
    };
  }, [queryClient]);
}
