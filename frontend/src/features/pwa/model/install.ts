/**
 * 홈 화면 설치 배너(순수 TS) — 두 번 이상 방문했고 첫 체크인을 한 사람에게만, 닫으면 14일 동안 숨긴다. 이미 홈 화면 앱으로 열었으면 보이지 않는다.
 * 크로미움은 브라우저 설치 창(beforeinstallprompt)이 준비됐을 때만 "설치" 버튼, iOS 사파리는 "공유 → 홈 화면에 추가" 안내.
 */
import type { VisitLog } from '../../../store/pwaStore';

/** 이만큼 쉬었다 다시 열면 새 방문 */
export const VISIT_GAP_MINUTES = 30;
export const MIN_VISITS = 2;
export const HIDE_AFTER_DISMISS_DAYS = 14;
const MINUTE_MS = 60 * 1000;
const DAY_MS = 24 * 60 * MINUTE_MS;

/** 화면을 연(또는 다시 본) 순간의 방문 기록 */
export function recordVisit(log: VisitLog, now: Date): VisitLog {
  const last = log.lastSeenAt ? Date.parse(log.lastSeenAt) : NaN;
  const fresh = !Number.isFinite(last) || now.getTime() - last >= VISIT_GAP_MINUTES * MINUTE_MS;
  return { count: fresh ? log.count + 1 : Math.max(log.count, 1), lastSeenAt: now.toISOString() };
}

export type InstallOffer = 'none' | 'prompt' | 'ios-guide';

export interface InstallInput {
  visits: number;
  checkedIn: boolean;
  standalone: boolean;
  installed: boolean;
  ios: boolean;
  /** 브라우저 설치 창을 띄울 수 있다(beforeinstallprompt 를 받아 둠) */
  canPrompt: boolean;
  dismissedAt: string | null;
  now: Date;
}

export function installOffer(input: InstallInput): InstallOffer {
  const { visits, checkedIn, standalone, installed, ios, canPrompt, dismissedAt, now } = input;
  if (standalone || installed || visits < MIN_VISITS || !checkedIn) return 'none';
  const at = dismissedAt ? Date.parse(dismissedAt) : NaN;
  if (Number.isFinite(at) && now.getTime() - at < HIDE_AFTER_DISMISS_DAYS * DAY_MS) return 'none';
  if (canPrompt) return 'prompt';
  return ios ? 'ios-guide' : 'none';
}
