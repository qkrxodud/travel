import { useCatalog } from '../../../shared/queries/catalog';
import { logEntries } from '../model/territory';
import { useMyTerritory } from '../../../shared/queries/territory';

/** 탐험 일지(서버 순서 — 최근 순, 최대 40) */
export function ExplorationLog() {
  const catalog = useCatalog();
  const { territory } = useMyTerritory();
  const entries = catalog ? logEntries(territory, catalog) : [];
  return (
    <div className="card">
      <h2>탐험 일지 <span>최근 순</span></h2>
      <ul className="log" id="log">
        {entries.length ? entries.map(entry => (
          <li key={entry.key}>
            <time>{entry.date || '날짜 미정'}</time><b>{entry.label}</b><span className="m">{entry.memo}</span>
            {entry.disputed ? <span className="tag-disputed">이의</span> : null}
          </li>
        )) : <li className="m">아직 기록이 없어요.</li>}
      </ul>
    </div>
  );
}
