import type { FeatureUsageView } from '../../../api/types/analytics';
import { countText, featureBars } from '../model/metrics';

/** 기능별 사용률(7일) — 쓴 사람 ÷ 활동한 사람 */
export function FeatureUsageCard({ usage }: { usage: FeatureUsageView }) {
  return (
    <div className="card" id="admin-features">
      <h2>기능별 사용률 <span>{usage.from} ~ {usage.to} · 활동 {countText(usage.activeUsers)}명</span></h2>
      <ul className="feature-bars">
        {featureBars(usage.features).map(bar => (
          <li key={bar.name} data-feature={bar.name}>
            <span className="label">{bar.label}</span>
            <span className="track"><i style={{ width: `${bar.width}%` }} /></span>
            <span className="value"><b>{bar.rate}</b> {bar.users}명</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
