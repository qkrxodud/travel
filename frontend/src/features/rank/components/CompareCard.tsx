import { useEffect, useMemo, useRef } from 'react';
import { createMapPath, MAP_HEIGHT, MAP_WIDTH } from '../../../shared/lib/map/mapEngine';
import { toClientCode } from '../../../api/client';
import { cardCanvas, vsCard } from '../../../shared/lib/cards';
import { useUiStore } from '../../../store/uiStore';
import { useCatalog } from '../../../shared/queries/catalog';
import { compareClasses, displayHandle, leadText, regionNames } from '../model/social';
import { useCompare, useStartCompare } from '../queries';

/** 영토 비교(모든 지도 기준): 나만·둘 다·상대만 칸 + 비교 지도 + 비교 카드 */
export function CompareCard() {
  const catalog = useCatalog();
  const handle = useUiStore(state => state.compareHandle);
  const showCard = useUiStore(state => state.showCard);
  const { data: compare } = useCompare(handle);
  const startCompare = useStartCompare();
  const inputRef = useRef<HTMLInputElement>(null);
  // 비교 지도 경로(지도 탭과 같은 투영) — 카탈로그가 정해지면 한 번
  const paths = useMemo(() => {
    if (!catalog) return [];
    const path = createMapPath(catalog.features);
    return catalog.features.map(feature => ({ code: feature.properties.code, d: path(feature as never) ?? '' }));
  }, [catalog]);

  useEffect(() => {
    if (compare && handle) document.documentElement.dataset.vs = handle;
  }, [compare, handle]);

  const total = catalog?.features.length ?? 0;
  let body;
  if (!compare || !handle) {
    body = <p className="empty">친구 랭킹에서 이름을 누르거나 handle 을 입력하면 두 영토를 비교해요 — 서로 팔로우한 친구이거나 공개 프로필만.</p>;
  } else {
    const other = displayHandle(compare.other.handle);
    const compared = compare.onlyMine.length + compare.both.length + compare.onlyTheirs.length || 1;
    const classes = compareClasses(compare);
    const makeCard = () => {
      if (!catalog) return;
      const url = vsCard(cardCanvas, {
        catalog, otherName: other,
        onlyMine: compare.onlyMine.map(toClientCode), both: compare.both.map(toClientCode), onlyTheirs: compare.onlyTheirs.map(toClientCode),
      });
      if (url) showCard(url);
    };
    body = (
      <>
        <div className="vs">
          <div><div className="big" style={{ color: 'var(--accent)' }} data-vs-mine="">{compare.me.regionCount}</div><div className="who">나</div></div>
          <div className="mid">{leadText(compare.lead)}</div>
          <div><div className="big" style={{ color: 'var(--gold)' }} data-vs-theirs="">{compare.other.regionCount}</div><div className="who">{other}</div></div>
        </div>
        <div className="vsbar">
          <i className="a" style={{ width: `${100 * compare.onlyMine.length / compared}%` }} />
          <i className="c" style={{ width: `${100 * compare.both.length / compared}%` }} />
          <i className="b" style={{ width: `${100 * compare.onlyTheirs.length / compared}%` }} />
        </div>
        <p className="sub" id="vs-basis" style={{ margin: '6px 0 0' }}>모든 지도(개인·공유)에서 칠한 곳을 탐험가 단위로 센 값이에요 — 친구 랭킹과 같은 기준, 프로필 탭 VS 카드는 개인 지도 기준.</p>
        <div className="vskey">
          <span className="a" data-vs-only-mine={compare.onlyMine.length}>나만 간 곳 {compare.onlyMine.length}</span>
          <span className="c" data-vs-both={compare.both.length}>둘 다 {compare.both.length}</span>
          <span className="b" data-vs-only-theirs={compare.onlyTheirs.length}>{other}만 {compare.onlyTheirs.length}</span>
        </div>
        <svg className="vsmap" id="vs-map" viewBox={`0 0 ${MAP_WIDTH} ${MAP_HEIGHT}`} role="img" aria-label="영토 비교 지도">
          {paths.map(path => <path key={path.code} d={path.d} data-code={path.code} className={classes.get(path.code) ?? ''} />)}
        </svg>
        <div className="vslist">
          <div><b>나만 간 곳</b> · {regionNames(compare.onlyMine, catalog) || '없음'}</div>
          <div><b>{other}만 간 곳</b> · {regionNames(compare.onlyTheirs, catalog) || '없음'}</div>
          <div><b>둘 다 안 간 곳</b> · {total - compared}곳 남음</div>
        </div>
        <div className="row"><button className="btn sm" id="t-vs-card" onClick={makeCard}>비교 카드 만들기</button></div>
      </>
    );
  }
  return (
    <div className="card" id="vs">
      <h2>영토 비교 <span id="vs-sub">{compare && handle ? `나 vs ${displayHandle(compare.other.handle)}${compare.mutual ? ' · 친구' : ''} · 모든 지도 기준` : ''}</span></h2>
      <div className="social-row">
        <input id="compare-handle" ref={inputRef} placeholder="@handle 과 비교" maxLength={21} aria-label="비교할 handle" onKeyDown={event => { if (event.key === 'Enter') void startCompare(inputRef.current?.value); }} />
        <button className="btn sm" id="t-compare" onClick={() => void startCompare(inputRef.current?.value)}>비교</button>
      </div>
      <div id="vs-body">{body}</div>
    </div>
  );
}
