/**
 * 가방 탭 — 가진 아이템을 고르고 늘어놓고, 입히고 벗기고, 아직 없는 것을 구경한다. 보유·착용·꾸미기 점수는 서버 값이다.
 * 이야기 순서: 가방 늘어놓기 → 꾸미기 등급 → 갖고 싶은 것 → 입히기·즐겨찾기 → 재방문 2회차 색·계절 배경 표시.
 */
import { act, cleanup, render, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { SceneResponse } from '../../api/types/wardrobe';
import { toDisplayItem, type DisplayItem } from '../../shared/lib/item/displayItem';
import { wardrobeKeys } from '../../shared/queries/wardrobe';
import { CATALOG } from '../../test/fixtures';
import { serverState } from '../../test/serverState';
import { BAG_FILTERS, filterAndSort, isSetReward, styleRank, wantedItems } from './model/bag';
import { useEditScene, useFavorite } from './queries';
import { InventoryPanel } from './components/InventoryPanel';

const { WORN_SCENE } = vi.hoisted(() => ({ WORN_SCENE: { gender: 'F', slots: {}, props: [], stylePoints: 8, wornCount: 0, updatedAt: null } satisfies SceneResponse }));

// 아이템 그림(캔버스)은 픽셀 페인터 테스트 몫 — 여기서는 타일 표시만 본다
vi.mock('../../shared/ui/ItemImage', () => ({ ItemImage: () => null }));
vi.mock('../../api/wardrobe', () => ({
  wardrobeApi: {
    editScene: vi.fn(async () => WORN_SCENE),
    favorite: vi.fn(async () => null),
  },
}));

const owned = (itemId: string, slot: DisplayItem['slot'], tier: DisplayItem['tier'], favorite = false): DisplayItem =>
  ({ ...toDisplayItem({ itemId, name: itemId, emoji: '🎁', slot: 'HAND', tier: 'COMMON', theme: null, look: null }, ''), slot, tier, favorite });

describe('가방 늘어놓기', () => {
  const items = [owned('a', 'hand', 'common'), owned('b', 'hat', 'legend'), owned('c', 'hand', 'rare'), owned('d', 'hand', 'common', true), owned('e', 'hand', 'common')];

  it('즐겨찾기한 것을 먼저, 그다음 전설·희귀·일반 순으로, 같으면 서버가 준 순서로 둔다', () => {
    expect(filterAndSort(items, 'all').map(item => item.code)).toEqual(['d', 'b', 'c', 'a', 'e']);
  });

  it('슬롯을 고르면 그 슬롯 아이템만 보이고, 없으면 비어 있다', () => {
    expect(filterAndSort(items, 'hat').map(item => item.code)).toEqual(['b']);
    expect(filterAndSort(items, 'pet')).toEqual([]);
  });

  it('고를 수 있는 슬롯은 전체·모자·손·키링·배낭·동행·배경·장식이다', () => {
    expect(BAG_FILTERS.map(([, label]) => label)).toEqual(['전체', '모자', '손', '키링', '배낭', '동행', '배경·풍경', '장식']);
  });

  it('세트를 완성해 받은 아이템은 따로 표시한다', () => {
    expect(isSetReward({ code: 'set:jiri' })).toBe(true);
    expect(isSetReward({ code: 'region:KR-11010' })).toBe(false);
  });
});

describe('꾸미기 등급', () => {
  it('서버가 센 꾸미기 점수에 따라 맨몸부터 전설급까지 등급 문구를 붙인다', () => {
    expect([0, 1, 8, 18, 30].map(styleRank)).toEqual(['맨몸', '소박함', '제법 꾸밈', '화려함', '전설급 꾸미기']);
  });

  it('등급 경계 바로 아래 점수는 아래 등급이다', () => {
    expect([7, 17, 29].map(styleRank)).toEqual(['소박함', '제법 꾸밈', '화려함']);
  });
});

describe('갖고 싶은 것', () => {
  it('아직 안 간 전설 지역과, 안 간 곳의 배경·장식을 카탈로그 순으로 보여 준다', () => {
    const wanted = wantedItems(CATALOG, new Map());
    // 울릉군(전설·배경)은 두 목록에 모두 든다(기존 화면과 같게)
    expect(wanted.map(entry => entry.code)).toEqual(['37430', '37430']);
    expect(wanted[0].item).toMatchObject({ name: '울릉 독도 바다 풍경', slot: 'bg', tier: 'legend', from: '경북 울릉군' });
  });

  it('이미 간 곳의 아이템은 갖고 싶은 것에서 빠진다', () => {
    expect(wantedItems(CATALOG, new Map([['37430', { code: '37430', date: '', memo: '', at: 0 }]]))).toEqual([]);
  });
});

describe('입히기와 즐겨찾기', () => {
  const bag = () => serverState([[wardrobeKeys.inventory(), { items: [] }], [wardrobeKeys.scene(), { gender: 'M' }]]);

  it('입히거나 벗기면 서버가 돌려준 장면을 바로 보여 주고, 가방의 착용 표시를 다시 읽는다', async () => {
    const { queryClient, wrapper, stale } = bag();
    const { result } = renderHook(() => useEditScene(), { wrapper });
    await act(() => result.current.mutateAsync({ gender: 'F' }));
    expect(queryClient.getQueryData(wardrobeKeys.scene())).toEqual(WORN_SCENE);
    expect(stale(wardrobeKeys.inventory())).toBe(true);
  });

  it('즐겨찾기를 바꾸면 가방을 다시 읽어 순서를 바꾼다', async () => {
    const { wrapper, stale } = bag();
    const { result } = renderHook(() => useFavorite(), { wrapper });
    await act(() => result.current.mutateAsync({ itemId: 'region:KR-11010', favorite: true }));
    expect(stale(wardrobeKeys.inventory())).toBe(true);
  });
});

describe('재방문 2회차 색과 계절 배경 표시', () => {
  afterEach(cleanup);
  const panel = (items: DisplayItem[]) => {
    const { wrapper } = serverState();
    render(<InventoryPanel items={items} sceneProps={[]} onEdit={() => undefined} />, { wrapper });
  };

  it('다시 다녀와 색이 바뀐 특산물에는 2회차 표시를 단다', () => {
    const recolored = toDisplayItem({ itemId: 'region:KR-11010', name: '청사초롱 등불', emoji: '🏮', slot: 'HAND', tier: 'COMMON', theme: null,
      look: { type: 'lantern', primary: '#e63946', secondary: '#f4c542' }, variant: 2, variantLook: { type: 'lantern', primary: '#f4c542', secondary: '#e63946' } }, '서울 종로구');
    panel([recolored, owned('region:KR-11020', 'badge', 'common')]);
    const tile = document.querySelector('[data-equip="region:KR-11010"]');
    expect(tile?.getAttribute('data-variant')).toBe('2');
    expect(tile?.querySelector('.variant')?.textContent).toBe('2회차');
    expect(document.querySelector('[data-equip="region:KR-11020"] .variant')).toBeNull();
  });

  it('계절 한정 배경은 받은 회차의 계절 이모지를 달고 어디서 받았는지 보인다', () => {
    const autumn = toDisplayItem({ itemId: 'season:autumn-2026', name: '2026 단풍 명소 배경', emoji: '🍁', slot: 'BG', tier: 'LEGEND', theme: 'autumn', look: null }, '2026 계절 한정 테마 완성 보상');
    panel([autumn]);
    const tile = document.querySelector('[data-equip="season:autumn-2026"]');
    expect(tile?.querySelector('.ach')?.textContent).toBe('🍁');
    expect(tile?.getAttribute('data-origin')).toBe('2026 계절 한정 테마 완성 보상');
  });
});
