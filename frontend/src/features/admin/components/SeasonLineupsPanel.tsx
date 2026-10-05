import { useRef, useState, type FormEvent } from 'react';
import type { SeasonLineupsResponse } from '../../../api/types/catalog';
import { RoundLineupCard } from './RoundLineupCard';
import { RoundLineupDetail } from './RoundLineupDetail';
import { TourApiStatusCard } from './TourApiStatusCard';

/** 계절 회차 지역 목록(13s단계): TourAPI 연결·오늘 호출 수 → 회차 목록(갱신·확정) → 고른 회차의 상세(지역·순위·근거·출처) */
export function SeasonLineupsPanel({ lineups, adminToken, session }: { lineups: SeasonLineupsResponse; adminToken: string; session: number }) {
  const [selected, setSelected] = useState<string | null>(null);
  const lookupRef = useRef<HTMLInputElement>(null);
  const lookup = (event: FormEvent) => {
    event.preventDefault();
    const roundId = lookupRef.current?.value.trim() ?? '';
    if (roundId) setSelected(roundId);
  };
  return (
    <div className="admin-body" id="admin-seasons">
      <TourApiStatusCard tourApi={lineups.tourApi} schedule={lineups.schedule} />
      <div className="lineup-rounds" id="lineup-rounds">
        {lineups.rounds.map(round => (
          <RoundLineupCard key={round.roundId} round={round} schedule={lineups.schedule} adminToken={adminToken} session={session}
            selected={round.roundId === selected} onSelect={setSelected} />
        ))}
      </div>
      <form className="admin-bar" id="lineup-lookup" onSubmit={lookup}>
        <label htmlFor="lineup-round-id">다른 회차 보기</label>
        <input id="lineup-round-id" ref={lookupRef} type="text" autoComplete="off" spellCheck={false} placeholder="예: spring-2026" />
        <button className="btn sm" id="lineup-lookup-open" type="submit">보기</button>
      </form>
      {selected ? <RoundLineupDetail roundId={selected} schedule={lineups.schedule} adminToken={adminToken} session={session} /> : null}
    </div>
  );
}
