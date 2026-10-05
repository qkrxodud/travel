/**
 * 계절 회차 지역의 추천 근거(13s단계) 표시 문구(순수 함수). 어느 지역을 무슨 근거로 골랐는지·순위는 서버가 정한다 — 여기서는 문구만 만든다.
 * 도감 탭 "계절 한정" 섹션(GET /seasons/current)과 관리자 계절 회차 화면(GET /admin/seasons)이 함께 쓴다.
 */

/** 근거 하나 — 플레이어 응답(SeasonEvidenceResponse)과 관리자 응답(LineupEvidenceResponse)이 같은 모양이다 */
export interface EvidenceLike {
  title: string;
  startDate: string | null;
  endDate: string | null;
  fetchedAt: string;
  evidenceKind: 'FESTIVAL' | 'ATTRACTION';
}

export type ProvenanceCode = 'tourapi' | 'ai-estimate' | 'mixed';

/** 근거 기관 표기(서버 source 값이 없을 때도 같은 이름을 쓴다) */
export const TOURAPI_SOURCE = '한국관광공사 TourAPI';
export const AI_ESTIMATE_LABEL = 'AI 추정';

const PROVENANCE_TEXT: Readonly<Record<ProvenanceCode, string>> = {
  tourapi: TOURAPI_SOURCE,
  'ai-estimate': 'AI 추정(검증 전)',
  mixed: 'TourAPI + AI 추정',
};

/** 출처 이름 — "한국관광공사 TourAPI" / "AI 추정(검증 전)" / "TourAPI + AI 추정" */
export function provenanceText(provenance: ProvenanceCode): string {
  return PROVENANCE_TEXT[provenance];
}

/** 근거 종류 — 축제(기간 있음) / 관광지(기간 없음) */
export function evidenceKindText(kind: EvidenceLike['evidenceKind']): string {
  return kind === 'FESTIVAL' ? '축제' : '관광지';
}

/** 2027-03-20 → 3/20 */
function shortDate(day: string): string {
  const [, month, date] = day.split('-');
  return month && date ? `${Number(month)}/${Number(date)}` : day;
}

/** 축제 기간 "3/20~4/5"(하루면 "3/20"), 기간이 없으면(관광지) null */
export function evidencePeriodText(evidence: Pick<EvidenceLike, 'startDate' | 'endDate'>): string | null {
  const { startDate, endDate } = evidence;
  if (!startDate && !endDate) return null;
  if (!startDate || !endDate || startDate === endDate) return shortDate(startDate ?? endDate ?? '');
  return `${shortDate(startDate)}~${shortDate(endDate)}`;
}

/** 근거 한 줄 "축제 「진해군항제」 3/20~4/5" · "관광지 「내장산 단풍생태공원」" */
export function evidenceText(evidence: EvidenceLike): string {
  const period = evidencePeriodText(evidence);
  return `${evidenceKindText(evidence.evidenceKind)} 「${evidence.title}」${period ? ' ' + period : ''}`;
}

/** 근거를 가장 최근에 읽은 시각(없으면 null) */
export function latestFetchedAt(evidence: readonly EvidenceLike[]): string | null {
  let latest: string | null = null;
  for (const item of evidence) {
    if (latest === null || Date.parse(item.fetchedAt) > Date.parse(latest)) latest = item.fetchedAt;
  }
  return latest;
}

/** 시각 → 서울 날짜 "2026. 10. 5." (읽을 수 없으면 원문) */
export function seoulDayText(instant: string): string {
  const epoch = Date.parse(instant);
  if (Number.isNaN(epoch)) return instant;
  return new Date(epoch).toLocaleDateString('ko-KR', { timeZone: 'Asia/Seoul' });
}

/** 시각 → 서울 날짜·시각 "2026. 10. 5. 오후 1:20" (없으면 "—") */
export function seoulTimeText(instant: string | null): string {
  if (!instant) return '—';
  const epoch = Date.parse(instant);
  if (Number.isNaN(epoch)) return instant;
  return new Date(epoch).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul', dateStyle: 'medium', timeStyle: 'short' });
}
