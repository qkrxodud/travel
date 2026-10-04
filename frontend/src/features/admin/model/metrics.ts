/**
 * 운영 지표 표시 로직(순수 TS) — 서버가 계산한 숫자·비율을 그대로 쓰고, 화면은 순서·포맷·이름만 정한다(다시 계산하지 않는다).
 * 비율 null 은 "분모 0"(0% 와 다르다) → "—", 리텐션 수 null 은 "아직 셀 수 없음" → 빈칸.
 */
import type { DailyView, FeatureUse, FunnelView, MetricsResponse, RetentionView } from '../../../api/types/analytics';

export const DAY_OPTIONS = [7, 30, 90] as const;
export const DEFAULT_DAYS = 30;

/** 0~1 비율 → "42.5%"(분모 0 이면 "—") */
export function percentText(rate: number | null): string {
  if (rate === null || !Number.isFinite(rate)) return '—';
  return `${(Math.round(rate * 1000) / 10).toFixed(1)}%`;
}

/** 수(null = 아직 셀 수 없음 → 빈칸) */
export function countText(count: number | null): string {
  return count === null ? '' : count.toLocaleString('ko-KR');
}

/** K 계수 → "0.42"(활동 탐험가 0 이면 "—") */
export function kFactorText(value: number | null): string {
  return value === null ? '—' : value.toFixed(2);
}

/** 2026-10-04 → 10/4 */
export function shortDay(day: string): string {
  const [, month, date] = day.split('-');
  return month && date ? `${Number(month)}/${Number(date)}` : day;
}

export interface Headline {
  key: 'dau' | 'wau' | 'mau' | 'new-visitors' | 'new-explorers' | 'k-factor';
  label: string;
  value: string;
  /** 서버 값 그대로(data-value) */
  raw: number | null;
  note: string;
}

/** 머리 숫자(오늘 실시간 + 30일 K 계수) */
export function headlines(metrics: MetricsResponse): Headline[] {
  const { today, kFactor } = metrics;
  return [
    { key: 'dau', label: 'DAU', value: countText(today.dau), raw: today.dau, note: '오늘' },
    { key: 'wau', label: 'WAU', value: countText(today.wau), raw: today.wau, note: '7일' },
    { key: 'mau', label: 'MAU', value: countText(today.mau), raw: today.mau, note: '30일' },
    { key: 'new-visitors', label: '새 방문', value: countText(today.newVisitors), raw: today.newVisitors, note: '오늘 처음 본 방문' },
    { key: 'new-explorers', label: '새 탐험가', value: countText(today.newExplorers), raw: today.newExplorers, note: '오늘 가입' },
    { key: 'k-factor', label: 'K 계수', value: kFactorText(kFactor.value), raw: kFactor.value, note: `${kFactor.viralNewExplorers} / ${kFactor.activeExplorers} · 30일` },
  ];
}

/** 그날 숫자의 상태: 센 날 / 배치 전(배치로 채울 수 있음) / 원본 보관 기간 지남(다시 셀 수 없음) */
export type DayStatus = 'counted' | 'missing' | 'expired';

export const DAY_STATUS_TEXT: Readonly<Record<Exclude<DayStatus, 'counted'>, string>> = {
  missing: '배치 전',
  expired: '보관 기간 지남',
};

/** from ~ to(서울 날짜 yyyy-MM-dd) 의 모든 날, 오래된 날 먼저 */
export function daysBetween(from: string, to: string): string[] {
  const days: string[] = [];
  const cursor = new Date(from + 'T00:00:00Z');
  const end = new Date(to + 'T00:00:00Z');
  if (Number.isNaN(cursor.getTime()) || Number.isNaN(end.getTime())) return days;
  while (cursor <= end && days.length < 400) {
    days.push(cursor.toISOString().slice(0, 10));
    cursor.setUTCDate(cursor.getUTCDate() + 1);
  }
  return days;
}

export interface DayEntry {
  day: string;
  status: DayStatus;
  /** 센 날만 값이 있다 — 배치 전·보관 기간 지남은 null(0 으로 그리지 않는다) */
  row: DailyView | null;
}

/**
 * 조회 구간의 모든 날. 서버 daily[] 에는 센 날만 오므로, 빠진 날을 배치 전(missingDays)·보관 기간 지남(expiredDays)으로 채운다.
 * 어느 목록에도 없이 빠진 날은 배치 전으로 본다.
 */
export function dayEntries(metrics: Pick<MetricsResponse, 'from' | 'to' | 'daily' | 'missingDays' | 'expiredDays'>): DayEntry[] {
  const byDay = new Map(metrics.daily.map(row => [row.day, row]));
  const expired = new Set(metrics.expiredDays);
  return daysBetween(metrics.from, metrics.to).map(day => {
    const row = byDay.get(day) ?? null;
    if (row) return { day, status: 'counted' as const, row };
    return { day, status: expired.has(day) ? 'expired' as const : 'missing' as const, row: null };
  });
}

