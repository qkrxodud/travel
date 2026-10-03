/**
 * 프로필 탭 — 한 해를 돌아보는 리캡, 로그인·병합 안내, 공개 범위, 칭호·handle 바꾸기, 로그아웃. 리캡 수치는 서버가 센 값이다.
 * 이야기 순서: 연간 리캡 → 로그인 안내 → 공개 범위 → 칭호 고르기 → handle 바꾸기 → 로그아웃.
 */
import { act, renderHook } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { MergeNotice } from '../../api/types/account';
import type { RecapResponse } from '../../api/types/sharing';
import { catalogKeys } from '../../shared/queries/catalog';
import { progressKeys } from '../../shared/queries/progress';
import { sessionKeys } from '../../shared/queries/session';
import { mapKeys } from '../../shared/queries/territory';
import { serverState } from '../../test/serverState';
import { noticeText } from './model/account';
import { recapView } from './model/recap';
import { privacyNote, VISIBILITY_OPTIONS } from './model/sharing';
import { sharingKeys, useChangeHandle, useLogout, useSelectTitle, useSetPrivacy } from './queries';

vi.mock('../../api/progression', () => ({ progressionApi: { selectTitle: vi.fn(async () => ({ xp: 120, selectedTitleId: 'wanderer' })) } }));
vi.mock('../../api/sharing', () => ({ sharingApi: { setPrivacy: vi.fn(async () => ({ visibility: 'PUBLIC', publiclyVisible: true })) } }));
vi.mock('../../api/exploration', () => ({ explorationApi: { changeHandle: vi.fn(async () => ({ handle: 'new_kim' })) } }));
vi.mock('../../api/account', () => ({ accountApi: { logout: vi.fn(async () => undefined), session: vi.fn(async () => null) } }));

const RECAP: RecapResponse = {
  year: 2026, mapId: 'personal', newRegions: 3, monthCounts: [0, 0, 2, 0, 0, 0, 1, 0, 0, 0, 0, 0],
  topProvince: { provinceCode: 'KR-11', provinceName: '서울', count: 2 },
  rarest: { regionCode: 'KR-37430', name: '울릉군', provinceCode: 'KR-37', provinceName: '경북', rarity: 'LEGEND' },
  newProvinces: 2, busiestMonth: { month: 3, count: 2 }, setsCompleted: 1,
};
const notice = (outcome: MergeNotice['outcome'], merge: MergeNotice['merge'] = null): MergeNotice =>
  ({ explorerId: 'e', handle: 'h', email: null, personalMapId: 'p', outcome, merge });

describe('연간 리캡', () => {
  it('서버가 센 한 해를 새 영토·많이 간 시·도·희귀한 곳·새 시·도·완성 세트·바쁜 달 여섯 칸으로 옮긴다', () => {
    const view = recapView(RECAP);
    expect(view.cells).toEqual([
      ['새 영토', '3곳'], ['가장 많이 간 시·도', '서울 2곳'], ['가장 희귀한 곳', '울릉군 (전설)'],
      ['새로 밟은 시·도', '2곳'], ['완성한 세트', '1개'], ['가장 바쁜 달', '3월 2곳'],
    ]);
  });

  it('달마다 칠한 수는 서버 값 그대로 막대로 쓴다', () => {
    expect(recapView(RECAP).months).toBe(RECAP.monthCounts);
  });

  it('가장 희귀한 곳의 희귀도는 서버가 정한 등급을 따른다', () => {
    expect(recapView({ ...RECAP, rarest: { regionCode: 'KR-11010', name: '종로구', provinceCode: 'KR-11', provinceName: '서울', rarity: 'COMMON' } }).cells[2][1]).toBe('종로구 (일반)');
  });

  it('그 해 칠한 곳이 없으면 0곳과 — 로 보인다', () => {
    const empty = recapView({ ...RECAP, newRegions: 0, monthCounts: Array(12).fill(0), topProvince: null, rarest: null, newProvinces: 0, busiestMonth: null, setsCompleted: 0 });
    expect(empty.cells.map(([, value]) => value)).toEqual(['0곳', '—', '—', '0곳', '0개', '—']);
  });

  it('리캡을 아직 못 읽었어도 빈 리캡과 열두 달 막대로 보인다', () => {
    expect(recapView(undefined).cells.map(([, value]) => value)).toEqual(['0곳', '—', '—', '0곳', '0개', '—']);
    expect(recapView(undefined).months).toHaveLength(12);
  });
});

