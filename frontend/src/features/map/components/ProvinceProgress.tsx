import { useUiStore } from '../../../store/uiStore';
import { useCatalog } from '../../../shared/queries/catalog';
import { conqueredProvinceCodes } from '../../../shared/lib/progress/news';
import { useProgress } from '../../../shared/queries/progress';
import { provinceRows } from '../model/territory';
import { useMyTerritory } from '../../../shared/queries/territory';

/** 시·도별 정복률(서버 값) — 누르면 그 시·도로 확대. 👑 = 탐험가 단위 정복 기록(GET /progress) */
export function ProvinceProgress() {
  const catalog = useCatalog();
  const { territory } = useMyTerritory();
  const { data: progress } = useProgress();
  const sendMapCommand = useUiStore(state => state.sendMapCommand);
  const rows = catalog ? provinceRows(territory, catalog, conqueredProvinceCodes(progress?.provinces)) : [];
  const zoomTo = (province: string) => {
    if (!catalog) return;
    sendMapCommand({ kind: 'zoom', codes: catalog.features.filter(feature => feature.properties.prov === province).map(feature => feature.properties.code) });
  };
  return (
    <div className="card">
      <h2>시·도별 정복률 <span>클릭하면 확대 · 👑 정복</span></h2>
      <div className="prov" id="prov">
        {rows.map(row => {
          const full = row.ratio === 1;
          return (
            <button key={row.name} data-prov={row.name} data-crowned={String(row.crowned)} onClick={() => zoomTo(row.name)}>
              <span className="n">{row.crowned ? <i className="crown" title="정복한 시·도">👑</i> : null}{row.name}</span>
              <span className="b"><i className={full ? 'full' : ''} style={{ width: `${Math.round(row.ratio * 100)}%` }} /></span>
              <span className={`c ${full ? 'full' : ''}`}>{full ? '정복' : `${row.visited}/${row.total}`}</span>
            </button>
          );
        })}
      </div>
    </div>
  );
}
