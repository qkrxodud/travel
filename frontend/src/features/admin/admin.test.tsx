/**
 * 운영 지표 화면 — 운영자는 관리자 토큰을 넣고 서버가 센 숫자(오늘 활동·추이·퍼널·리텐션·K 계수·기능 사용률·오류)를 그대로 본다.
 * 이야기 순서: 숫자 읽는 법 → 머리 숫자·추이 → 퍼널 → 리텐션 → 기능·오류 → 토큰을 넣고 보기 → 배치가 비워 둔 날.
 */
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { adminApi } from '../../api/admin';
import { ApiError } from '../../api/client';
import type { DailyView, FunnelView, MetricsResponse, RetentionView } from '../../api/types/analytics';
import { AdminApp } from './components/AdminApp';
import {
  activityTrend, adminErrorText, dayEntries, daysBetween, expiredCohortNote, expiredDaysText, gapBands, lastValue, countText, featureBars, funnelRows, headlines, kFactorText, missingDaysText, newcomerTrend, niceMax,
  percentText, retentionRows, shortDay, spreadLabels,
} from './model/metrics';

vi.mock('../../api/admin', () => ({ adminApi: { metrics: vi.fn(), runBatch: vi.fn(async () => ({ from: '', to: '', dailyDays: 3, cohortDays: 35, purged: 0, elapsedMs: 5 })) } }));

const day = (date: string, overrides: Partial<DailyView> = {}): DailyView => ({
  day: date, newVisitors: 4, newExplorers: 2, dau: 10, wau: 30, mau: 60, profileViews: 1, cardViews: 2, botViews: 0, kFactor: 0.1, live: false, ...overrides,
});
const funnel = (cohortDay: string, overrides: Partial<FunnelView> = {}): FunnelView => ({
  cohortDay, firstScreen: 10, firstCheckIn: 4, revisitedWithin7Days: 1, checkInRate: 0.4, revisitRate: 0.25, overallRate: 0.1, settled: true, ...overrides,
});
const retention = (cohortDay: string, overrides: Partial<RetentionView> = {}): RetentionView => ({
  cohortDay, newExplorers: 5, d1: 2, d7: 1, d30: null, d1Rate: 0.4, d7Rate: 0.2, d30Rate: null, ...overrides,
});

const METRICS: MetricsResponse = {
  generatedAt: '2026-10-04T03:00:00Z', timeZone: 'Asia/Seoul', from: '2026-10-02', to: '2026-10-04', lastBatchAt: null, missingDays: [], expiredDays: [],
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
