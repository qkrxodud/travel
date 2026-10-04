import type { CSSProperties } from 'react';
import type { RetentionView } from '../../../api/types/analytics';
import { expiredCohortNote, retentionRows, type RetentionCell } from '../model/metrics';

const DAYS = [['d1', 'D1'], ['d7', 'D7'], ['d30', 'D30']] as const;

function Cell({ cell, day }: { cell: RetentionCell; day: string }) {
  if (cell.shade === null) return <td className="ret empty" data-retention={day} title="아직 그날이 지나지 않았어요" />;
  return (
    <td className="ret" data-retention={day} style={{ '--shade': cell.shade } as CSSProperties}>
      <b>{cell.rate}</b> <span>{cell.count}</span>
    </td>
  );
}

/** 코호트 리텐션(그날 가입한 탐험가가 N 일째 그 하루에 활동) — 아직 셀 수 없는 칸은 빈칸 */
export function RetentionTable({ retention, expiredDays, pendingCohortDays }: {
  retention: readonly RetentionView[]; expiredDays: readonly string[]; pendingCohortDays: readonly string[];
}) {
  const rows = retentionRows(retention, pendingCohortDays);
  const expired = expiredCohortNote(expiredDays);
  return (
    <div className="card" id="admin-retention">
      <h2>코호트 리텐션 <span>가입일 + N 일째 하루의 활동</span></h2>
      {expired ? <p className="note gap-caption" data-gap="expired">{expired}</p> : null}
      {rows.length ? (
        <div className="table-scroll">
          <table className="admin-table">
            <thead><tr><th>가입일</th><th>가입</th>{DAYS.map(([key, label]) => <th key={key}>{label}</th>)}</tr></thead>
            <tbody>
              {rows.map(row => row.pending ? (
                <tr key={row.cohortDay} data-cohort-day={row.cohortDay} data-status="pending" data-days={row.pending.days} className="gap missing">
                  <th scope="row">{row.cohortDay}</th>
                  <td colSpan={4} className="gap-note">{row.pending.text}</td>
                </tr>
              ) : (
                <tr key={row.cohortDay} data-cohort-day={row.cohortDay}>
                  <th scope="row">{row.cohortDay}</th>
                  <td data-retention="new">{row.newExplorers}</td>
                  {DAYS.map(([key]) => <Cell key={key} cell={row.cells[key]} day={key} />)}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : <p className="empty">이 기간에 가입한 탐험가가 없어요.</p>}
    </div>
  );
}
