import { toClientCode } from '../../../api/client';
import { nextRoundText, openRound, pastRoundText, seasonRoundView } from '../../../shared/lib/progress/season';
import { useSeasons } from '../../../shared/queries/seasons';
import { Bar } from '../../../shared/ui/Bar';
import { useUiStore } from '../../../store/uiStore';
import { SeasonSources } from './SeasonSources';

/** 도감 탭 "계절 한정"(#season) — 지금 회차의 남은 기간·진행·완성 보상·지도에서 보기·추천 근거와 출처(13s), 다음 회차·지난 기록. 값은 전부 서버 값 */
export function SeasonSection() {
  const { data: seasons } = useSeasons();
  const showOnMap = useUiStore(state => state.showOnMap);
  const showRegionsOnMap = useUiStore(state => state.showRegionsOnMap);
  if (!seasons) return null;
  const round = openRound(seasons);
  const view = round ? seasonRoundView(round) : null;
  return (
    <div className="card season" id="season" style={{ marginBottom: 14 }} data-open={String(!!round)}>
      <h2>계절 한정 <span id="season-left">{view ? view.remaining : '기간 아님'}</span></h2>
      {round && view ? (
        <div className={`set season-round ${round.seasonId} ${view.completed ? 'done' : ''}`} data-round={round.roundId} data-completed={String(view.completed)}>
          <h3>{view.title}<span className="have" id="season-have">{view.have}</span></h3>
          <p className="d">{view.period} · 이 기간에 칠한 곳만 세어요(기간 전 방문은 다시 칠해야 들어가요)</p>
          <Bar percent={view.percent} gold={view.completed} />
          <div className="slots">
            {round.regions.map(region => {
              const code = toClientCode(region.code);
              return (
                <button key={region.code} className={`slot ${region.collected ? 'on' : ''}`} data-season-region={code} onClick={() => showOnMap(code)}>
                  {region.name}
                </button>
              );
            })}
          </div>
          <div className="reward" id="season-reward">{view.completed ? <b>{view.reward}</b> : view.reward}</div>
          <div className="row">
            <button className="btn sm" id="season-show" onClick={() => showRegionsOnMap(round.regions.map(region => toClientCode(region.code)))}>지도에서 보기</button>
          </div>
          <SeasonSources round={round} />
        </div>
      ) : (
        <p className="sub" style={{ margin: 0 }}>지금은 계절 한정 기간이 아니에요.</p>
      )}
      {seasons.next ? <p className="sub season-next" id="season-next">{nextRoundText(seasons.next)}</p> : null}
      {seasons.history.length ? (
        <ul className="season-history" id="season-history">
          {seasons.history.map(past => <li key={past.roundId} data-round={past.roundId} data-completed={String(past.completed)}>{pastRoundText(past)}</li>)}
        </ul>
      ) : null}
    </div>
  );
}
