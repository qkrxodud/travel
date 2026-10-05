/**
 * 계절 회차 추천 근거 문구 — 근거 종류(축제·관광지), 축제 기간, 출처 이름. 무엇을 근거로 골랐는지는 서버가 정한다.
 */
import { describe, expect, it } from 'vitest';
import { evidencePeriodText, evidenceText, latestFetchedAt, provenanceText } from './seasonEvidence';

const festival = { title: '진해군항제', startDate: '2027-03-23', endDate: '2027-04-01', fetchedAt: '2026-10-05T04:00:00Z', evidenceKind: 'FESTIVAL' as const };
const attraction = { title: '철암단풍군락지', startDate: null, endDate: null, fetchedAt: '2026-10-06T04:00:00Z', evidenceKind: 'ATTRACTION' as const };

describe('추천 근거 한 줄', () => {
  it('축제는 종류·이름·기간을 월/일로 보인다', () => {
    expect(evidenceText(festival)).toBe('축제 「진해군항제」 3/23~4/1');
  });

  it('하루짜리 축제는 그날 하나만 보인다', () => {
    expect(evidencePeriodText({ startDate: '2027-04-05', endDate: '2027-04-05' })).toBe('4/5');
  });

  it('관광지는 기간이 없어 종류와 이름만 보인다', () => {
    expect(evidenceText(attraction)).toBe('관광지 「철암단풍군락지」');
    expect(evidencePeriodText(attraction)).toBeNull();
  });
});

describe('출처', () => {
  it('TourAPI·AI 추정·둘이 섞인 목록을 각각 이름으로 부른다', () => {
    expect(provenanceText('tourapi')).toBe('한국관광공사 TourAPI');
    expect(provenanceText('ai-estimate')).toBe('AI 추정(검증 전)');
    expect(provenanceText('mixed')).toBe('TourAPI + AI 추정');
  });

  it('근거를 가장 최근에 읽은 시각을 고르고, 근거가 없으면 없다고 한다', () => {
    expect(latestFetchedAt([festival, attraction])).toBe('2026-10-06T04:00:00Z');
    expect(latestFetchedAt([])).toBeNull();
  });
});
