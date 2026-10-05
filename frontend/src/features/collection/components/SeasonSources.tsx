import type { SeasonRoundResponse } from '../../../api/types/progression';
import { toClientCode } from '../../../api/client';
import { regionSourceLines, sourceFootnote, sourceSummaryText } from '../model/seasonSources';

/** 계절 회차 지역마다 추천 근거(한국관광공사 TourAPI 축제·관광지 또는 AI 추정) + 출처 안내 한 줄(13s단계) */
export function SeasonSources({ round }: { round: SeasonRoundResponse }) {
  const lines = regionSourceLines(round);
  return (
    <>
      <details className="season-sources" id="season-sources" data-provenance={round.provenance}>
        <summary id="season-sources-summary">{sourceSummaryText(round)}</summary>
        <ul>
          {lines.map(line => (
            <li key={line.code} data-source-region={toClientCode(line.code)} data-provenance={line.provenance}>
              <b>{line.name}</b>
              <span className={`season-source-tag ${line.provenance}`}>{line.sourceLabel}</span>
              {line.evidence.map(item => <span key={item.id} className="season-evidence">{item.text}</span>)}
            </li>
          ))}
        </ul>
      </details>
      <p className="season-footnote" id="season-footnote">{sourceFootnote(round)}</p>
    </>
  );
}