describe('로그인 안내', () => {
  it('익명 기록을 계정으로 옮겼으면 옮긴 곳과 새로 생긴 곳을 알려 준다', () => {
    expect(noticeText(notice('MERGED', { fromExplorerId: 'x', movedRegions: 2, newRegions: 1 }))).toBe('익명 기록 2곳을 계정으로 옮겼어요 (새 지역 1곳)');
    expect(noticeText(notice('MERGED', { fromExplorerId: 'x', movedRegions: 2, newRegions: 0 }))).toBe('익명 기록 2곳을 계정으로 옮겼어요');
  });

  it('처음 로그인해 지금 영토를 계정에 붙였으면 연결했다고 알려 준다', () => {
    expect(noticeText(notice('LINKED'))).toBe('지금까지의 영토를 계정에 연결했어요');
  });

  it('새 계정 탐험가를 만들었으면 그렇게 알려 준다', () => {
    expect(noticeText(notice('CREATED'))).toBe('새 계정 탐험가를 만들었어요');
  });

  it('그냥 다시 로그인했거나 알릴 것이 없으면 안내하지 않는다', () => {
    expect(noticeText(notice('SIGNED_IN'))).toBeNull();
    expect(noticeText(null)).toBeNull();
  });
});

describe('공개 범위', () => {
  it('비공개(기본)·친구 공개·전체 공개 중에서 고른다', () => {
    expect(VISIBILITY_OPTIONS.map(([, label]) => label)).toEqual(['비공개', '친구 공개', '전체 공개']);
  });

  it('고른 범위마다 누가 내 프로필을 볼 수 있는지 알려 준다', () => {
    expect(privacyNote('FRIENDS')).toContain('서로 팔로우한 친구에게만');
    expect(privacyNote('PRIVATE')).toContain('비공개(기본)');
    expect(privacyNote('PUBLIC')).toContain('전체 공개');
  });

  it('범위를 바꾸면 서버가 정한 공개 여부로 내 카드 정보를 바로 고친다', async () => {
    const { queryClient, wrapper } = serverState([[sharingKeys.cards(), { handle: 'kim', profileUrl: null, visibility: 'PRIVATE', publiclyVisible: false, cards: [] }]]);
    const { result } = renderHook(() => useSetPrivacy(), { wrapper });
    await act(() => result.current.mutateAsync('PUBLIC'));
    expect(queryClient.getQueryData(sharingKeys.cards())).toMatchObject({ visibility: 'PUBLIC', publiclyVisible: true });
  });
});

describe('칭호 고르기', () => {
  it('고른 칭호가 담긴 서버 진행 값으로 헤더를 바로 바꾼다', async () => {
    const { queryClient, wrapper } = serverState([[progressKeys.progress(), { xp: 120, selectedTitleId: null }]]);
    const { result } = renderHook(() => useSelectTitle(), { wrapper });
    await act(() => result.current.mutateAsync('wanderer'));
    expect(queryClient.getQueryData(progressKeys.progress())).toMatchObject({ selectedTitleId: 'wanderer' });
  });
});

describe('handle 바꾸기', () => {
  it('바뀐 handle 을 로그인 정보에 바로 반영한다', async () => {
    const { queryClient, wrapper } = serverState([[sessionKeys.session(), { loggedIn: true, handle: 'kim' }]]);
    const { result } = renderHook(() => useChangeHandle(), { wrapper });
    await act(() => result.current.mutateAsync('new_kim'));
    expect(queryClient.getQueryData(sessionKeys.session())).toMatchObject({ loggedIn: true, handle: 'new_kim' });
  });
});

describe('로그아웃', () => {
  it('이 기기는 새 익명 탐험가가 되므로 지도 카탈로그만 남기고 내 영토 같은 서버 값은 버린다', async () => {
    const { queryClient, wrapper, stale } = serverState([
      [catalogKeys.index(), { features: [] }],
      [mapKeys.territory(null), { mapId: 'personal' }],
      [sessionKeys.session(), { loggedIn: true }],
    ]);
    const { result } = renderHook(() => useLogout(), { wrapper });
    await act(() => result.current.mutateAsync());
    expect(queryClient.getQueryData(catalogKeys.index())).toEqual({ features: [] });
    expect(queryClient.getQueryData(mapKeys.territory(null))).toBeUndefined();
    expect(stale(sessionKeys.session())).toBe(true);
  });
});
