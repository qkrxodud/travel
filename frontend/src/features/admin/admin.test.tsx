/**
 * 관리자 화면 — 운영자는 관리자 토큰을 넣고 서버가 센 숫자(오늘 활동·추이·퍼널·리텐션·K 계수·기능 사용률·오류)를 그대로 보고,
 * 계절 회차마다 한국관광공사 TourAPI 근거로 모은 후보 지역을 살펴 확정한다(13s단계).
 * 이야기 순서: 숫자 읽는 법 → 머리 숫자·추이 → 퍼널 → 리텐션 → 기능·오류 → 토큰을 넣고 보기 → 배치가 비워 둔 날
 *   → 계절 회차(근거 읽는 법 → 회차 목록과 TourAPI 사용량 → 새로 모으기 → 확정 → 바꿀 수 없을 때).
 */
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { adminApi } from '../../api/admin';
import { ApiError } from '../../api/client';
import type { DailyView, FunnelView, MetricsResponse, RetentionView } from '../../api/types/analytics';
import type { LineupRegionResponse, RoundLineupResponse, SeasonLineupsResponse } from '../../api/types/catalog';
import { AdminApp } from './components/AdminApp';
import {
  activityTrend, adminErrorText, dayEntries, daysBetween, expiredCohortNote, expiredDaysText, gapBands, lastValue, countText, featureBars, funnelRows, headlines, kFactorText, missingDaysText, newcomerTrend, niceMax,
  partialWindowText, pendingSpans, percentText, retentionRows, shortDay, spreadLabels,
} from './model/metrics';
import {
  adminViewOf, attemptOutcomeText, canConfirm, canRefresh, confirmedByText, lineupErrorText, regionRows, scheduleText, shortageText, usageText,
} from './model/seasonLineups';

vi.mock('../../api/admin', () => ({
  adminApi: {
    metrics: vi.fn(), runBatch: vi.fn(async () => ({ from: '', to: '', dailyDays: 3, cohortDays: 35, purged: 0, elapsedMs: 5 })),
    seasonLineups: vi.fn(), seasonLineup: vi.fn(), refreshSeasonLineup: vi.fn(), confirmSeasonLineup: vi.fn(),
  },
}));

const day = (date: string, overrides: Partial<DailyView> = {}): DailyView => ({
  day: date, newVisitors: 4, newExplorers: 2, dau: 10, wau: 30, mau: 60, profileViews: 1, cardViews: 2, botViews: 0, kFactor: 0.1, live: false, partialWindow: false, ...overrides,
});
const funnel = (cohortDay: string, overrides: Partial<FunnelView> = {}): FunnelView => ({
  cohortDay, firstScreen: 10, firstCheckIn: 4, revisitedWithin7Days: 1, checkInRate: 0.4, revisitRate: 0.25, overallRate: 0.1, settled: true, ...overrides,
});
const retention = (cohortDay: string, overrides: Partial<RetentionView> = {}): RetentionView => ({
  cohortDay, newExplorers: 5, d1: 2, d7: 1, d30: null, d1Rate: 0.4, d7Rate: 0.2, d30Rate: null, ...overrides,
});

const METRICS: MetricsResponse = {
  generatedAt: '2026-10-04T03:00:00Z', timeZone: 'Asia/Seoul', from: '2026-10-02', to: '2026-10-04', lastBatchAt: null, missingDays: [], expiredDays: [], pendingCohortDays: [],
  today: { day: '2026-10-04', newVisitors: 3, newExplorers: 1, dau: 12, wau: 40, mau: 90, computedAt: '2026-10-04T03:00:00Z' },
  daily: [day('2026-10-02'), day('2026-10-03'), day('2026-10-04', { live: true, dau: 12 })],
  funnel: [funnel('2026-10-02'), funnel('2026-10-03', { firstScreen: 0, firstCheckIn: 0, revisitedWithin7Days: 0, checkInRate: null }), funnel('2026-10-04', { firstScreen: 2, firstCheckIn: 1, settled: false })],
  retention: [retention('2026-10-02'), retention('2026-10-03', { newExplorers: 0 }), retention('2026-10-04', { d1: null, d1Rate: null, d7: null, d7Rate: null })],
  kFactor: { from: '2026-09-05', to: '2026-10-04', invitedNewExplorers: 2, cardNewExplorers: 1, viralNewExplorers: 3, activeExplorers: 12, value: 0.25 },
  featureUsage: { from: '2026-09-28', to: '2026-10-04', activeUsers: 20, features: [{ name: 'check_in', users: 15, rate: 0.75 }, { name: 'link_copy', users: 0, rate: 0 }] },
  topErrors: { from: '2026-09-28', to: '2026-10-04', total: 7, codes: [{ code: 'DUPLICATE_VISIT', count: 5 }, { code: 'NETWORK_ERROR', count: 2 }] },
};

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('숫자 읽는 법', () => {
  it('비율은 백분율 소수 첫째 자리로, 분모가 0 이라 셀 수 없으면 0% 와 구분해 "—" 로 보인다', () => {
    expect(percentText(0.4167)).toBe('41.7%');
    expect(percentText(0)).toBe('0.0%');
    expect(percentText(null)).toBe('—');
  });

  it('아직 셀 수 없는 수는 빈칸, K 계수는 소수 둘째 자리', () => {
    expect(countText(null)).toBe('');
    expect(countText(1234)).toBe('1,234');
    expect(kFactorText(0.6667)).toBe('0.67');
    expect(kFactorText(null)).toBe('—');
  });

  it('날짜는 월/일로 줄여 보인다', () => {
    expect(shortDay('2026-10-04')).toBe('10/4');
  });
});

