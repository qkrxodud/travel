import { toClientCode } from '../../../api/client';
import { openRound, seasonBadgeText } from '../../../shared/lib/progress/season';
import { useSeasons } from '../../../shared/queries/seasons';
import { useUiStore } from '../../../store/uiStore';

/** 지도 카드 위 계절 한정 배지(#season-badge) — 계절 기간에만 보인다. 누르면 회차 지역을 지도에서 함께 강조한다 */
export function SeasonBadge() {
  const { data: seasons } = useSeasons();
  const showRegionsOnMap = useUiStore(state => state.showRegionsOnMap);
  const round = openRound(seasons);
  if (!round) return null;
  const codes = round.regions.map(region => toClientCode(region.code));
  return (
    <button
      className={`season-badge ${round.seasonId} ${round.completed ? 'done' : ''}`}
      id="season-badge"
      data-round={round.roundId}
      data-completed={String(round.completed)}
      title={`계절 한정 · ${round.name} 지역을 지도에서 보기`}
      onClick={() => showRegionsOnMap(codes)}
    >
      {seasonBadgeText(round)}
    </button>
  );
}
