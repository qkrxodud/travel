/**
 * 관리자 계절 회차 화면(13s단계) 표시 로직(순수 TS). 어떤 지역을 어떤 순위로 골랐는지·확정 여부·호출 수·경고는 서버 값 —
 * 화면은 이름표·문구·버튼을 누를 수 있는지만 정한다(다시 계산하지 않는다).
 */
import type {
  LineupAttemptOutcome, LineupCandidateResponse, LineupConfirmedBy, LineupNextPlan, LineupRegionResponse, LineupScheduleResponse,
  RoundLineupResponse, TourApiStatusResponse,
} from '../../../api/types/catalog';
import { toClientCode } from '../../../api/client';
import { seasonPeriodText } from '../../../shared/lib/progress/season';
import { evidenceText, provenanceText } from '../../../shared/lib/progress/seasonEvidence';

/** 관리자 화면 안의 보기 — #/admin(운영 지표) · #/admin/seasons(계절 회차) */
export type AdminView = 'metrics' | 'seasons';
export const ADMIN_VIEW_HASH: Readonly<Record<AdminView, string>> = { metrics: '#/admin', seasons: '#/admin/seasons' };

export function adminViewOf(hash: string): AdminView {
  return hash.startsWith(ADMIN_VIEW_HASH.seasons) ? 'seasons' : 'metrics';
}

const OUTCOME_TEXT: Readonly<Record<LineupAttemptOutcome, string>> = {
  COLLECTED: '모음',
  PARTIAL: '일부만 모음(근거 부족)',
  NOT_CONFIGURED: '키 없음 — 부르지 않음',
  KEY_REJECTED: '키 거절',
  QUOTA_EXCEEDED: '호출 한도 초과',
  BAD_RESPONSE: '응답 이상',
  UNREACHABLE: '연결 실패',
};

const PLAN_TEXT: Readonly<Record<LineupNextPlan, string>> = {
  NONE: '자동 수집 없음',
  PREVIEW: '미리보기만',
  COLLECT: '모아서 후보로',
  COLLECT_AND_CONFIRM: '모아서 자동 확정',
};

const CONFIRMED_BY_TEXT: Readonly<Record<LineupConfirmedBy, string>> = {
  AUTO: '자동 확정',
  ADMIN: '관리자 확정',
  OPENING: '열릴 때 고정',
};

export function attemptOutcomeText(outcome: LineupAttemptOutcome): string {
  return OUTCOME_TEXT[outcome];
}

export function nextPlanText(plan: LineupNextPlan): string {
  return PLAN_TEXT[plan];
}

/** 쓰는 목록의 상태 — 확정한 쪽(null = 아직 열리지 않은 회차의 기본 목록) */
export function confirmedByText(confirmedBy: LineupConfirmedBy | null): string {
  return confirmedBy ? CONFIRMED_BY_TEXT[confirmedBy] : '기본 목록(확정 전)';
}

/** 오늘 호출 "오늘 TourAPI 호출 3 / 200회" */
export function usageText(tourApi: TourApiStatusResponse): string {
  return `오늘 TourAPI 호출 ${tourApi.callsToday.toLocaleString('ko-KR')} / ${tourApi.dailyLimit.toLocaleString('ko-KR')}회`;
}

/** 키 연결 상태 — 키 값은 서버도 보내지 않는다(연결 여부만) */
export function keyStateText(tourApi: TourApiStatusResponse): string {
  if (!tourApi.configured) return '키 없음';
  return tourApi.exhausted ? '키 연결됨 · 오늘 상한 도달' : '키 연결됨';
}

/** 자동 수집 정책 한 줄 */
export function scheduleText(schedule: LineupScheduleResponse): string {
  const days = Math.round(schedule.recollectAfterHours / 24);
  const confirm = schedule.autoConfirm ? `근거 ${schedule.autoConfirmMinRegions}곳이 모이면 자동 확정` : '자동 확정 꺼짐(관리자가 확정)';
  return `회차 시작 ${schedule.leadDays}일 전부터 ${days}일마다 모아요 · ${confirm}`;
}

