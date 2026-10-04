/**
 * 프로필 탭 알림 설정(순수 TS) — 서버 설정(GET /push/preferences)과 이 브라우저 상태를 화면 줄로. 판단은 서버가 하고(하루 1개·조용한 시간),
 * 화면은 서버가 알려 준 값을 문장으로만 바꾼다.
 */
import type { PreferencesRequest, PreferencesResponse, PushKind } from '../../../api/types/notification';
import type { PushSupport } from '../../../shared/lib/pwa/device';
import type { PermissionState } from '../../../shared/lib/pwa/pushBrowser';

export const KIND_ORDER: readonly PushKind[] = ['mystery', 'streak', 'season'];

export const KIND_TEXT: Readonly<Record<PushKind, { label: string; when: string }>> = {
  mystery: { label: '이번 주 미스터리 지역', when: '월요일 아침 — 어디인지는 비밀' },
  streak: { label: '스트릭 지키기', when: '월말 3일 전, 이번 달 새 지역이 없을 때' },
  season: { label: '계절 테마 시작', when: '계절 한정 지역이 열리는 날' },
};

/**
 * 이 기기 상태:
 * - unsupported: 이 브라우저는 웹 푸시를 못 받는다 / ios-install: 홈 화면에 추가해야 받는다
 * - blocked: 브라우저 설정에서 알림이 막혀 있다 / off: 이 기기는 아직 구독 전 / on: 이 기기에서 받는다
 */
export type DeviceStatus = 'unsupported' | 'ios-install' | 'blocked' | 'off' | 'on';

export function deviceStatus(support: PushSupport, permission: PermissionState, subscribed: boolean): DeviceStatus {
  if (support === 'ios-needs-install') return 'ios-install';
  if (support === 'unsupported') return 'unsupported';
  if (subscribed && permission === 'granted') return 'on';
  if (permission === 'denied') return 'blocked';
  return 'off';
}

export const DEVICE_STATUS_TEXT: Readonly<Record<DeviceStatus, string>> = {
  unsupported: '이 브라우저는 알림을 받을 수 없어요.',
  'ios-install': 'iPhone·iPad 는 사파리 공유 버튼 → "홈 화면에 추가"로 설치한 앱에서 알림을 받을 수 있어요.',
  blocked: '브라우저 설정에서 이 사이트 알림이 막혀 있어요. 사이트 설정에서 허용하면 켤 수 있어요.',
  off: '이 기기는 아직 알림을 받지 않아요.',
  on: '이 기기에서 알림을 받고 있어요.',
};

export interface KindRow {
  kind: PushKind;
  label: string;
  when: string;
  on: boolean;
}

export function kindRows(preferences: PreferencesRequest): KindRow[] {
  return KIND_ORDER.map(kind => ({ kind, ...KIND_TEXT[kind], on: preferences[kind] }));
}

/** 한 종류만 바꾼 PUT 본문(세 값 모두 — 서버가 셋 다 요구한다) */
export function toggled(preferences: PreferencesRequest, kind: PushKind, on: boolean): PreferencesRequest {
  return { mystery: preferences.mystery, streak: preferences.streak, season: preferences.season, [kind]: on };
}

/** "22:00~08:00 에는 보내지 않고 하루 1개까지" */
export function rulesText(preferences: Pick<PreferencesResponse, 'quietHours' | 'dailyLimit'>): string {
  const { start, end } = preferences.quietHours;
  return `${start}~${end}에는 보내지 않고, 하루에 ${preferences.dailyLimit}개까지만 보내요.`;
}

/** 서버에 등록된 기기 수 안내 — 0 이면 아무 알림도 가지 않는다 */
export function devicesText(devices: number): string {
  return devices > 0 ? `알림 받는 기기 ${devices}대` : '알림 받는 기기가 없어요 — 설정만으로는 알림이 오지 않아요';
}