describe('머리 숫자와 추이', () => {
  it('오늘의 DAU·WAU·MAU·새 방문·새 탐험가와 30일 K 계수를 서버 값 그대로 보인다', () => {
    expect(headlines(METRICS).map(headline => [headline.key, headline.value])).toEqual([
      ['dau', '12'], ['wau', '40'], ['mau', '90'], ['new-visitors', '3'], ['new-explorers', '1'], ['k-factor', '0.25'],
    ]);
    expect(headlines(METRICS).at(-1)?.note).toBe('3 / 12 · 30일');
  });

  it('활동 추이는 DAU·WAU·MAU, 신규 추이는 새 방문·새 탐험가를 오래된 날부터 고정 색 순서로 그린다', () => {
    const active = activityTrend(dayEntries(METRICS));
    expect(active.days).toEqual(['2026-10-02', '2026-10-03', '2026-10-04']);
    expect(active.series.map(series => [series.key, series.slot])).toEqual([['dau', 1], ['wau', 2], ['mau', 3]]);
    expect(active.series[0]?.values).toEqual([10, 10, 12]);
    expect(newcomerTrend(dayEntries(METRICS)).series.map(series => series.label)).toEqual(['새 방문', '새 탐험가']);
  });

  it('세로축 끝은 가장 큰 값 이상의 깔끔한 수이고, 모두 0 이어도 축이 선다', () => {
    expect(niceMax([0, 7, 3])).toBe(10);
    expect(niceMax([120])).toBe(200);
    expect(niceMax([0, 0])).toBe(1);
  });

  it('선 끝 이름표가 겹치면 벌려 놓는다', () => {
    expect(spreadLabels([100, 104, 50], 14)).toEqual([100, 114, 50]);
  });
});

describe('셀 수 없는 날', () => {
  // 서버 daily[] 에는 센 날만 온다 — 10/1 은 원본 보관 기간이 지났고, 10/2 는 아직 배치 전
  const GAPPED = {
    from: '2026-09-30', to: '2026-10-04', daily: [day('2026-09-30'), day('2026-10-03'), day('2026-10-04', { live: true })],
    missingDays: ['2026-10-02'], expiredDays: ['2026-10-01'],
  };

  it('조회 구간의 모든 날을 오래된 날부터 늘어놓는다', () => {
    expect(daysBetween('2026-09-29', '2026-10-02')).toEqual(['2026-09-29', '2026-09-30', '2026-10-01', '2026-10-02']);
  });

  it('보관 기간이 지난 날과 배치 전인 날을 센 날과 구분하고, 그날 숫자는 0 이 아니라 비워 둔다', () => {
    expect(dayEntries(GAPPED).map(entry => [entry.day, entry.status])).toEqual([
      ['2026-09-30', 'counted'], ['2026-10-01', 'expired'], ['2026-10-02', 'missing'], ['2026-10-03', 'counted'], ['2026-10-04', 'counted'],
    ]);
    expect(activityTrend(dayEntries(GAPPED)).series[0]?.values).toEqual([10, null, null, 10, 10]);
  });

  it('차트에는 이어진 셀 수 없는 날을 상태별 띠로 묶어 보인다', () => {
    expect(gapBands(['counted', 'expired', 'expired', 'missing', 'counted', 'expired'])).toEqual([
      { status: 'expired', start: 1, end: 2 }, { status: 'missing', start: 3, end: 3 }, { status: 'expired', start: 5, end: 5 },
    ]);
  });

  it('선 끝 이름표는 마지막으로 센 날의 값이다', () => {
    expect(lastValue([3, 5, null])).toEqual({ index: 1, value: 5 });
    expect(lastValue([null, null])).toBeNull();
  });

  it('보관 기간이 지난 날이 있으면 빈 날이 아니라고 알려 주고, 퍼널·리텐션 표에도 같은 이유를 적는다', () => {
    expect(expiredDaysText(['2026-07-01', '2026-07-02', '2026-07-03'])).toBe('원본 보관 기간이 지나 다시 셀 수 없는 날 3일(7/1 ~ 7/3) — 빈 날(0)이 아니라 숫자가 없는 날이에요');
    expect(expiredCohortNote(['2026-07-01'])).toBe('7/1 코호트(1일)는 보관 기간 지남 — 표에 없는 것은 0 명이어서가 아니에요');
    expect(expiredDaysText([])).toBeNull();
    expect(expiredCohortNote([])).toBeNull();
  });
});

