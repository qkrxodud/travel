import { useUiStore } from '../../../store/uiStore';
import { SeasonBadge } from './SeasonBadge';

/** 지도 위 도구: 칠하기 · 기록 보기 · 전체 보기 · 내 캐릭터로 (+ 계절 기간이면 계절 배지 — 같은 줄 끝, 좁으면 다음 줄로 내려가 버튼을 가리지 않는다) */
export function MapTools() {
  const mode = useUiStore(state => state.mode);
  const setMode = useUiStore(state => state.setMode);
  const sendMapCommand = useUiStore(state => state.sendMapCommand);
  const setHighlight = useUiStore(state => state.setHighlight);
  return (
    <div className="maptools">
      <button id="t-paint" aria-pressed={mode === 'paint'} onClick={() => setMode('paint')}>칠하기</button>
      <button id="t-detail" aria-pressed={mode === 'detail'} onClick={() => setMode('detail')}>기록 보기</button>
      <button id="t-reset-view" onClick={() => { setHighlight(null); sendMapCommand({ kind: 'reset' }); }}>전체 보기</button>
      <button id="t-char" onClick={() => sendMapCommand({ kind: 'to-character' })}>내 캐릭터로</button>
      <SeasonBadge />
    </div>
  );
}
