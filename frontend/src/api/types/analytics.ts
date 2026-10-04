/**
 * 10단계 분석(계약 _workspace/10_contracts.md) — 화면 이벤트(POST /events)와 운영 지표(GET /admin/metrics)의 타입.
 * 백엔드 analytics.api.web.AnalyticsDtos 와 1:1. 시각(Instant)은 ISO-8601 UTC 문자열, 날짜(LocalDate)는 서울 날짜 yyyy-MM-dd.
 */

// ---- 화면 이벤트(§1-1) ----

/** push = 알림을 눌러 열었다(12단계) */
export type EntryPoint = 'direct' | 'invite' | 'profile' | 'card' | 'other' | 'push';
export type AnalyticsTab = 'map' | 'bag' | 'sets' | 'quests' | 'rank' | 'profile';
export type OnboardingAction = 'view' | 'done' | 'skip';
export type PushPromptResult = 'shown' | 'granted' | 'denied' | 'dismissed';
/** 알림 종류(12단계 — notification 컨텍스트 NotificationKind) */
export type PushKind = 'mystery' | 'streak' | 'season';

/** 이벤트 이름 → 필드. 여기 없는 이름·필드는 보내지 않는다(서버도 거절한다). */
export interface ClientEventProps {
  app_open: { entry: EntryPoint };
  tab_view: { tab: AnalyticsTab };
  checkin_open: Record<never, never>;
  checkin_save: Record<never, never>;
  checkin_cancel: Record<never, never>;
  /** target: 소문자 식별자(territory·recent·recap·vs·profile·invite) */
  share_click: { target: string };
  /** target: 소문자 식별자(profile·invite·card) */
  link_copy: { target: string };
  onboarding_step: { step: number; action: OnboardingAction };
  push_prompt: { result: PushPromptResult };
  /** 알림을 눌러 열었다(12단계) */
  push_open: { kind: PushKind };
  /** code: 서버 오류 코드 형식([A-Z][A-Z0-9_]{1,63}) — 문장 금지 */
  error_toast: { code: string };
}

export type ClientEventName = keyof ClientEventProps;

/** 요청 본문의 이벤트 한 건 */
export interface EventItem {
  name: ClientEventName;
  at: string;
  props: Record<string, string | number>;
}

export interface EventsRequest {
  visitorId: string;
  events: EventItem[];
}

export type RejectReason = 'UNKNOWN_EVENT' | 'SERVER_ONLY_EVENT' | 'PERSONAL_DATA' | 'UNKNOWN_FIELD' | 'MISSING_FIELD' | 'INVALID_FIELD';

export interface RejectedEvent {
  index: number;
  name: string;
  reason: RejectReason;
}

/** 202 응답 */
export interface EventsResponse {
  accepted: number;
  rejected: RejectedEvent[];
}

// ---- 운영 지표(§3) ----

export interface TodayView {
  day: string;
  newVisitors: number;
  newExplorers: number;
  dau: number;
  wau: number;
  mau: number;
  computedAt: string;
}

export interface DailyView {
  day: string;
  newVisitors: number;
  newExplorers: number;
  dau: number;
  wau: number;
  mau: number;
  profileViews: number;
  cardViews: number;
  botViews: number;
  kFactor: number | null;
  live: boolean;
  /** 그날 30일 구간(MAU·K 계수) 앞부분 원본이 이미 지워진 채 계산했다 — 신뢰도 낮음(오늘은 늘 false, 12단계) */
  partialWindow: boolean;
}

export interface FunnelView {
  cohortDay: string;
  firstScreen: number;
  firstCheckIn: number;
  revisitedWithin7Days: number;
  checkInRate: number | null;
  revisitRate: number | null;
  overallRate: number | null;
  settled: boolean;
}

export interface RetentionView {
  cohortDay: string;
  newExplorers: number;
  d1: number | null;
  d7: number | null;
  d30: number | null;
  d1Rate: number | null;
  d7Rate: number | null;
  d30Rate: number | null;
}

export interface KFactorView {
  from: string;
  to: string;
  invitedNewExplorers: number;
  cardNewExplorers: number;
  viralNewExplorers: number;
  activeExplorers: number;
  value: number | null;
}

export interface FeatureUse {
  name: string;
  users: number;
  rate: number | null;
}

export interface FeatureUsageView {
  from: string;
  to: string;
  activeUsers: number;
  features: FeatureUse[];
}

export interface ErrorCount {
  code: string;
  count: number;
}

export interface TopErrorsView {
  from: string;
  to: string;
  total: number;
  codes: ErrorCount[];
}

/** GET /admin/metrics?days= 응답 */
export interface MetricsResponse {
  generatedAt: string;
  timeZone: string;
  from: string;
  to: string;
  lastBatchAt: string | null;
  /** 아직 계산하지 않은 지난 날 — 배치로 채울 수 있다 */
  missingDays: string[];
  /** 계산한 적 없고 원본 보관 기간도 지나 다시 셀 수 없는 날(빈 날이 아니다) */
  expiredDays: string[];
  /** 배치가 아직 퍼널·리텐션을 계산하지 않은 코호트 날(원본이 남아 배치로 채울 수 있다, 12단계) */
  pendingCohortDays: string[];
  today: TodayView;
  daily: DailyView[];
  funnel: FunnelView[];
  retention: RetentionView[];
  kFactor: KFactorView;
  featureUsage: FeatureUsageView;
  topErrors: TopErrorsView;
}

/** POST /admin/metrics/batch 응답 */
export interface BatchResponse {
  from: string;
  to: string;
  dailyDays: number;
  cohortDays: number;
  purged: number;
  elapsedMs: number;
}
