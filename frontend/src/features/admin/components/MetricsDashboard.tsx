import type { MetricsResponse } from '../../../api/types/analytics';
import { activityTrend, dayEntries, expiredDaysText, newcomerTrend } from '../model/metrics';
import { BatchNotice } from './BatchNotice';
import { DailyTable } from './DailyTable';
import { FeatureUsageCard } from './FeatureUsageCard';
import { FunnelTable } from './FunnelTable';
import { HeadlineStats } from './HeadlineStats';
import { RetentionTable } from './RetentionTable';
import { TopErrorsCard } from './TopErrorsCard';
import { TrendChart } from './TrendChart';

/** 지표 한 화면: 머리 숫자 → 추이 → 퍼널 → 리텐션 → 기능·오류 */
export function MetricsDashboard({ metrics, adminToken }: { metrics: MetricsResponse; adminToken: string }) {
  const entries = dayEntries(metrics);
  const expired = expiredDaysText(metrics.expiredDays);
  return (
    <div className="admin-body" id="admin-metrics" data-from={metrics.from} data-to={metrics.to}>
      <p className="note" id="admin-range">
        {metrics.from} ~ {metrics.to} ({metrics.timeZone}) · 오늘 값은 실시간 · 마지막 배치 {metrics.lastBatchAt ? new Date(metrics.lastBatchAt).toLocaleString('ko-KR') : '없음'}
      </p>
      <BatchNotice missingDays={metrics.missingDays} adminToken={adminToken} />
      {expired ? <p className="admin-notice expired" id="admin-expired" data-days={metrics.expiredDays.length}>{expired}</p> : null}
      <HeadlineStats metrics={metrics} />
      <div className="admin-grid">
        <div className="card">
          <h2>활동 사용자 <span>그날로 끝나는 1·7·30일</span></h2>
          <TrendChart id="chart-active" trend={activityTrend(entries)} />
        </div>
        <div className="card">
          <h2>신규 <span>그날 처음 본 방문 · 가입</span></h2>
          <TrendChart id="chart-new" trend={newcomerTrend(entries)} />
        </div>
      </div>
      <DailyTable entries={entries} />
      <FunnelTable funnel={metrics.funnel} today={metrics.to} expiredDays={metrics.expiredDays} />
      <RetentionTable retention={metrics.retention} expiredDays={metrics.expiredDays} />
      <div className="admin-grid">
        <FeatureUsageCard usage={metrics.featureUsage} />
        <TopErrorsCard errors={metrics.topErrors} kFactor={metrics.kFactor} />
      </div>
    </div>
  );
}
