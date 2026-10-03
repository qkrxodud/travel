import { describe, expect, it } from 'vitest';
import type { RecapResponse } from '../../../api/types/sharing';
import { noticeText } from './account';
import { recapView } from './recap';
import { privacyNote } from './sharing';

describe('연간 리캡 문장(서버 값 표시만)', () => {
  const recap: RecapResponse = {
    year: 2026, mapId: 'personal', newRegions: 3, monthCounts: [0, 0, 2, 0, 0, 0, 1, 0, 0, 0, 0, 0],
    topProvince: { provinceCode: 'KR-11', provinceName: '서울', count: 2 },
    rarest: { regionCode: 'KR-37430', name: '울릉군', provinceCode: 'KR-37', provinceName: '경북', rarity: 'LEGEND' },
    newProvinces: 2, busiestMonth: { month: 3, count: 2 }, setsCompleted: 1,
  };

  it('서버 값을 기존 화면과 같은 문장으로 옮긴다(다시 세지 않는다)', () => {
    const view = recapView(recap);
    expect(view.cells).toEqual([
      ['새 영토', '3곳'], ['가장 많이 간 시·도', '서울 2곳'], ['가장 희귀한 곳', '울릉군 (전설)'],
      ['새로 밟은 시·도', '2곳'], ['완성한 세트', '1개'], ['가장 바쁜 달', '3월 2곳'],
    ]);
    expect(view.months).toBe(recap.monthCounts);
  });

  it('희귀도 라벨은 서버 rarity 를 따른다', () => {
    expect(recapView({ ...recap, rarest: { regionCode: 'KR-11010', name: '종로구', provinceCode: 'KR-11', provinceName: '서울', rarity: 'COMMON' } }).cells[2][1]).toBe('종로구 (일반)');
  });

  it('그 해 방문이 없거나 아직 값이 없으면 0곳·— 로 보인다', () => {
    const empty = recapView({ ...recap, newRegions: 0, monthCounts: Array(12).fill(0), topProvince: null, rarest: null, newProvinces: 0, busiestMonth: null, setsCompleted: 0 });
    expect(empty.cells.map(([, value]) => value)).toEqual(['0곳', '—', '—', '0곳', '0개', '—']);
    expect(recapView(undefined).cells.map(([, value]) => value)).toEqual(['0곳', '—', '—', '0곳', '0개', '—']);
    expect(recapView(undefined).months).toHaveLength(12);
  });
});

describe('계정·공유 문구', () => {
  it('로그인 안내', () => {
    expect(noticeText({ explorerId: 'e', handle: 'h', email: null, personalMapId: 'p', outcome: 'MERGED', merge: { fromExplorerId: 'x', movedRegions: 2, newRegions: 1 } }))
      .toBe('익명 기록 2곳을 계정으로 옮겼어요 (새 지역 1곳)');
    expect(noticeText({ explorerId: 'e', handle: 'h', email: null, personalMapId: 'p', outcome: 'LINKED', merge: null })).toBe('지금까지의 영토를 계정에 연결했어요');
    expect(noticeText({ explorerId: 'e', handle: 'h', email: null, personalMapId: 'p', outcome: 'SIGNED_IN', merge: null })).toBeNull();
    expect(noticeText(null)).toBeNull();
  });

  it('공개 범위 안내', () => {
    expect(privacyNote('FRIENDS')).toContain('서로 팔로우한 친구에게만');
    expect(privacyNote('PRIVATE')).toContain('비공개(기본)');
    expect(privacyNote('PUBLIC')).toContain('전체 공개');
  });
});
