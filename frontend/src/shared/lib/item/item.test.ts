/**
 * 화면 아이템 — 서버 아이템(카탈로그 정의·보유·장면)을 가방 타일·캐릭터가 쓰는 모양으로 옮긴다. 값은 서버 그대로다.
 * 이야기 순서: 아이템 출처 → 아이템 표시 → 재방문 2회차 색 → 지금 입은 옷차림 → 가방.
 */
import { describe, expect, it } from 'vitest';
import type { InventoryResponse, ItemResponse, SceneResponse } from '../../../api/types/wardrobe';
import { CATALOG } from '../../../test/fixtures';
import { achievementOf, catalogItem, itemOrigin, SLOT_NAME, toDisplayItem } from './displayItem';
import { inventoryItems, sceneEquipment } from './equipment';

const served = (itemId: string, overrides: Partial<ItemResponse> = {}): ItemResponse =>
  ({ itemId, name: '청사초롱 등불', emoji: '🏮', slot: 'HAND', tier: 'COMMON', theme: null, look: null, ...overrides } as ItemResponse);
const setName = (setId: string) => (setId === 'jiri' ? '지리산 둘레' : undefined);

describe('아이템 출처', () => {
  it('지역 아이템은 받은 지역 이름으로 알려 준다', () => {
    expect(itemOrigin('region:KR-11010', CATALOG, setName)).toBe('서울 종로구');
  });

  it('세트 보상은 세트 이름으로, 이름을 모르면 세트 코드로 알려 준다', () => {
    expect(itemOrigin('set:jiri', CATALOG, setName)).toBe('지리산 둘레 세트 완성 보상');
    expect(itemOrigin('set:unknown', CATALOG, setName)).toBe('unknown 세트 완성 보상');
  });

  it('시·도 정복 대표 장식은 정복한 시·도 이름으로, 이름을 모르면 시·도 코드로 알려 준다', () => {
    expect(itemOrigin('conquest:KR-11', CATALOG, setName)).toBe('서울 정복 보상');
    expect(itemOrigin('conquest:KR-29', CATALOG, setName)).toBe('KR-29 정복 보상');
    expect(achievementOf('conquest:KR-11')).toBe('conquest');
  });

  it('연속 탐험 마일스톤 아이템은 몇 개월 연속 탐험 보상인지 알려 준다', () => {
    expect(itemOrigin('streak:3', CATALOG, setName)).toBe('3개월 연속 탐험 보상');
    expect(achievementOf('streak:12')).toBe('streak');
  });

  it('지역·세트 아이템은 업적 보상으로 표시하지 않는다', () => {
    expect(achievementOf('region:KR-11010')).toBeNull();
    expect(achievementOf('set:jiri')).toBeNull();
  });

  it('계절 한정 배경은 그 회차 연도의 계절 한정 테마 완성 보상이고 업적 보상으로 표시한다', () => {
    expect(itemOrigin('season:autumn-2026', CATALOG, setName)).toBe('2026 계절 한정 테마 완성 보상');
    expect(achievementOf('season:spring-2027')).toBe('season');
  });

  it('그 밖의 아이템은 이벤트 보상이다', () => {
    expect(itemOrigin('event:chuseok', CATALOG, setName)).toBe('이벤트 보상');
  });
});

describe('아이템 표시', () => {
  it('서버 칸 이름을 화면 칸으로 바꾸고 희귀도·즐겨찾기·착용 여부를 함께 담는다', () => {
    const shown = toDisplayItem(served('region:KR-11010', { slot: 'BAG', tier: 'RARE' }), '서울 종로구', { favorite: true, equipped: true, source: 'REGION' });
    expect(shown).toMatchObject({ code: 'region:KR-11010', slot: 'back', tier: 'rare', from: '서울 종로구', favorite: true, equipped: true });
    expect(SLOT_NAME[shown.slot]).toBe('배낭');
  });

  it('카탈로그에서 사라진 아이템은 선물 상자와 아이템 코드로 보인다', () => {
    expect(toDisplayItem(served('gone', { name: '', emoji: '' }), '')).toMatchObject({ emoji: '🎁', name: 'gone' });
  });

  it('아직 갖지 않은 카탈로그 아이템은 즐겨찾기·착용 없이 보인다', () => {
    const view = CATALOG.itemById.get('region:KR-31370');
    expect(view && catalogItem(view, '경기 가평군')).toMatchObject({ name: '가평 잣 다람쥐', slot: 'pet', tier: 'rare', favorite: false, equipped: false, source: null });
  });
});

