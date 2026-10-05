/**
 * 도감 "계절 한정" 섹션의 추천 근거 표시(13s단계, 순수 함수). 지역마다 TourAPI 근거(축제·관광지) 또는 AI 추정을 보여 주고,
 * 섹션 아래에 출처 안내 한 줄을 붙인다. 출처·근거·순서는 서버 값(GET /seasons/current) 그대로.
 */
import type { SeasonRoundResponse } from '../../../api/types/progression';
import {
  AI_ESTIMATE_LABEL, evidenceText, latestFetchedAt, provenanceText, seoulDayText, TOURAPI_SOURCE,
} from '../../../shared/lib/progress/seasonEvidence';

export interface RegionSourceLine {
  /** KR-xxxxx */
  code: string;
  name: string;
  provenance: 'tourapi' | 'ai-estimate';
  /** 출처 표기 — "한국관광공사 TourAPI" / "AI 추정" */
  sourceLabel: string;
  /** 근거 줄(서버 순서: 축제 이른 순 → 관광지) — AI 추정이면 빈 목록. id 는 근거 종류·TourAPI 콘텐츠 id(같은 제목·기간이어도 겹치지 않는 식별자) */
  evidence: { id: string; text: string }[];
}

/** 지역마다 출처·근거 줄(서버 지역 순서 그대로) */
export function regionSourceLines(round: Pick<SeasonRoundResponse, 'regions' | 'source'>): RegionSourceLine[] {
  return round.regions.map(region => ({
    code: region.code,
    name: region.name,
    provenance: region.provenance,
    sourceLabel: region.provenance === 'tourapi' ? round.source ?? TOURAPI_SOURCE : AI_ESTIMATE_LABEL,
    evidence: region.evidence.map(item => ({ id: `${item.evidenceKind}:${item.contentId}`, text: evidenceText(item) })),
  }));
}

/** 근거 묶음 머리 "추천 근거: AI 추정(검증 전)" · "추천 근거: 한국관광공사 TourAPI" · "추천 근거: TourAPI 7곳 · AI 추정 3곳" */
export function sourceSummaryText(round: Pick<SeasonRoundResponse, 'regions' | 'provenance'>): string {
  if (round.provenance !== 'mixed') return `추천 근거: ${provenanceText(round.provenance)}`;
  const evidenced = round.regions.filter(region => region.provenance === 'tourapi').length;
  return `추천 근거: TourAPI ${evidenced}곳 · AI 추정 ${round.regions.length - evidenced}곳`;
}

/** 섹션 아래 출처 안내 한 줄 */
export function sourceFootnote(round: Pick<SeasonRoundResponse, 'regions' | 'provenance' | 'source'>): string {
  const aiNote = 'AI 추정은 공개 자료로 확인하기 전의 추천이에요.';
  if (round.provenance === 'ai-estimate') return `출처: ${aiNote}`;
  const fetchedAt = latestFetchedAt(round.regions.flatMap(region => region.evidence));
  const tourApi = `${round.source ?? TOURAPI_SOURCE} 축제·관광지 정보${fetchedAt ? `(${seoulDayText(fetchedAt)} 조회)` : ''}`;
  return round.provenance === 'mixed' ? `출처: ${tourApi} · ${aiNote}` : `출처: ${tourApi}`;
}
