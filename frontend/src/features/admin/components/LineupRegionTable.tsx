import type { LineupRegionResponse } from '../../../api/types/catalog';
import { regionRows } from '../model/seasonLineups';

/** 회차 지역 표 — 순위(서버 순서)·지역·출처·근거(축제·관광지 이름, 축제는 기간) */
export function LineupRegionTable({ id, regions }: { id: string; regions: readonly LineupRegionResponse[] }) {
  return (
    <div className="table-scroll">
      <table className="admin-table lineup-table" id={id}>
        <thead>
          <tr><th scope="col">순위</th><th scope="col">지역</th><th scope="col">출처</th><th scope="col">근거</th></tr>
        </thead>
        <tbody>
          {regionRows(regions).map(row => (
            <tr key={row.code} data-region={row.code} data-provenance={row.provenance}>
              <td>{row.rank}</td>
              <th scope="row">{row.name}</th>
              <td><span className={`tag ${row.provenance === 'tourapi' ? 'done' : ''}`}>{row.sourceText}</span></td>
              <td className="lineup-evidence">{row.evidence.length ? row.evidence.join(' · ') : '—'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
