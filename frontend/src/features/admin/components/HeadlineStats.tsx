import type { MetricsResponse } from '../../../api/types/analytics';
import { headlines } from '../model/metrics';

/** 오늘 머리 숫자(DAU·WAU·MAU·새 방문·새 탐험가·K 계수) */
export function HeadlineStats({ metrics }: { metrics: MetricsResponse }) {
  return (
    <div className="admin-stats" id="admin-today" data-day={metrics.today.day}>
      {headlines(metrics).map(headline => (
        <div className="stat" key={headline.key} id={`m-${headline.key}`} data-value={headline.raw ?? ''}>
          <div className="k">{headline.label}</div>
          <div className="v">{headline.value}</div>
          <div className="note">{headline.note}</div>
        </div>
      ))}
    </div>
  );
}