describe('퍼널', () => {
  it('최근 코호트부터, 첫 화면이 있었던 날과 오늘만 보이고 아직 바뀔 수 있는 코호트는 "집계 중"이다', () => {
    const rows = funnelRows(METRICS.funnel, METRICS.to);
    expect(rows.map(row => [row.cohortDay, row.firstScreen, row.firstCheckIn, row.status])).toEqual([
      ['2026-10-04', '2', '1', '집계 중'],
      ['2026-10-02', '10', '4', '확정'],
    ]);
    expect(rows[1]).toMatchObject({ checkInRate: '40.0%', revisitRate: '25.0%', overallRate: '10.0%' });
  });

  it('오늘 첫 화면이 아직 없어도 오늘 줄은 보인다', () => {
    const rows = funnelRows([funnel('2026-10-04', { firstScreen: 0 })], '2026-10-04');
    expect(rows).toHaveLength(1);
    expect(rows[0]?.today).toBe(true);
  });
});

describe('배치가 아직 코호트를 계산하지 않은 날', () => {
  it('퍼널 표에 0 명이 아니라 "배치 전" 줄로 날짜 순서에 끼워 보인다', () => {
    const rows = funnelRows(METRICS.funnel, METRICS.to, ['2026-10-03']);
    expect(rows.map(row => [row.cohortDay, row.status, row.pending?.days ?? 0])).toEqual([
      ['2026-10-04', '집계 중', 0], ['2026-10-03', '배치 전', 1], ['2026-10-02', '확정', 0],
    ]);
    expect(rows[1]).toMatchObject({ firstScreen: '', firstCheckIn: '', checkInRate: '' });
  });

  it('리텐션 표에도 같은 날을 "배치 전" 줄로 보이고 칸은 비워 둔다', () => {
    const rows = retentionRows(METRICS.retention, ['2026-10-03']);
    expect(rows.map(row => [row.cohortDay, !!row.pending])).toEqual([['2026-10-04', false], ['2026-10-03', true], ['2026-10-02', false]]);
    expect(rows[1]?.cells.d1).toEqual({ count: '', rate: '', shade: null });
  });
});

describe('배치 전 코호트가 여러 날 이어질 때', () => {
  it('이어진 날은 한 줄로 묶어 기간과 날 수를 적는다(떨어진 날은 따로)', () => {
    expect(pendingSpans(['2026-09-09', '2026-09-10', '2026-09-11', '2026-09-20']).map(span => [span.last, span.days, span.text])).toEqual([
      ['2026-09-11', 3, '배치 전 · 9/9 ~ 9/11 (3일) — 배치를 실행하면 채워져요'],
      ['2026-09-20', 1, '배치 전 — 배치를 실행하면 채워져요'],
    ]);
  });

  it('달을 넘어 이어진 날도 한 묶음이다', () => {
    expect(pendingSpans(['2026-09-30', '2026-10-01']).map(span => span.days)).toEqual([2]);
  });
});

describe('30일 구간 앞부분 원본이 지워진 뒤 계산한 날', () => {
  it('그런 날이 있으면 MAU·K 계수의 신뢰도가 낮다고 알리고, 없으면 아무 말도 하지 않는다', () => {
    expect(partialWindowText([day('2026-07-01', { partialWindow: true }), day('2026-07-02')]))
      .toBe('1일은 30일 구간 앞부분 원본이 지워진 뒤 늦게 계산해 MAU·K 계수가 실제보다 작게 나올 수 있어요(신뢰도 낮음)');
    expect(partialWindowText([day('2026-07-02')])).toBeNull();
  });
});

describe('리텐션', () => {
  it('가입한 탐험가가 있는 코호트만 최근부터 보이고, 아직 그날이 지나지 않은 칸은 빈칸이다', () => {
    const rows = retentionRows(METRICS.retention);
    expect(rows.map(row => row.cohortDay)).toEqual(['2026-10-04', '2026-10-02']);
    expect(rows[0]?.cells.d1).toEqual({ count: '', rate: '', shade: null });
    expect(rows[1]?.cells.d1).toEqual({ count: '2', rate: '40.0%', shade: 0.4 });
    expect(rows[1]?.cells.d30.shade).toBeNull();
  });
});