export interface Series {
  key: string;
  label: string;
  /** 범례·선 색 순서(1부터 — 고정 순서, 순위로 바꾸지 않는다) */
  slot: 1 | 2 | 3;
  /** 날마다 값(센 날이 아니면 null — 선이 끊긴다) */
  values: (number | null)[];
}

export interface Trend {
  days: string[];
  status: DayStatus[];
  series: Series[];
}

type Measure = 'dau' | 'wau' | 'mau' | 'newVisitors' | 'newExplorers';

function trend(entries: readonly DayEntry[], measures: readonly (readonly [string, string, Series['slot'], Measure])[]): Trend {
  return {
    days: entries.map(entry => entry.day),
    status: entries.map(entry => entry.status),
    series: measures.map(([key, label, slot, measure]) => ({ key, label, slot, values: entries.map(entry => entry.row?.[measure] ?? null) })),
  };
}

/** 일별 활동(DAU·WAU·MAU) */
export function activityTrend(entries: readonly DayEntry[]): Trend {
  return trend(entries, [['dau', 'DAU', 1, 'dau'], ['wau', 'WAU', 2, 'wau'], ['mau', 'MAU', 3, 'mau']]);
}

/** 일별 신규(새 방문·새 탐험가) */
export function newcomerTrend(entries: readonly DayEntry[]): Trend {
  return trend(entries, [['new-visitors', '새 방문', 1, 'newVisitors'], ['new-explorers', '새 탐험가', 2, 'newExplorers']]);
}

/** 차트에 띠로 표시할 셀 수 없는 날 묶음(이어진 날끼리) */
export interface GapBand {
  status: Exclude<DayStatus, 'counted'>;
  start: number;
  end: number;
}

export function gapBands(status: readonly DayStatus[]): GapBand[] {
  const bands: GapBand[] = [];
  status.forEach((current, index) => {
    if (current === 'counted') return;
    const last = bands.at(-1);
    if (last && last.status === current && last.end === index - 1) last.end = index;
    else bands.push({ status: current, start: index, end: index });
  });
  return bands;
}

/** 마지막으로 센 날의 값(선 끝 이름표) */
export function lastValue(values: readonly (number | null)[]): { index: number; value: number } | null {
  for (let i = values.length - 1; i >= 0; i--) {
    const value = values[i];
    if (value !== null && value !== undefined) return { index: i, value };
  }
  return null;
}

/** 보관 기간이 지나 다시 셀 수 없는 날 안내(없으면 null) */
export function expiredDaysText(expiredDays: readonly string[]): string | null {
  if (!expiredDays.length) return null;
  const first = expiredDays[0] ?? '';
  const last = expiredDays.at(-1) ?? first;
  const span = expiredDays.length === 1 ? shortDay(first) : `${shortDay(first)} ~ ${shortDay(last)}`;
  return `원본 보관 기간이 지나 다시 셀 수 없는 날 ${expiredDays.length}일(${span}) — 빈 날(0)이 아니라 숫자가 없는 날이에요`;
}

/** 세로축 위 끝 — 가장 큰 값보다 크거나 같은 "깔끔한" 수(값이 모두 0 이면 1) */
export function niceMax(values: readonly (number | null)[]): number {
  const peak = Math.max(0, ...values.filter((value): value is number => value !== null));
  if (peak <= 0) return 1;
  const magnitude = 10 ** Math.floor(Math.log10(peak));
  const steps = [1, 2, 2.5, 5, 10];
  const step = steps.find(candidate => candidate * magnitude >= peak) ?? 10;
  return step * magnitude;
}

export interface FunnelRow {
  cohortDay: string;
  firstScreen: string;
  firstCheckIn: string;
  revisited: string;
  checkInRate: string;
  revisitRate: string;
  overallRate: string;
  status: string;
  settled: boolean;
  today: boolean;
  /** 배치가 아직 계산하지 않은 코호트(숫자 대신 "배치 전" — 이어진 날은 한 줄로 묶고 cohortDay 는 그 마지막 날) */
  pending: PendingSpan | null;
}

/** 이어진 날끼리 묶은 배치 전 코호트(표에 한 줄씩 — 30일이 모두 비어도 표가 길어지지 않게) */
export interface PendingSpan {
  /** 묶음의 마지막 날(표 정렬 기준) */
  last: string;
  days: number;
  /** "배치 전 · 9/9 ~ 10/7 (29일)" */
  text: string;
}