/** 회차 기간(서울 날짜) */
export function roundPeriodText(round: Pick<RoundLineupResponse, 'startsAt' | 'endsAt'>): string {
  return seasonPeriodText(round);
}

/** 후보의 근거 지역이 자동 확정 기준보다 적으면 경고(아니면 null) */
export function shortageText(candidate: LineupCandidateResponse | null, schedule: LineupScheduleResponse | undefined): string | null {
  if (!candidate) return null;
  const needed = schedule?.autoConfirmMinRegions ?? candidate.regions.length;
  if (candidate.evidencedRegions >= needed) return null;
  return `근거 지역 ${candidate.evidencedRegions}곳 — ${needed}곳 미만이라 자동 확정하지 않아요(나머지는 AI 추정으로 채움)`;
}

/** 후보 한 줄 "후보 10곳 중 TourAPI 근거 8곳 · 2026. 10. 5. 오후 1:20 모음" 의 앞부분 */
export function candidateText(candidate: LineupCandidateResponse): string {
  return `후보 ${candidate.regions.length}곳 중 TourAPI 근거 ${candidate.evidencedRegions}곳 · ${provenanceText(candidate.provenance)}`;
}

/** 갱신 버튼 — 열렸거나 지난 회차는 목록이 고정돼 누를 수 없다 */
export function canRefresh(round: RoundLineupResponse): boolean {
  return !round.locked;
}

/** 확정 버튼 — 고정되지 않았고 후보가 있어야 한다 */
export function canConfirm(round: RoundLineupResponse): boolean {
  return !round.locked && round.candidate !== null;
}

export interface LineupRegionRow {
  rank: number;
  /** 화면 코드(11010) */
  code: string;
  name: string;
  provenance: LineupRegionResponse['provenance'];
  sourceText: string;
  evidence: string[];
}

/** 목록 표 — 서버 순서가 곧 순위(확정·후보 목록은 순위 순, 기본 목록은 정의 순서) */
export function regionRows(regions: readonly LineupRegionResponse[]): LineupRegionRow[] {
  return regions.map((region, index) => ({
    rank: index + 1,
    code: toClientCode(region.code),
    name: region.name ?? region.code,
    provenance: region.provenance,
    sourceText: provenanceText(region.provenance),
    evidence: region.evidence.map(evidenceText),
  }));
}

const ERROR_TEXT: Readonly<Record<string, string>> = {
  ADMIN_TOKEN_REQUIRED: '관리자 토큰을 입력해 주세요.',
  ADMIN_TOKEN_INVALID: '관리자 토큰이 맞지 않아요. 다시 확인해 주세요.',
  SEASON_ROUND_NOT_FOUND: '그런 회차가 없어요. 회차 id(예: spring-2027)를 확인해 주세요.',
  SEASON_ROUND_LOCKED: '이미 열렸거나 지난 회차라 지역 목록을 바꿀 수 없어요(진행 중인 목록은 고정돼요).',
  SEASON_CANDIDATE_MISSING: '확정할 후보가 없어요. 먼저 새로 모아 주세요.',
  SEASON_LINEUP_BUSY: '다른 곳에서 이 회차를 고치는 중이에요. 잠시 뒤 다시 시도해 주세요.',
};

/** 관리자 계절 회차 요청의 오류 문구 — 서버 오류 종류(code)마다 */
export function lineupErrorText(error: unknown): string {
  const code = error && typeof error === 'object' ? (error as { code?: unknown }).code : undefined;
  const text = typeof code === 'string' ? ERROR_TEXT[code] : undefined;
  return text ?? '계절 회차 요청을 처리하지 못했어요. 잠시 뒤 다시 시도해 주세요.';
}