describe('기능과 오류', () => {
  it('기능 사용률은 서버 순서 그대로, 아무도 안 쓴 기능도 0 으로 보인다', () => {
    expect(featureBars(METRICS.featureUsage.features)).toEqual([
      { name: 'check_in', label: '체크인', users: '15', rate: '75.0%', width: 75 },
      { name: 'link_copy', label: '링크 복사', users: '0', rate: '0.0%', width: 0 },
    ]);
  });

  it('토큰이 없거나 틀리면 그 이유를 알려 준다', () => {
    expect(adminErrorText(new ApiError(401, 'ADMIN_TOKEN_REQUIRED', ''))).toBe('관리자 토큰을 입력해 주세요.');
    expect(adminErrorText(new ApiError(403, 'ADMIN_TOKEN_INVALID', ''))).toContain('맞지 않아요');
    expect(adminErrorText(new TypeError('offline'))).toContain('불러오지 못했어요');
  });

  it('배치가 비워 둔 날이 있으면 몇 날인지 알려 주고, 없으면 안내하지 않는다', () => {
    expect(missingDaysText(['2026-10-01', '2026-10-02'])).toBe('아직 계산하지 않은 날 2일(10/1, 10/2) — 배치를 실행하면 채워져요');
    expect(missingDaysText([])).toBeNull();
  });
});

describe('토큰을 넣고 보기', () => {
  const open = (token: string) => {
    fireEvent.change(screen.getByLabelText('관리자 토큰'), { target: { value: token } });
    fireEvent.click(screen.getByText('지표 보기'));
  };

  it('토큰 없이 열면 서버가 거절한 이유를 보여 주고 지표는 보이지 않는다', async () => {
    vi.mocked(adminApi.metrics).mockRejectedValueOnce(new ApiError(401, 'ADMIN_TOKEN_REQUIRED', 'required'));
    render(<AdminApp />);
    open('');
    expect((await screen.findByRole('alert')).textContent).toBe('관리자 토큰을 입력해 주세요.');
    expect(document.getElementById('admin-metrics')).toBeNull();
  });

  it('맞는 토큰이면 30일 지표를 읽어 오늘 퍼널·리텐션·오류를 보여 주고, 토큰은 어디에도 저장하지 않는다', async () => {
    vi.mocked(adminApi.metrics).mockResolvedValueOnce(METRICS);
    render(<AdminApp />);
    open('local-admin-token');
    await screen.findByText('코호트 리텐션');
    expect(adminApi.metrics).toHaveBeenCalledWith('local-admin-token', 30);
    const today = document.querySelector('#admin-funnel tr[data-cohort-day="2026-10-04"]');
    expect(today?.querySelector('[data-funnel="first-screen"]')?.textContent).toBe('2');
    expect(today?.querySelector('[data-funnel="first-check-in"]')?.textContent).toBe('1');
    expect(document.querySelector('#m-dau')?.getAttribute('data-value')).toBe('12');
    expect(document.querySelectorAll('#admin-retention tbody tr')).toHaveLength(2);
    expect(document.querySelector('[data-error-code="DUPLICATE_VISIT"]')?.textContent).toContain('5');
    expect((screen.getByLabelText('관리자 토큰') as HTMLInputElement).value).toBe('');
    expect(JSON.stringify({ ...localStorage })).not.toContain('local-admin-token');
    expect(JSON.stringify({ ...sessionStorage })).not.toContain('local-admin-token');
  });

  it('잠그면 지표를 감추고 다시 토큰을 물어본다', async () => {
    vi.mocked(adminApi.metrics).mockResolvedValueOnce(METRICS);
    render(<AdminApp />);
    open('local-admin-token');
    await screen.findByText('코호트 리텐션');
    fireEvent.click(screen.getByText('잠그기'));
    expect(document.getElementById('admin-metrics')).toBeNull();
    expect(document.getElementById('admin-login')?.hidden).toBe(false);
  });

  it('보관 기간이 지난 날은 배치 실행 대신 "보관 기간 지남"으로 표와 차트에 따로 보인다', async () => {
    vi.mocked(adminApi.metrics).mockResolvedValueOnce({
      ...METRICS, from: '2026-10-01', daily: METRICS.daily, expiredDays: ['2026-10-01'],
    });
    render(<AdminApp />);
    open('local-admin-token');
    await screen.findByText('코호트 리텐션');
    expect(document.getElementById('admin-missing')).toBeNull();
    expect(document.getElementById('admin-expired')?.textContent).toContain('다시 셀 수 없는 날 1일');
    expect(document.querySelector('#admin-daily tr[data-day="2026-10-01"]')?.textContent).toContain('보관 기간 지남');
    expect(document.querySelector('#chart-active rect[data-gap="expired"]')).not.toBeNull();
    expect(document.querySelector('#admin-retention [data-gap="expired"]')).not.toBeNull();
  });

  it('배치 전 코호트는 퍼널·리텐션 표에 "배치 전"으로, 신뢰도 낮은 날은 일별 표의 MAU·K 계수 옆에 표시한다', async () => {
    vi.mocked(adminApi.metrics).mockResolvedValueOnce({
      ...METRICS, pendingCohortDays: ['2026-10-03'],
      daily: [day('2026-10-02', { partialWindow: true }), day('2026-10-03'), day('2026-10-04', { live: true, dau: 12 })],
    });
    render(<AdminApp />);
    open('local-admin-token');
    await screen.findByText('코호트 리텐션');
    expect(document.querySelector('#admin-funnel tr[data-cohort-day="2026-10-03"]')?.textContent).toContain('배치 전');
    expect(document.querySelector('#admin-retention tr[data-cohort-day="2026-10-03"]')?.textContent).toContain('배치 전');
    const partial = document.querySelector('#admin-daily tr[data-day="2026-10-02"]');
    expect(partial?.querySelectorAll('[data-low-confidence]')).toHaveLength(2);
    expect(document.querySelector('#admin-daily tr[data-day="2026-10-03"] [data-low-confidence]')).toBeNull();
    expect(document.getElementById('admin-partial')?.textContent).toContain('신뢰도 낮음');
  });

  it('배치가 비워 둔 날이 있으면 배치를 실행하고 지표를 다시 읽는다', async () => {
    vi.mocked(adminApi.metrics).mockResolvedValue({ ...METRICS, missingDays: ['2026-10-03'] });
    render(<AdminApp />);
    open('local-admin-token');
    const run = await screen.findByText('배치 실행');
    await act(async () => {
      fireEvent.click(run);
    });
    expect(adminApi.runBatch).toHaveBeenCalledWith('local-admin-token');
    await waitFor(() => expect(vi.mocked(adminApi.metrics).mock.calls.length).toBeGreaterThanOrEqual(2));
  });
});

