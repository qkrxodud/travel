/**
 * "알림 받을래요?" 동의 흐름(순수 TS, 계약 _workspace/12_contracts.md §0).
 *
 * - 첫 체크인 뒤에만 묻는다(칠한 내 영토가 한 곳이라도 있을 때 — 예시 데이터는 세지 않는다).
 * - 예 → 그때 브라우저 권한을 묻는다(화면 질문보다 먼저 묻지 않는다). 허용·거절은 브라우저가 기억하므로 다시 묻지 않는다.
 * - 나중에 → 30일 동안 묻지 않고, 두 번 미루면 더는 화면에서 묻지 않는다(프로필 탭 알림 설정에서 언제든 켤 수 있다).
 * - 미지원 브라우저는 묻지 않는다. iOS 사파리 탭에서는 "홈 화면에 추가하면 받을 수 있어요"만 조용히 한 번(닫으면 같은 규칙).
 */
import type { PushSupport } from '../../../shared/lib/pwa/device';
import type { PermissionState } from '../../../shared/lib/pwa/pushBrowser';
import type { PromptAnswer, PromptMemory } from '../../../store/pwaStore';

export const ASK_AGAIN_AFTER_DAYS = 30;
export const MAX_DISMISSALS = 2;
const DAY_MS = 24 * 60 * 60 * 1000;

export type ConsentStep = 'hidden' | 'ask' | 'ios-install';

export interface ConsentInput {
  checkedIn: boolean;
  support: PushSupport;
  permission: PermissionState;
  /** 이 브라우저가 이미 구독돼 있다(null = 아직 모름) */
  subscribed: boolean | null;
  memory: PromptMemory;
  now: Date;
}

/** 미룬 뒤 다시 물을 때가 됐는지 */
export function coolingDown(memory: PromptMemory, now: Date): boolean {
  if (memory.answer !== 'dismissed') return false;
  if (memory.dismissals >= MAX_DISMISSALS) return true;
  const at = memory.dismissedAt ? Date.parse(memory.dismissedAt) : NaN;
  return Number.isFinite(at) && now.getTime() - at < ASK_AGAIN_AFTER_DAYS * DAY_MS;
}

/** 지금 화면에 무엇을 보일지 */
export function consentStep(input: ConsentInput): ConsentStep {
  const { checkedIn, support, permission, subscribed, memory, now } = input;
  if (!checkedIn || support === 'unsupported') return 'hidden';
  if (coolingDown(memory, now)) return 'hidden';
  if (support === 'ios-needs-install') return memory.answer === null || memory.answer === 'dismissed' ? 'ios-install' : 'hidden';
  if (subscribed === null || subscribed) return 'hidden';
  if (permission === 'denied' || permission === 'unsupported') return 'hidden';
  if (memory.answer === 'granted' || memory.answer === 'denied') return 'hidden';
  return 'ask';
}

/** "나중에"·닫기 */
export function dismissed(memory: PromptMemory, now: Date): PromptMemory {
  return { answer: 'dismissed', dismissals: memory.dismissals + 1, dismissedAt: now.toISOString() };
}

/** 브라우저 권한 답 */
export function answered(memory: PromptMemory, answer: Exclude<PromptAnswer, 'dismissed'>): PromptMemory {
  return { ...memory, answer };
}

/** 예를 누른 뒤의 결과 — 화면 문구 */
export type EnableOutcome = 'subscribed' | 'denied' | 'failed';

export const OUTCOME_TEXT: Readonly<Record<EnableOutcome, { title: string; detail: string }>> = {
  subscribed: { title: '알림을 켰어요', detail: '미스터리 지역·스트릭·계절 테마 소식을 하루 한 번까지만 보내요. 프로필 탭에서 종류별로 끌 수 있어요.' },
  denied: { title: '알림을 허용하지 않았어요', detail: '마음이 바뀌면 브라우저 사이트 설정에서 알림을 허용한 뒤 프로필 탭에서 켤 수 있어요.' },
  failed: { title: '이 브라우저에서는 알림을 켤 수 없어요', detail: '다른 브라우저나 홈 화면에 추가한 앱에서 다시 시도해 주세요.' },
};
