import { countText, DAY_STATUS_TEXT, kFactorText, type DayEntry } from '../model/metrics';

/** 일별 숫자 표(차트의 표 보기) — 최근 날 먼저. 셀 수 없는 날은 0 대신 이유(배치 전·보관 기간 지남)를 보인다. */
export function DailyTable({ entries }: { entries: readonly DayEntry[] }) {
  return (
    <details className="card admin-details" id="admin-daily">
      <summary>일별 숫자 표로 보기</summary>
      <div className="table-scroll">
        <table className="admin-table">
          <thead>
            <tr><th>날짜</th><th>새 방문</th><th>새 탐험가</th><th>DAU</th><th>WAU</th><th>MAU</th><th>프로필 열람</th><th>카드 열람</th><th>봇</th><th>K(30일)</th></tr>
          </thead>
          <tbody>
            {entries.slice().reverse().map(({ day, status, row }) => (
              <tr key={day} data-day={day} data-status={status} className={status === 'counted' ? undefined : `gap ${status}`}>
                <th scope="row">{day}{row?.live ? ' (오늘)' : ''}</th>
                {row ? (
                  <>
                    <td>{countText(row.newVisitors)}</td><td>{countText(row.newExplorers)}</td>
                    <td>{countText(row.dau)}</td><td>{countText(row.wau)}</td><td>{countText(row.mau)}</td>
                    <td>{countText(row.profileViews)}</td><td>{countText(row.cardViews)}</td><td>{countText(row.botViews)}</td>
                    <td>{kFactorText(row.kFactor)}</td>
                  </>
                ) : <td colSpan={9} className="gap-note">{status === 'counted' ? '' : DAY_STATUS_TEXT[status]}</td>}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </details>
  );
}