// ---- 계절 회차(13s단계) ----

const FETCHED = '2026-10-05T04:00:00Z';
const festivalRegion = (code: string, name: string, title: string): LineupRegionResponse => ({
  code, name, provinceCode: code.slice(0, 5), provenance: 'tourapi',
  evidence: [{ contentId: 'c-' + code, title, startDate: '2027-03-25', endDate: '2027-04-05', fetchedAt: FETCHED, evidenceKind: 'FESTIVAL' }],
});
const attractionRegion = (code: string, name: string, title: string): LineupRegionResponse => ({
  code, name, provinceCode: code.slice(0, 5), provenance: 'tourapi',
  evidence: [{ contentId: 'a-' + code, title, startDate: null, endDate: null, fetchedAt: FETCHED, evidenceKind: 'ATTRACTION' }],
});
const estimatedRegion = (code: string, name: string): LineupRegionResponse => ({ code, name, provinceCode: code.slice(0, 5), provenance: 'ai-estimate', evidence: [] });

const SCHEDULE = { leadDays: 30, recollectAfterHours: 168, autoConfirm: true, autoConfirmMinRegions: 10 };
const lineupRound = (overrides: Partial<RoundLineupResponse> = {}): RoundLineupResponse => ({
  roundId: 'spring-2027', seasonId: 'spring', name: '2027 벚꽃 명소', emoji: '🌸', year: 2027,
  startsAt: '2027-03-19T15:00:00Z', endsAt: '2027-04-30T15:00:00Z', locked: false, collectionOpensAt: '2027-02-17T15:00:00Z', nextPlan: 'PREVIEW',
  inEffect: { provenance: 'ai-estimate', source: null, confirmedBy: null, confirmedAt: null, collectedAt: null, regions: [estimatedRegion('KR-38110', '창원시')] },
  candidate: null, lastAttempt: null, warnings: [], ...overrides,
});
const CANDIDATE = {
  provenance: 'mixed' as const, source: '한국관광공사 TourAPI', collectedAt: FETCHED, evidencedRegions: 2,
  regions: [festivalRegion('KR-11650', '서초구', '[개발용] 양재천 벚꽃 축제'), attractionRegion('KR-43150', '제천시', '청풍호 벚꽃길'), estimatedRegion('KR-38110', '창원시')],
  warnings: ['맞는 축제가 모자라 AI 추정으로 채웠어요'],
};
const LINEUPS: SeasonLineupsResponse = {
  tourApi: { configured: true, callsToday: 4, dailyLimit: 200, exhausted: false, source: '한국관광공사 TourAPI', warnings: [] },
  schedule: SCHEDULE,
  rounds: [
    lineupRound({
      roundId: 'autumn-2026', seasonId: 'autumn', name: '2026 단풍 명소', emoji: '🍁', year: 2026, locked: true, nextPlan: 'NONE',
      inEffect: { provenance: 'ai-estimate', source: null, confirmedBy: 'OPENING', confirmedAt: '2026-09-30T15:00:00Z', collectedAt: null, regions: [estimatedRegion('KR-31370', '가평군')] },
    }),
    lineupRound(),
  ],
};

