/**
 * 내 방문 — 지금 보는 지도에서 내가 칠한 곳. 지도·가방·공유 지도·프로필이 같은 규칙으로 고른다.
 */
import { describe, expect, it } from 'vitest';
import type { MapDetailResponse } from '../../../api/types/exploration';
import { territory, visit } from '../../../test/fixtures';
import { meIn, memberName, myVisits } from './visits';

const detail = (members: MapDetailResponse['members']): MapDetailResponse => ({
  mapId: 'shared', name: '원정대', kind: 'SHARED', countryCode: 'KR', ownerId: 'me', inviteCode: 'ABCDEFGH',
  settings: { photoRequired: false, dailyCheckInCap: 5, visibility: 'PRIVATE' }, members, claims: [], disputed: [], departing: 0, rules: [], rejoined: false,
});
const member = (explorerId: string, me: boolean) => ({ explorerId, role: me ? 'OWNER' as const : 'MEMBER' as const, joinedAt: '', color: '#2fc3ad', me, regionCount: 1, claimCount: 1 });

describe('개인 지도의 내 방문', () => {
  it('방문 전부가 내 것이고 날짜·메모를 함께 기억한다', () => {
    const visits = myVisits(territory({ visits: [visit('11010', { memo: '경복궁' }), visit('31370')] }), null);
    expect([...(visits?.keys() ?? [])]).toEqual(['11010', '31370']);
    expect(visits?.get('11010')).toMatchObject({ date: '2026-10-01', memo: '경복궁' });
  });

  it('지역 코드 순으로 둔다 — 연간 리캡의 동점 순서가 이 순서를 따른다', () => {
    const visits = myVisits(territory({ visits: [visit('37430'), visit('21090'), visit('11010')] }), null);
    expect([...(visits?.keys() ?? [])]).toEqual(['11010', '21090', '37430']);
  });
});

describe('공유 지도의 내 방문', () => {
  const shared = territory({ mapKind: 'SHARED', visits: [visit('11010', { checkedInBy: 'me' }), visit('11020', { checkedInBy: 'friend' })] });

  it('멤버 가운데 내가 칠한 곳만 고른다', () => {
    const mine = myVisits(shared, detail([member('me', true), member('friend', false)]));
    expect([...(mine?.keys() ?? [])]).toEqual(['11010']);
  });

  it('누가 나인지 아직 모르면 고르지 않고 준비 전으로 둔다', () => {
    expect(myVisits(shared, null)).toBeNull();
    expect(meIn(null)).toBeNull();
  });
});

describe('공유 지도 멤버 이름', () => {
  it('나는 "나"로, 다른 멤버는 앞 네 글자로 부른다', () => {
    expect(memberName({ me: true, explorerId: 'abcdef' })).toBe('나');
    expect(memberName({ me: false, explorerId: 'abcdef' })).toBe('멤버 abcd');
  });
});
