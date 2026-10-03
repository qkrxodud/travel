import type { FriendRankingResponse, FriendRankRow } from '../../../api/types/social';
import { useCatalog } from '../../../shared/queries/catalog';
import { useMyTerritory } from '../../../shared/queries/territory';
import { useProgress } from '../../../shared/queries/progress';
import { avatarColor, displayHandle, initialOf, shareOfAll } from '../model/social';
import { useStartCompare } from '../queries';

/** 친구 랭킹(서로 팔로우한 친구 · 정복 지역 수). 서버 랭킹 전에는 내 줄만 보인다. 이름을 누르면 비교. */
export function FriendRankingCard({ ranking }: { ranking: FriendRankingResponse | null }) {
  const catalog = useCatalog();
  const { data: progress } = useProgress();
  const { visits } = useMyTerritory();
  const startCompare = useStartCompare();
  const total = catalog?.features.length ?? 0;
  const rows: FriendRankRow[] = ranking ? ranking.rows : [{
    explorerId: '', me: true, handle: null, rank: 1, regionCount: visits?.size ?? 0, level: progress?.level ?? 1,
    titleName: progress?.title?.name ?? '초보 탐험가',
  }];
  const baseline = ranking?.baseline ?? null;
  const mine = ranking ? (ranking.rows.find(row => row.me)?.regionCount ?? 0) : visits?.size ?? 0;
  const showBaseline = !!ranking && ranking.friendCount === 0;
  return (
    <div className="card" id="rank-card">
      <h2>친구 랭킹 <span>서로 팔로우한 친구 · 정복 지역 수 · 이름을 누르면 비교</span></h2>
      <ul className="rank" id="rank">
        {rows.map(row => (
          <li key={row.explorerId || 'me'} className={row.me ? 'me' : ''} data-handle={row.handle || ''}>
            <button data-vs={row.me ? '' : row.handle || ''} onClick={() => { if (!row.me && row.handle) void startCompare(row.handle); }}>
              <span className={`pos ${row.rank <= 3 ? 'top' : ''}`}>{row.rank}</span>
              <span className="av" style={{ background: row.me ? 'var(--accent)' : avatarColor(row.handle) }}>{row.me ? 'K' : initialOf(row.handle)}</span>
              <span className="nm">
                {row.me ? (row.handle ? `나 (@${row.handle})` : '나 (Kobi)') : displayHandle(row.handle)}
                <small>Lv.{row.level} · {row.titleName || ''}</small>
              </span>
              <span className="sc">{row.regionCount}<small>{shareOfAll(row.regionCount, total)}%</small></span>
            </button>
          </li>
        ))}
      </ul>
      <div className="baseline" id="rank-baseline" hidden={!showBaseline}>
        {showBaseline ? (baseline ? (
          <>친구가 아직 없어요 — <b>{baseline.nationwide ? '전국' : (catalog?.provinceByCode.get(baseline.provinceCode ?? '') ?? baseline.provinceCode)} 평균 유저</b>는 <b data-baseline-avg="">{baseline.averageRegionCount}</b>곳, 나는 <b>{mine}</b>곳 ({baseline.explorerCount.toLocaleString()}명 기준 · 하루 한 번 집계)</>
        ) : '친구가 아직 없어요 — 지역 평균은 하루 한 번 집계된 뒤에 보여요.') : null}
      </div>
    </div>
  );
}