describe('계절 회차 근거 읽는 법', () => {
  it('관리자 주소 #/admin/seasons 는 계절 회차를, 그 밖의 관리자 주소는 운영 지표를 연다', () => {
    expect(adminViewOf('#/admin/seasons')).toBe('seasons');
    expect(adminViewOf('#/admin')).toBe('metrics');
  });

  it('오늘 TourAPI 호출 수와 하루 예산, 자동 수집 정책을 서버 값 그대로 한 줄로 보인다', () => {
    expect(usageText(LINEUPS.tourApi)).toBe('오늘 TourAPI 호출 4 / 200회');
    expect(scheduleText(SCHEDULE)).toBe('회차 시작 30일 전부터 7일마다 모아요 · 근거 10곳이 모이면 자동 확정');
  });

  it('지역 표는 서버 순서가 곧 순위이고, 축제는 기간을 붙이고 관광지는 이름만, AI 추정은 근거가 없다', () => {
    expect(regionRows(CANDIDATE.regions)).toEqual([
      { rank: 1, code: '11650', name: '서초구', provenance: 'tourapi', sourceText: '한국관광공사 TourAPI', evidence: ['축제 「[개발용] 양재천 벚꽃 축제」 3/25~4/5'] },
      { rank: 2, code: '43150', name: '제천시', provenance: 'tourapi', sourceText: '한국관광공사 TourAPI', evidence: ['관광지 「청풍호 벚꽃길」'] },
      { rank: 3, code: '38110', name: '창원시', provenance: 'ai-estimate', sourceText: 'AI 추정(검증 전)', evidence: [] },
    ]);
  });

  it('카탈로그에서 못 찾은 지역은 이름 대신 코드를 보인다', () => {
    expect(regionRows([{ ...estimatedRegion('KR-99999', '없음'), name: null, provinceCode: null }])[0]?.name).toBe('KR-99999');
  });

  it('후보의 근거 지역이 자동 확정 기준보다 적으면 몇 곳뿐인지 경고하고, 기준을 채우면 경고하지 않는다', () => {
    expect(shortageText(CANDIDATE, SCHEDULE)).toBe('근거 지역 2곳 — 10곳 미만이라 자동 확정하지 않아요(나머지는 AI 추정으로 채움)');
    expect(shortageText({ ...CANDIDATE, evidencedRegions: 10 }, SCHEDULE)).toBeNull();
    expect(shortageText(null, SCHEDULE)).toBeNull();
  });

  it('쓰는 목록이 누가 정한 것인지와 마지막 수집 결과를 우리말로 보인다', () => {
    expect(confirmedByText(null)).toBe('기본 목록(확정 전)');
    expect(confirmedByText('OPENING')).toBe('열릴 때 고정');
    expect(confirmedByText('ADMIN')).toBe('관리자 확정');
    expect(attemptOutcomeText('QUOTA_EXCEEDED')).toBe('호출 한도 초과');
    expect(attemptOutcomeText('NOT_CONFIGURED')).toBe('키 없음 — 부르지 않음');
  });

  it('열렸거나 지난 회차는 새로 모을 수도 확정할 수도 없고, 후보가 없으면 확정할 수 없다', () => {
    expect(canRefresh(lineupRound({ locked: true }))).toBe(false);
    expect(canConfirm(lineupRound({ locked: true, candidate: CANDIDATE }))).toBe(false);
    expect(canRefresh(lineupRound())).toBe(true);
    expect(canConfirm(lineupRound())).toBe(false);
    expect(canConfirm(lineupRound({ candidate: CANDIDATE }))).toBe(true);
  });

  it('서버가 거절한 이유를 종류마다 알려 준다', () => {
    expect(lineupErrorText(new ApiError(409, 'SEASON_ROUND_LOCKED', ''))).toContain('바꿀 수 없어요');
    expect(lineupErrorText(new ApiError(409, 'SEASON_CANDIDATE_MISSING', ''))).toBe('확정할 후보가 없어요. 먼저 새로 모아 주세요.');
    expect(lineupErrorText(new ApiError(409, 'SEASON_LINEUP_BUSY', ''))).toContain('고치는 중');
    expect(lineupErrorText(new ApiError(404, 'SEASON_ROUND_NOT_FOUND', ''))).toContain('그런 회차가 없어요');
    expect(lineupErrorText(new ApiError(403, 'ADMIN_TOKEN_INVALID', ''))).toContain('맞지 않아요');
    expect(lineupErrorText(new TypeError('offline'))).toContain('처리하지 못했어요');
  });
});

