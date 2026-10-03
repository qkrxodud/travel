import { describe, expect, it } from 'vitest';
import type { MapDetailResponse } from '../../../api/types/exploration';
import type { SetResponse } from '../../../api/types/progression';
import { CATALOG, territory, visit } from '../../../test/fixtures';
import { myVisits } from '../../../shared/lib/territory/visits';
import { claimMaps, latestVisitCode, logEntries, provinceRows, regionTip, setChips, showsProvinceFirstHint } from './territory';

const detail = (members: MapDetailResponse['members']): MapDetailResponse => ({
  mapId: 'shared', name: '원정대', kind: 'SHARED', countryCode: 'KR', ownerId: 'me', inviteCode: 'ABCDEFGH',
  settings: { photoRequired: false, dailyCheckInCap: 5, visibility: 'PRIVATE' }, members, claims: [], disputed: [], departing: 0, rules: [], rejoined: false,
});
const member = (explorerId: string, me: boolean, color: string) => ({ explorerId, role: me ? 'OWNER' as const : 'MEMBER' as const, joinedAt: '', color, me, regionCount: 1, claimCount: 1 });

const SET: SetResponse = {
  id: 'seoul', name: '서울 둘', desc: '', title: '서울러', have: 1, total: 2, completed: false, completedAt: null, rewardXp: 100,
  regions: [{ code: 'KR-11010', name: '종로구', collected: true }, { code: 'KR-11020', name: '중구', collected: false }],
};

describe('내 영토', () => {
  it('개인 지도는 방문 전부가 내 것(화면 코드)', () => {
    const visits = myVisits(territory({ visits: [visit('11010', { memo: '경복궁' }), visit('31370')] }), null);
    expect([...(visits?.keys() ?? [])]).toEqual(['11010', '31370']);
    expect(visits?.get('11010')).toMatchObject({ date: '2026-10-01', memo: '경복궁' });
  });

  it('지역 코드 순으로 둔다(프로토타입 객체 키 순서 — 리캡 동점 순서)', () => {
    const visits = myVisits(territory({ visits: [visit('37430'), visit('21090'), visit('11010')] }), null);
    expect([...(visits?.keys() ?? [])]).toEqual(['11010', '21090', '37430']);
  });

  it('공유 지도는 멤버 정보(나)가 와야 고를 수 있고, 내 방문만 고른다', () => {
    const shared = territory({ mapKind: 'SHARED', visits: [visit('11010', { checkedInBy: 'me' }), visit('11020', { checkedInBy: 'friend' })] });
    expect(myVisits(shared, null)).toBeNull();
    const mine = myVisits(shared, detail([member('me', true, '#2fc3ad'), member('friend', false, '#e8743b')]));
    expect([...(mine?.keys() ?? [])]).toEqual(['11010']);
  });

  it('캐릭터는 가장 최근에 처리한 체크인 지역에 선다', () => {
    const visits = myVisits(territory({ visits: [visit('11010', { visitedAt: '2026-10-01T03:00:00Z' }), visit('37430', { visitedAt: '2026-10-02T03:00:00Z' }), visit('11020', { visitedAt: '2026-09-01T03:00:00Z' })] }), null);
    expect(latestVisitCode(visits ?? new Map())).toBe('37430');
    expect(latestVisitCode(new Map())).toBeNull();
  });

  it('공유 지도 선점 색: 지역 → 선점자 id·색', () => {
    const shared = territory({ mapKind: 'SHARED', claims: [{ regionCode: 'KR-11010', explorerId: 'friend' }] });
    const { claimer, color } = claimMaps(shared, detail([member('me', true, '#2fc3ad'), member('friend', false, '#e8743b')]));
    expect(claimer.get('11010')).toBe('friend');
    expect(color.get('11010')).toBe('#e8743b');
    expect(claimMaps(territory(), null).color.size).toBe(0);
  });
});

describe('시·도별·일지·세트', () => {
  it('시·도별 정복률은 서버 값 — 비율 높은 순, 같으면 방문 수 많은 순', () => {
    const rows = provinceRows(territory({ provinces: [
      { code: 'KR-11', name: '서울', visited: 1, total: 2, percent: 50, conquered: false },
      { code: 'KR-31', name: '경기', visited: 1, total: 1, percent: 100, conquered: true },
      { code: 'KR-37', name: '경북', visited: 0, total: 1, percent: 0, conquered: false },
    ] }), CATALOG);
    expect(rows.map(row => row.name)).toEqual(['경기', '서울', '경북']);
    // 서버 응답 전: 카탈로그 시·도 순서(표시 순) · 0
    expect(provinceRows(null, CATALOG).map(row => `${row.name}${row.visited}/${row.total}`)).toEqual(['서울0/2', '경기0/1', '경북0/1']);
  });

  it('일지: 서버 순서 그대로 라벨·메모·이의, 모르는 지역은 뺀다', () => {
    const entries = logEntries(territory({ visits: [visit('31370', { memo: '잣', disputed: true }), visit('99999'), visit('11010')] }), CATALOG);
    expect(entries.map(entry => entry.label)).toEqual(['경기 가평군', '서울 종로구']);
    expect(entries[0]).toMatchObject({ memo: '잣', disputed: true });
  });

  it('세트 칩: 내 영토 기준 모은 수, 체크인 모달은 지금 칠할 곳도 센다', () => {
    const mine = new Set(['11010']);
    expect(setChips([SET], '11020', mine)).toEqual([{ id: 'seoul', name: '서울 둘', have: 1, total: 2, done: false }]);
    expect(setChips([SET], '11020', mine, '11020')[0]).toMatchObject({ have: 2, done: true });
    expect(setChips([SET], '31370', mine)).toEqual([]);
  });

  it('툴팁 문구', () => {
    const ulleung = CATALOG.byCode.get('37430');
    const jongno = CATALOG.byCode.get('11010');
    expect(ulleung && regionTip(ulleung, false, [SET])).toBe('경북 울릉군 · 미탐험 · 전설');
    expect(jongno && regionTip(jongno, true, [SET])).toBe('서울 종로구 · 내 영토 · 도감: 서울 둘');
  });
});

describe('시·도 첫 방문 표시', () => {
  it('지금 지도에서 그 시·도에 칠한 곳이 없을 때만 보인다', () => {
    expect(showsProvinceFirstHint(CATALOG, '11020', [])).toBe(true);
    expect(showsProvinceFirstHint(CATALOG, '11020', ['11010'])).toBe(false); // 같은 서울
    expect(showsProvinceFirstHint(CATALOG, '11020', ['37430'])).toBe(true); // 다른 시·도
    expect(showsProvinceFirstHint(CATALOG, '99999', [])).toBe(false); // 모르는 지역
  });
});