function nextDay(day: string): string {
  const date = new Date(day + 'T00:00:00Z');
  date.setUTCDate(date.getUTCDate() + 1);
  return date.toISOString().slice(0, 10);
}

export function pendingSpans(days: readonly string[]): PendingSpan[] {
  const sorted = [...new Set(days)].sort();
  const spans: { first: string; last: string; days: number }[] = [];
  for (const day of sorted) {
    const current = spans.at(-1);
    if (current && nextDay(current.last) === day) {
      current.last = day;
      current.days += 1;
    } else {
      spans.push({ first: day, last: day, days: 1 });
    }
  }
  return spans.map(span => ({
    last: span.last,
    days: span.days,
    text: span.days === 1
      ? `${DAY_STATUS_TEXT.missing} — 배치를 실행하면 채워져요`
      : `${DAY_STATUS_TEXT.missing} · ${shortDay(span.first)} ~ ${shortDay(span.last)} (${span.days}일) — 배치를 실행하면 채워져요`,
  }));
}

/** 최근 날 먼저(같은 날은 없다 — 배치 전 코호트는 서버 funnel·retention 에 오지 않는다) */
function latestFirst<T extends { cohortDay: string }>(rows: readonly T[]): T[] {
  return rows.slice().sort((first, second) => second.cohortDay.localeCompare(first.cohortDay));
}

/**
 * 퍼널 표 — 최근 코호트 먼저, 첫 화면이 있는 날과 오늘만. 아직 바뀔 수 있는 코호트는 "집계 중".
 * 배치가 아직 계산하지 않은 코호트(pendingCohortDays)는 0 명이 아니라 "배치 전" 줄로 끼운다.
 */
export function funnelRows(funnel: readonly FunnelView[], today: string, pendingCohortDays: readonly string[] = []): FunnelRow[] {
  const counted: FunnelRow[] = funnel
    .filter(row => row.firstScreen > 0 || row.cohortDay === today)
    .map(row => ({
      cohortDay: row.cohortDay,
      firstScreen: countText(row.firstScreen),
      firstCheckIn: countText(row.firstCheckIn),
      revisited: countText(row.revisitedWithin7Days),
      checkInRate: percentText(row.checkInRate),
      revisitRate: percentText(row.revisitRate),
      overallRate: percentText(row.overallRate),
      status: row.settled ? '확정' : '집계 중',
      settled: row.settled,
      today: row.cohortDay === today,
      pending: null,
    }));
  const known = new Set(counted.map(row => row.cohortDay));
  const pending: FunnelRow[] = pendingSpans(pendingCohortDays.filter(day => !known.has(day))).map(span => ({
    cohortDay: span.last, firstScreen: '', firstCheckIn: '', revisited: '', checkInRate: '', revisitRate: '', overallRate: '',
    status: DAY_STATUS_TEXT.missing, settled: false, today: false, pending: span,
  }));
  return latestFirst([...counted, ...pending]);
}

export interface RetentionCell {
  count: string;
  rate: string;
  /** 칸 바탕 진하기(0~1). 셀 수 없으면 null */
  shade: number | null;
}

export interface RetentionRow {
  cohortDay: string;
  newExplorers: string;
  cells: { d1: RetentionCell; d7: RetentionCell; d30: RetentionCell };
  /** 배치가 아직 계산하지 않은 코호트(숫자 대신 "배치 전" — 이어진 날은 한 줄로) */
  pending: PendingSpan | null;
}

function retentionCell(count: number | null, rate: number | null): RetentionCell {
  if (count === null) return { count: '', rate: '', shade: null };
  return { count: countText(count), rate: percentText(rate), shade: rate === null ? 0 : Math.min(1, Math.max(0, rate)) };
}

const BLANK_CELL: RetentionCell = { count: '', rate: '', shade: null };

/** 리텐션 표 — 가입한 탐험가가 있는 코호트와 배치 전 코호트("배치 전" 줄), 최근 먼저 */
export function retentionRows(retention: readonly RetentionView[], pendingCohortDays: readonly string[] = []): RetentionRow[] {
  const counted: RetentionRow[] = retention
    .filter(row => row.newExplorers > 0)
    .map(row => ({
      cohortDay: row.cohortDay,
      newExplorers: countText(row.newExplorers),
      cells: {
        d1: retentionCell(row.d1, row.d1Rate),
        d7: retentionCell(row.d7, row.d7Rate),
        d30: retentionCell(row.d30, row.d30Rate),
      },
      pending: null,
    }));
  const known = new Set(counted.map(row => row.cohortDay));
  const pending: RetentionRow[] = pendingSpans(pendingCohortDays.filter(day => !known.has(day))).map(span => ({
    cohortDay: span.last, newExplorers: '', cells: { d1: BLANK_CELL, d7: BLANK_CELL, d30: BLANK_CELL }, pending: span,
  }));
  return latestFirst([...counted, ...pending]);
}