describe('계절 회차 화면', () => {
  const openSeasons = async (token = 'local-admin-token') => {
    window.location.hash = '#/admin/seasons';
    render(<AdminApp />);
    fireEvent.change(screen.getByLabelText('관리자 토큰'), { target: { value: token } });
    fireEvent.click(screen.getByText('회차 보기'));
  };
  afterEach(() => {
    window.location.hash = '';
  });

  it('토큰을 넣으면 TourAPI 사용량과 회차 목록을 보이고, 열린 회차는 버튼이 잠겨 있으며 토큰은 어디에도 저장하지 않는다', async () => {
    vi.mocked(adminApi.seasonLineups).mockResolvedValueOnce(LINEUPS);
    await openSeasons();
    await screen.findByText('오늘 TourAPI 호출 4 / 200회');
    expect(adminApi.seasonLineups).toHaveBeenCalledWith('local-admin-token');
    expect(adminApi.metrics).not.toHaveBeenCalled();
    const autumn = document.querySelector('.lineup-round[data-round="autumn-2026"]');
    expect(autumn?.getAttribute('data-locked')).toBe('true');
    expect((autumn?.querySelector('[data-action="refresh"]') as HTMLButtonElement).disabled).toBe(true);
    expect((autumn?.querySelector('[data-action="confirm"]') as HTMLButtonElement).disabled).toBe(true);
    const spring = document.querySelector('.lineup-round[data-round="spring-2027"]');
    expect((spring?.querySelector('[data-action="refresh"]') as HTMLButtonElement).disabled).toBe(false);
    expect((spring?.querySelector('[data-action="confirm"]') as HTMLButtonElement).disabled).toBe(true);
    expect(document.getElementById('admin-login')?.hidden).toBe(true);
    expect(JSON.stringify({ ...localStorage }) + JSON.stringify({ ...sessionStorage })).not.toContain('local-admin-token');
  });

  it('키가 없으면 그렇다고 알리고 서버가 준 경고를 그대로 보인다', async () => {
    vi.mocked(adminApi.seasonLineups).mockResolvedValueOnce({
      ...LINEUPS, tourApi: { ...LINEUPS.tourApi, configured: false, callsToday: 0, warnings: ['TourAPI 키가 없어요 — 계절 회차는 AI 추정 목록을 그대로 씁니다'] },
    });
    await openSeasons();
    expect((await screen.findByText('키 없음')).id).toBe('tourapi-key-state');
    expect(document.querySelector('#tourapi-warnings li')?.textContent).toContain('AI 추정 목록을 그대로');
  });

  it('새로 모으면 후보의 지역·순위·근거·출처를 상세로 보이고, 근거가 모자라면 경고한다', async () => {
    vi.mocked(adminApi.seasonLineups).mockResolvedValue(LINEUPS);
    const refreshed = lineupRound({ candidate: CANDIDATE, lastAttempt: { at: FETCHED, outcome: 'PARTIAL', failed: false, warnings: [] } });
    vi.mocked(adminApi.refreshSeasonLineup).mockResolvedValueOnce(refreshed);
    vi.mocked(adminApi.seasonLineup).mockResolvedValue(refreshed);
    await openSeasons();
    await screen.findByText('오늘 TourAPI 호출 4 / 200회');
    await act(async () => {
      fireEvent.click(document.querySelector('.lineup-round[data-round="spring-2027"] [data-action="refresh"]') as HTMLButtonElement);
    });
    expect(adminApi.refreshSeasonLineup).toHaveBeenCalledWith('local-admin-token', 'spring-2027', false);
    await waitFor(() => expect(document.querySelectorAll('#lineup-candidate tbody tr')).toHaveLength(3));
    const first = document.querySelector('#lineup-candidate tr[data-region="11650"]');
    expect(first?.textContent).toContain('축제 「[개발용] 양재천 벚꽃 축제」 3/25~4/5');
    expect(first?.textContent).toContain('한국관광공사 TourAPI');
    expect(document.querySelector('#lineup-candidate tr[data-region="38110"]')?.getAttribute('data-provenance')).toBe('ai-estimate');
    expect(document.getElementById('lineup-detail-shortage')?.textContent).toContain('10곳 미만');
    await waitFor(() => expect(vi.mocked(adminApi.seasonLineups).mock.calls.length).toBeGreaterThanOrEqual(2));
  });

  it('캐시 없이 새로 모으기는 같은 날 받아 둔 응답을 건너뛰라고 요청한다', async () => {
    vi.mocked(adminApi.seasonLineups).mockResolvedValue(LINEUPS);
    vi.mocked(adminApi.refreshSeasonLineup).mockResolvedValueOnce(lineupRound({ candidate: CANDIDATE }));
    vi.mocked(adminApi.seasonLineup).mockResolvedValue(lineupRound({ candidate: CANDIDATE }));
    await openSeasons();
    await screen.findByText('오늘 TourAPI 호출 4 / 200회');
    await act(async () => {
      fireEvent.click(document.querySelector('.lineup-round[data-round="spring-2027"] [data-action="refresh-fresh"]') as HTMLButtonElement);
    });
    expect(adminApi.refreshSeasonLineup).toHaveBeenCalledWith('local-admin-token', 'spring-2027', true);
  });

  it('후보를 확정하면 쓰는 목록이 관리자 확정으로 바뀐다', async () => {
    const withCandidate = { ...LINEUPS, rounds: [LINEUPS.rounds[0] as RoundLineupResponse, lineupRound({ candidate: CANDIDATE })] };
    const confirmed = lineupRound({ inEffect: { ...CANDIDATE, confirmedBy: 'ADMIN', confirmedAt: FETCHED } });
    vi.mocked(adminApi.seasonLineups).mockResolvedValueOnce(withCandidate).mockResolvedValue({ ...LINEUPS, rounds: [LINEUPS.rounds[0] as RoundLineupResponse, confirmed] });
    vi.mocked(adminApi.confirmSeasonLineup).mockResolvedValueOnce(confirmed);
    vi.mocked(adminApi.seasonLineup).mockResolvedValue(confirmed);
    await openSeasons();
    await screen.findByText('오늘 TourAPI 호출 4 / 200회');
    await act(async () => {
      fireEvent.click(document.querySelector('.lineup-round[data-round="spring-2027"] [data-action="confirm"]') as HTMLButtonElement);
    });
    expect(adminApi.confirmSeasonLineup).toHaveBeenCalledWith('local-admin-token', 'spring-2027');
    await waitFor(() => expect(document.querySelector('.lineup-round[data-round="spring-2027"]')?.getAttribute('data-confirmed-by')).toBe('ADMIN'));
    expect(document.querySelectorAll('#lineup-in-effect tbody tr')).toHaveLength(3);
  });

  it('보는 사이 회차가 열려 서버가 바꿀 수 없다고 거절하면 그 이유를 회차 카드에 알려 준다', async () => {
    vi.mocked(adminApi.seasonLineups).mockResolvedValue(LINEUPS);
    vi.mocked(adminApi.refreshSeasonLineup).mockRejectedValueOnce(new ApiError(409, 'SEASON_ROUND_LOCKED', 'locked'));
    await openSeasons();
    await screen.findByText('오늘 TourAPI 호출 4 / 200회');
    await act(async () => {
      fireEvent.click(document.querySelector('.lineup-round[data-round="spring-2027"] [data-action="refresh"]') as HTMLButtonElement);
    });
    const error = await screen.findByRole('alert');
    expect(error.textContent).toContain('이미 열렸거나 지난 회차라');
    expect(error.getAttribute('data-code')).toBe('SEASON_ROUND_LOCKED');
  });

  it('지난 회차는 id 로 찾아 그때 쓴 목록을 보고, 없는 회차면 없다고 알려 준다', async () => {
    vi.mocked(adminApi.seasonLineups).mockResolvedValue(LINEUPS);
    vi.mocked(adminApi.seasonLineup).mockRejectedValueOnce(new ApiError(404, 'SEASON_ROUND_NOT_FOUND', 'missing'));
    await openSeasons();
    await screen.findByText('오늘 TourAPI 호출 4 / 200회');
    fireEvent.change(screen.getByLabelText('다른 회차 보기'), { target: { value: 'winter-2027' } });
    fireEvent.click(screen.getByText('보기'));
    expect((await screen.findByRole('alert')).textContent).toContain('그런 회차가 없어요');
    expect(adminApi.seasonLineup).toHaveBeenCalledWith('local-admin-token', 'winter-2027');
  });
});
