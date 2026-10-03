import { useUiStore } from '../../../store/uiStore';
import { useCatalog } from '../../../shared/queries/catalog';
import { provinceRows } from '../model/territory';
import { useMyTerritory } from '../../../shared/queries/territory';

/** 시·도별 정복률(서버 값) — 누르면 그 시·도로 확대 */
export function ProvinceProgress() {
  const catalog = useCatalog();
  const { territory } = useMyTerritory();
  const sendMapCommand = useUiStore(state => state.sendMapCommand);
  const rows = catalog ? provinceRows(territory, catalog) : [];
  const zoomTo = (province: string) => {
    if (!catalog) return;
    sendMapCommand({ kind: 'zoom', codes: catalog.features.filter(feature => feature.properties.prov === province).map(feature => feature.properties.code) });
  };
  return (
    <div className="card">
      <h2>시·도별 정복률 <span>클릭하면 확대</span></h2>
      <div className="prov" id="prov">
        {rows.map(row => {
          const full = row.ratio === 1;
          return (
            <button key={row.name} data-prov={row.name} onClick={() => zoomTo(row.name)}>
              <span className="n">{row.name}</span>
              <span className="b"><i className={full ? 'full' : ''} style={{ width: `${Math.round(row.ratio * 100)}%` }} /></span>
              <span className={`c ${full ? 'full' : ''}`}>{full ? '정복' : `${row.visited}/${row.total}`}</span>
            </button>
          );
        })}
      </div>
    </div>
  );
}
