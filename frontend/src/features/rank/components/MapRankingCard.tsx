import { useMyTerritory } from '../../../shared/queries/territory';
import { initialOf } from '../model/social';
import { useMapRanking } from '../queries';

/** 지도 안 랭킹(공유 지도일 때) — 영토·선점·전설, 이의 표시 방문은 빠진다 */
export function MapRankingCard({ active }: { active: boolean }) {
  const { detail } = useMyTerritory();
  const { data: ranking } = useMapRanking(detail?.mapId ?? null, active);
  const visible = !!(detail && ranking && ranking.mapId === detail.mapId);
  return (
    <div className="card" id="map-rank-card" hidden={!visible}>
      <h2>지도 안 랭킹 <span id="map-rank-sub">{visible && ranking ? `영토 · 선점 · 전설${ranking.disputedExcluded ? ` (이의 표시 ${ranking.disputedExcluded}건 제외)` : ' (이의 표시 제외)'}` : '영토 · 선점 · 전설 (이의 표시 제외)'}</span></h2>
      <ul className="rank" id="map-rank">
        {visible && ranking && detail ? ranking.rows.map(row => {
          const member = detail.members.find(candidate => candidate.explorerId === row.explorerId);
          return (
            <li key={row.explorerId} className={row.me ? 'me' : ''} data-explorer={row.explorerId}>
              <span className={`pos ${row.rank <= 3 ? 'top' : ''}`}>{row.rank}</span>
              <span className="av" style={{ background: member ? member.color : '#999' }}>{row.me ? 'K' : initialOf(row.handle, 'M')}</span>
              <span className="nm">{row.me ? '나' : row.handle ? '@' + row.handle : '멤버 ' + row.explorerId.slice(0, 4)}<small>선점 {row.claims} · 전설 {row.legends}</small></span>
              <span className="sc">{row.territories}<small>영토</small></span>
            </li>
          );
        }) : null}
      </ul>
    </div>
  );
}