/** 기능 이름(서버 이벤트 이름) → 화면 이름. 모르는 이름은 그대로. */
const FEATURE_LABEL: Readonly<Record<string, string>> = {
  tab_view: '탭 보기',
  checkin_open: '체크인 열기',
  share_click: '공유 버튼',
  link_copy: '링크 복사',
  check_in: '체크인',
  shared_map_created: '공유 지도 만들기',
  shared_map_joined: '공유 지도 초대 합류',
  account_linked: '로그인 연결',
  quest_claimed: '퀘스트 보상 받기',
  revisit_stamped: '재방문 도장',
  wish_fulfilled: '가고 싶은 곳 다녀옴',
};

export function featureLabel(name: string): string {
  return FEATURE_LABEL[name] ?? name;
}

export interface FeatureBar {
  name: string;
  label: string;
  users: string;
  rate: string;
  /** 막대 길이(0~100) */
  width: number;
}

/** 기능별 사용률 막대 — 서버 순서(많이 쓴 기능 먼저) 그대로 */
export function featureBars(features: readonly FeatureUse[]): FeatureBar[] {
  return features.map(feature => ({
    name: feature.name,
    label: featureLabel(feature.name),
    users: countText(feature.users),
    rate: percentText(feature.rate),
    width: feature.rate === null ? 0 : Math.round(Math.min(1, Math.max(0, feature.rate)) * 1000) / 10,
  }));
}

/** 30일 구간 앞부분 원본이 지워진 채 계산한 날의 MAU·K 계수 옆 표시 */
export const LOW_CONFIDENCE_TEXT = '신뢰도 낮음';

/** 그런 날이 있으면 안내(없으면 null) — 오늘(실시간)은 늘 온전하다 */
export function partialWindowText(daily: readonly Pick<DailyView, 'day' | 'partialWindow'>[]): string | null {
  const partial = daily.filter(row => row.partialWindow);
  if (!partial.length) return null;
  return `${partial.length}일은 30일 구간 앞부분 원본이 지워진 뒤 늦게 계산해 MAU·K 계수가 실제보다 작게 나올 수 있어요(${LOW_CONFIDENCE_TEXT})`;
}

/** 배치가 비워 둔 날 안내(없으면 null) */
export function missingDaysText(missingDays: readonly string[]): string | null {
  if (!missingDays.length) return null;
  const shown = missingDays.slice(0, 3).map(shortDay).join(', ');
  return `아직 계산하지 않은 날 ${missingDays.length}일(${shown}${missingDays.length > 3 ? ' …' : ''}) — 배치를 실행하면 채워져요`;
}

/** 관리자 API 오류 → 안내 문장 */
export function adminErrorText(error: unknown): string {
  const code = error && typeof error === 'object' ? (error as { code?: unknown }).code : undefined;
  if (code === 'ADMIN_TOKEN_REQUIRED') return '관리자 토큰을 입력해 주세요.';
  if (code === 'ADMIN_TOKEN_INVALID') return '관리자 토큰이 맞지 않아요. 다시 확인해 주세요.';
  if (code === 'INVALID_METRICS_RANGE') return '조회 기간은 1~90일이에요.';
  return '지표를 불러오지 못했어요. 잠시 뒤 다시 시도해 주세요.';
}

/** 선 끝 이름표 세로 위치 — 가까우면 gap 만큼 벌린다(입력 순서대로 돌려준다) */
export function spreadLabels(positions: readonly number[], gap: number): number[] {
  const order = positions.map((position, index) => ({ position, index })).sort((first, second) => first.position - second.position);
  for (let i = 1; i < order.length; i++) {
    const previous = order[i - 1];
    const current = order[i];
    if (previous && current && current.position - previous.position < gap) current.position = previous.position + gap;
  }
  const spread = new Array<number>(positions.length);
  for (const label of order) spread[label.index] = label.position;
  return spread;
}

/** 퍼널·리텐션 표 아래 안내 — 보관 기간이 지난 날의 코호트는 다시 셀 수 없어 표에 없다(없으면 null) */
export function expiredCohortNote(expiredDays: readonly string[]): string | null {
  if (!expiredDays.length) return null;
  const first = expiredDays[0] ?? '';
  const last = expiredDays.at(-1) ?? first;
  return `${shortDay(first)}${expiredDays.length > 1 ? ` ~ ${shortDay(last)}` : ''} 코호트(${expiredDays.length}일)는 ${DAY_STATUS_TEXT.expired} — 표에 없는 것은 0 명이어서가 아니에요`;
}
