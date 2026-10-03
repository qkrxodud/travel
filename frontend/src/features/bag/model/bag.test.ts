import { describe, expect, it } from 'vitest';
import { toDisplayItem, type DisplayItem } from '../../../shared/lib/item/displayItem';
import { CATALOG } from '../../../test/fixtures';
import { filterAndSort, styleRank, wantedItems } from './bag';

const owned = (itemId: string, slot: DisplayItem['slot'], tier: DisplayItem['tier'], favorite = false): DisplayItem =>
  ({ ...toDisplayItem({ itemId, name: itemId, emoji: '🎁', slot: 'HAND', tier: 'COMMON', theme: null, look: null }, ''), slot, tier, favorite });

describe('가방', () => {
  it('즐겨찾기 먼저, 그다음 전설→희귀→일반, 같으면 서버 순서. 필터는 슬롯', () => {
    const items = [owned('a', 'hand', 'common'), owned('b', 'hat', 'legend'), owned('c', 'hand', 'rare'), owned('d', 'hand', 'common', true), owned('e', 'hand', 'common')];
    expect(filterAndSort(items, 'all').map(item => item.code)).toEqual(['d', 'b', 'c', 'a', 'e']);
    expect(filterAndSort(items, 'hat').map(item => item.code)).toEqual(['b']);
    expect(filterAndSort(items, 'pet')).toEqual([]);
  });

  it('꾸미기 점수(서버 계산) → 등급 문구', () => {
    expect([0, 1, 8, 18, 30].map(styleRank)).toEqual(['맨몸', '소박함', '제법 꾸밈', '화려함', '전설급 꾸미기']);
  });

  it('갖고 싶은 것: 안 간 전설 지역 + 안 간 곳의 배경·장식(카탈로그 순, 각 4개까지)', () => {
    const wanted = wantedItems(CATALOG, new Map());
    // 울릉군(전설·배경)은 두 목록에 모두 든다(프로토타입과 같게)
    expect(wanted.map(entry => entry.code)).toEqual(['37430', '37430']);
    expect(wanted[0].item).toMatchObject({ name: '울릉 독도 바다 풍경', slot: 'bg', tier: 'legend', from: '경북 울릉군' });
    expect(wantedItems(CATALOG, new Map([['37430', { code: '37430', date: '', memo: '', at: 0 }]]))).toEqual([]);
  });
});
