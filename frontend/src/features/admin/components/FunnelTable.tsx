import type { FunnelView } from '../../../api/types/analytics';
import { expiredCohortNote, funnelRows } from '../model/metrics';

/** 퍼널(코호트 = 그날 처음 본 방문): 첫 화면 → 7일 안 첫 체크인 → 그 뒤 7일 안 재방문 */
export function FunnelTable({ funnel, today, expiredDays, pendingCohortDays }: {
  funnel: readonly FunnelView[]; today: string; expiredDays: readonly string[]; pendingCohortDays: readonly string[];
}) {
  const rows = funnelRows(funnel, today, pendingCohortDays);
  const expired = expiredCohortNote(expiredDays);
  return (
    <div className="card" id="admin-funnel">
      <h2>퍼널 <span>첫 화면 → 첫 체크인(7일 안) → 재방문(그 뒤 7일 안) · 15일이 지나면 확정</span></h2>
      {expired ? <p className="note gap-caption" data-gap="expired">{expired}</p> : null}
      <div className="table-scroll">
        <table className="admin-table">
          <thead>
            <tr><th>첫 화면 날</th><th>첫 화면</th><th>첫 체크인</th><th>전환</th><th>7일 내 재방문</th><th>전환</th><th>전체</th><th>상태</th></tr>
          </thead>
          <tbody>
            {rows.map(row => row.pending ? (
              <tr key={row.cohortDay} data-cohort-day={row.cohortDay} data-status="pending" data-days={row.pending.days} className="gap missing">
                <th scope="row">{row.cohortDay}</th>
                <td colSpan={7} className="gap-note">{row.pending.text}</td>
              </tr>
            ) : (
              <tr key={row.cohortDay} data-cohort-day={row.cohortDay} data-settled={row.settled} className={row.today ? 'today' : undefined}>
                <th scope="row">{row.cohortDay}{row.today ? ' (오늘)' : ''}</th>
                <td data-funnel="first-screen">{row.firstScreen}</td>
                <td data-funnel="first-check-in">{row.firstCheckIn}</td>
                <td className="rate">{row.checkInRate}</td>
                <td data-funnel="revisited">{row.revisited}</td>
                <td className="rate">{row.revisitRate}</td>
                <td className="rate">{row.overallRate}</td>
                <td><span className={row.settled ? 'tag done' : 'tag'}>{row.status}</span></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
