/**
 * 비동기 반영(outbox 릴레이) 대기 창. 체크인·취소·보상 받기처럼 서버가 이벤트로 늦게 반영하는 변경 직후,
 * 진행·도감·퀘스트·가방·장면 쿼리를 잠깐 짧은 주기로 다시 읽는다(refetchInterval) — setTimeout 체인으로 화면을 다시 그리지 않는다.
 * 이 쿼리들의 키는 모두 SETTLED_ROOT 로 시작한다(한 번에 무효화).
 */
import type { QueryClient } from '@tanstack/react-query';
import { create } from 'zustand';

export const SETTLED_ROOT = 'settled' as const;
/** 반영 대기 창 길이(로컬 릴레이 주기 1초 + 재시도 여유 — 프로토타입 0.3~4초 재조회와 같은 폭) */
export const SETTLE_WINDOW_MS = 4500;
export const SETTLE_INTERVAL_MS = 500;

interface SyncState {
  settleUntil: number;
  /** 영토도 다시 읽어야 하는 반영(로그인 병합 — 방문 이동이 비동기) */
  territoryUntil: number;
  /** 이번 변경으로 새 뱃지·레벨 업·세트 배경을 알릴지(예시 채우기·전부 지우기는 알리지 않는다) */
  announce: boolean;
  begin: (announce: boolean) => void;
  beginTerritory: () => void;
}

export const useSyncStore = create<SyncState>()(set => ({
  settleUntil: 0,
  territoryUntil: 0,
  announce: false,
  begin: announce => set({ settleUntil: Date.now() + SETTLE_WINDOW_MS, announce }),
  beginTerritory: () => set({ territoryUntil: Date.now() + SETTLE_WINDOW_MS, settleUntil: Date.now() + SETTLE_WINDOW_MS, announce: false }),
}));

/** 반영 대기 창 동안만 짧은 주기로 다시 읽는다. */
export function settleInterval(): number | false {
  return Date.now() < useSyncStore.getState().settleUntil ? SETTLE_INTERVAL_MS : false;
}

/** 영토 반영 대기 창(로그인 병합 직후만) */
export function territorySettleInterval(): number | false {
  return Date.now() < useSyncStore.getState().territoryUntil ? SETTLE_INTERVAL_MS * 2 : false;
}

/** 변경 직후: 대기 창을 열고 반영 대상 쿼리를 무효화한다(지금 한 번 + 창 동안 주기적으로). */
export function settleAfterChange(queryClient: QueryClient, announce: boolean): Promise<void> {
  useSyncStore.getState().begin(announce);
  return queryClient.invalidateQueries({ queryKey: [SETTLED_ROOT] });
}
