import { useMemo } from 'react';
import { useRecap } from '../../../shared/queries/recap';
import { useUiStore } from '../../../store/uiStore';
import { recapView } from '../model/recap';

/** 연간 리캡 — 지금 보는 지도에서 내가 칠한 곳의 올해 값(서버 GET /me/recap). 문장만 화면이 만든다. */
export function RecapCard() {
  const mapId = useUiStore(state => state.mapId);
  const year = new Date().getFullYear();
  const { data: recap } = useRecap(year, mapId);
  const { cells, months } = useMemo(() => recapView(recap), [recap]);
  const peak = Math.max(1, ...months);
  return (
    <div className="card">
      <h2>연간 리캡 <span id="recap-year">{recap?.year ?? year}년</span></h2>
      <div className="recap" id="recap">
        {cells.map(([label, value]) => <div key={label}><div className="k">{label}</div><div className="v">{value}</div></div>)}
      </div>
      <div className="monthwrap">
        <div className="months" id="months">
          {months.map((count, i) => (
            <div key={i} className={count ? '' : 'zero'} style={{ height: `${Math.max(3, 100 * count / peak)}%` }}><span>{i + 1}</span></div>
          ))}
        </div>
      </div>
    </div>
  );
}
