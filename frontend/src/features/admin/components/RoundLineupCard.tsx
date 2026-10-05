import type { LineupScheduleResponse, RoundLineupResponse } from '../../../api/types/catalog';
import { provenanceText, seoulTimeText } from '../../../shared/lib/progress/seasonEvidence';
import {
  attemptOutcomeText, candidateText, canConfirm, canRefresh, confirmedByText, lineupErrorText, nextPlanText, roundPeriodText, shortageText,
} from '../model/seasonLineups';
import { useConfirmSeasonLineup, useRefreshSeasonLineup } from '../queries';

interface Props {
  round: RoundLineupResponse;
  schedule: LineupScheduleResponse;
  adminToken: string;
  session: number;
  selected: boolean;
  onSelect: (roundId: string) => void;
}

/** 회차 하나의 요약 — 쓰는 목록·후보·마지막 시도·경고 + 상세·새로 모으기(캐시 / 새로 읽기)·확정 */
export function RoundLineupCard({ round, schedule, adminToken, session, selected, onSelect }: Props) {
  const refresh = useRefreshSeasonLineup(adminToken, session);
  const confirm = useConfirmSeasonLineup(adminToken, session);
  const busy = refresh.isPending || confirm.isPending;
  const failure = refresh.error ?? confirm.error;
  const shortage = shortageText(round.candidate, schedule);
  const collect = (fresh: boolean) => {
    confirm.reset();
    refresh.mutate({ roundId: round.roundId, fresh }, { onSuccess: () => onSelect(round.roundId) });
  };
  const confirmCandidate = () => {
    refresh.reset();
    confirm.mutate({ roundId: round.roundId }, { onSuccess: () => onSelect(round.roundId) });
  };
  return (
    <div className={`card lineup-round ${round.seasonId} ${selected ? 'selected' : ''}`} data-round={round.roundId} data-locked={String(round.locked)}
      data-provenance={round.inEffect.provenance} data-confirmed-by={round.inEffect.confirmedBy ?? 'none'}>
      <h2>{round.emoji} {round.name} <span>{round.locked ? '목록 고정(열림·지남)' : nextPlanText(round.nextPlan)}</span></h2>
      <p className="note">{roundPeriodText(round)} · 자동 수집 시작 {seoulTimeText(round.collectionOpensAt)}</p>
      <p className="lineup-in-effect">
        쓰는 목록: <b>{confirmedByText(round.inEffect.confirmedBy)}</b> · {provenanceText(round.inEffect.provenance)}
        {round.inEffect.confirmedAt ? ` · ${seoulTimeText(round.inEffect.confirmedAt)}` : ''}
      </p>
      {round.candidate ? (
        <p className="lineup-candidate-summary" data-evidenced={round.candidate.evidencedRegions}>
          {candidateText(round.candidate)} · {seoulTimeText(round.candidate.collectedAt)} 모음
        </p>
      ) : <p className="note lineup-candidate-summary" data-evidenced="none">후보 없음</p>}
      {shortage ? <p className="admin-notice lineup-shortage">{shortage}</p> : null}
      {round.lastAttempt ? (
        <p className="note lineup-attempt" data-outcome={round.lastAttempt.outcome} data-failed={String(round.lastAttempt.failed)}>
          마지막 시도 {seoulTimeText(round.lastAttempt.at)} · {attemptOutcomeText(round.lastAttempt.outcome)}
        </p>
      ) : null}
      {round.warnings.length ? (
        <ul className="lineup-warnings">{round.warnings.map(warning => <li key={warning}>{warning}</li>)}</ul>
      ) : null}
      <div className="row">
        <button className="btn sm" type="button" data-action="detail" onClick={() => onSelect(round.roundId)}>상세 보기</button>
        <button className="btn sm" type="button" data-action="refresh" disabled={!canRefresh(round) || busy} onClick={() => collect(false)}>
          {refresh.isPending ? '모으는 중…' : '새로 모으기'}
        </button>
        <button className="btn sm" type="button" data-action="refresh-fresh" disabled={!canRefresh(round) || busy} onClick={() => collect(true)}
          title="같은 날 받아 둔 응답을 쓰지 않고 TourAPI 를 다시 불러요(오늘 호출 수에 들어가요)">
          캐시 없이 새로 모으기
        </button>
        <button className="btn sm primary" type="button" data-action="confirm" disabled={!canConfirm(round) || busy} onClick={confirmCandidate}>
          {confirm.isPending ? '확정 중…' : '후보 확정'}
        </button>
      </div>
      {failure ? <p className="admin-error lineup-error" role="alert" data-code={(failure as { code?: string }).code ?? ''}>{lineupErrorText(failure)}</p> : null}
    </div>
  );
}
