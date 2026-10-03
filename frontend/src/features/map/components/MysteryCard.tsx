import { toClientCode } from '../../../api/client';
import { useMysteryThisWeek } from '../../../shared/queries/mystery';
import { useUiStore } from '../../../store/uiStore';
import { mysteryCard } from '../model/mystery';

/** 이번 주 미스터리(#mystery-card) — 누르면 지도에서 그 지역을 보여 준다. 지역·남은 기간·보너스·받음은 서버 값 */
export function MysteryCard() {
  const { data: week } = useMysteryThisWeek();
  const revealed = useUiStore(state => state.mysteryRevealed);
  const revealMystery = useUiStore(state => state.revealMystery);
  if (!week) return null;
  const view = mysteryCard(week, revealed);
  const code = toClientCode(week.region.code);
  return (
    <div className={`card mystery ${view.received ? 'received' : ''}`} id="mystery-card" data-received={String(view.received)} data-week={week.weekStart}>
      <h2>이번 주 미스터리 <span id="mystery-left">{view.remaining}</span></h2>
      <button className="mystery-body" id="mystery-show" data-revealed={String(view.revealed)} onClick={() => revealMystery(code)}>
        <span className="q" aria-hidden="true">{view.received ? '✓' : '❓'}</span>
        <span className="t">
          <b id="mystery-name">{view.title}</b>
          <small>{view.hint}</small>
        </span>
        <span className="x" id="mystery-bonus">{view.received ? '받음 ✓' : view.bonus}</span>
      </button>
    </div>
  );
}