describe('재방문 2회차 색', () => {
  const swapped = { type: 'lantern', primary: '#f4c542', secondary: '#e63946' };
  const lanternLook = { type: 'lantern', primary: '#e63946', secondary: '#f4c542' };

  it('다시 다녀와 받은 2회차 특산물은 서버가 준 변형 색으로 그리고, 같은 아이템이라 입히기는 그대로다', () => {
    const shown = toDisplayItem(served('region:KR-11010', { look: lanternLook, variant: 2, variantLook: swapped }), '서울 종로구');
    expect(shown).toMatchObject({ code: 'region:KR-11010', variant: 2, look: swapped });
  });

  it('기본 특산물은 원래 색 그대로다', () => {
    expect(toDisplayItem(served('region:KR-11010', { look: lanternLook, variant: 1, variantLook: null }), '')).toMatchObject({ variant: 1, look: lanternLook });
  });

  it('입은 2회차 특산물도 변형 색으로 그린다', () => {
    const scene = { gender: 'M', slots: { HAND: served('region:KR-11010', { look: lanternLook, variant: 2, variantLook: swapped }) }, props: [], stylePoints: 0, wornCount: 1, updatedAt: null } as unknown as SceneResponse;
    expect(sceneEquipment(scene, CATALOG, setName).hand).toMatchObject({ variant: 2, look: swapped });
  });

  it('아직 갖지 않은 카탈로그 아이템은 변형 색이 있어도 기본 색으로 보인다', () => {
    const view = CATALOG.itemById.get('region:KR-11010');
    expect(view && catalogItem(view, '')).toMatchObject({ variant: 1, look: view?.look });
  });
});

describe('지금 입은 옷차림', () => {
  it('장면이 아직 없으면 맨몸의 남성 여행자다', () => {
    expect(sceneEquipment(undefined, CATALOG, setName)).toEqual({ gender: 'm', props: [] });
  });

  it('서버 장면의 성별·칸마다 입은 것·장식을 그대로 옮긴다', () => {
    const scene = { gender: 'F', slots: { HAND: served('region:KR-11010'), HAT: null }, props: [served('set:jiri', { slot: 'PROP' })], stylePoints: 3, wornCount: 2, updatedAt: null } as SceneResponse;
    const worn = sceneEquipment(scene, CATALOG, setName);
    expect(worn.gender).toBe('f');
    expect(worn.hand).toMatchObject({ code: 'region:KR-11010', from: '서울 종로구' });
    expect(worn.hat).toBeUndefined();
    expect(worn.props.map(prop => prop.from)).toEqual(['지리산 둘레 세트 완성 보상']);
  });
});

describe('가방', () => {
  it('가진 아이템을 서버가 준 순서대로 즐겨찾기·착용 표시와 함께 보여 주고, 아직 못 읽었으면 비어 있다', () => {
    const inventory = { items: [{ ...served('region:KR-11010'), favorite: true, equipped: false, source: 'REGION' }, { ...served('set:jiri'), favorite: false, equipped: true, source: 'SET_REWARD' }] } as unknown as InventoryResponse;
    expect(inventoryItems(inventory, CATALOG, setName).map(item => [item.code, item.favorite, item.equipped])).toEqual([['region:KR-11010', true, false], ['set:jiri', false, true]]);
    expect(inventoryItems(undefined, CATALOG, setName)).toEqual([]);
  });
});
