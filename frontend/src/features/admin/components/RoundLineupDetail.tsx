import type { LineupScheduleResponse } from '../../../api/types/catalog';
import { provenanceText, seoulTimeText } from '../../../shared/lib/progress/seasonEvidence';
import { candidateText, confirmedByText, lineupErrorText, roundPeriodText, shortageText } from '../model/seasonLineups';
import { useSeasonLineup } from '../queries';
import { LineupRegionTable } from './LineupRegionTable';

/** 회차 하나의 상세(GET /admin/seasons/{roundId}) — 쓰는 목록과 후보의 지역·순위·근거·출처 */
export function RoundLineupDetail({ roundId, schedule, adminToken, session }: { roundId: string; schedule: LineupScheduleResponse; adminToken: string; session: number }) {
  const detail = useSeasonLineup(adminToken, session, roundId);
  if (detail.isError) return <p className="admin-error" id="lineup-detail-error" role="alert">{lineupErrorText(detail.error)}</p>;
  if (!detail.data) return <p className="note" id="lineup-detail-loading">불러오는 중…</p>;
  const round = detail.data;
  const shortage = shortageText(round.candidate, schedule);
  return (
    <div className="lineup-detail" id="lineup-detail" data-round={round.roundId}>
      <div className="card">
        <h2>{round.emoji} {round.name} · 쓰는 목록 <span>{confirmedByText(round.inEffect.confirmedBy)} · {provenanceText(round.inEffect.provenance)}</span></h2>
        <p className="note">
          {roundPeriodText(round)}
          {round.inEffect.collectedAt ? ` · 근거 ${seoulTimeText(round.inEffect.collectedAt)} 조회` : ''}
          {round.inEffect.source ? ` · 출처 ${round.inEffect.source}` : ''}
        </p>
        <LineupRegionTable id="lineup-in-effect" regions={round.inEffect.regions} />
      </div>
      {round.candidate ? (
        <div className="card">
          <h2>후보(확정 전) <span>{candidateText(round.candidate)}</span></h2>
          <p className="note">
            {seoulTimeText(round.candidate.collectedAt)} 모음{round.candidate.source ? ` · 출처 ${round.candidate.source}` : ''}
          </p>
          {shortage ? <p className="admin-notice lineup-shortage" id="lineup-detail-shortage">{shortage}</p> : null}
          {round.candidate.warnings.length ? (
            <ul className="lineup-warnings">{round.candidate.warnings.map(warning => <li key={warning}>{warning}</li>)}</ul>
          ) : null}
          <LineupRegionTable id="lineup-candidate" regions={round.candidate.regions} />
        </div>
      ) : null}
    </div>
  );
}
