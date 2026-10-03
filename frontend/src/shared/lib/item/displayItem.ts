/**
 * 화면 아이템(가방 타일·슬롯·체크인 모달 보상·지도 캐릭터). 서버 아이템(카탈로그 정의·보유·장면) 값을 표시용으로 옮기기만 한다.
 */
import type { ItemView, ServerSlot } from '../../../api/types/catalog';
import type { ItemResponse, ItemSource, OwnedItemResponse, SceneSlot } from '../../../api/types/wardrobe';
import type { ClientSlot, PixelItem } from '../pixel/looks';
import { labelOfCode, toTier, type Catalog, type Tier } from '../region/catalog';

export interface DisplayItem extends PixelItem {
  name: string;
  tier: Tier;
  /** 툴팁 출처(지역 라벨 · 세트 완성 보상 · 이벤트 보상) */
  from: string;
  favorite: boolean;
  equipped: boolean;
  source: ItemSource | null;
}

export const SLOT_TO_CLIENT: Readonly<Record<ServerSlot, ClientSlot>> = { HAT: 'hat', HAND: 'hand', BADGE: 'badge', BAG: 'back', PET: 'pet', BG: 'bg', PROP: 'prop' };
export const SLOT_TO_SERVER: Readonly<Partial<Record<ClientSlot, SceneSlot>>> = { hat: 'HAT', hand: 'HAND', badge: 'BADGE', back: 'BAG', pet: 'PET', bg: 'BG' };
export const SLOT_NAME: Readonly<Record<ClientSlot, string>> = { hand: '손', badge: '키링', hat: '모자', back: '배낭', pet: '동행', bg: '배경', prop: '장식', aura: '오라' };

/** 세트 이름 찾기(도감 GET /collection 값) */
export type SetNameLookup = (setId: string) => string | undefined;

/** 아이템이 어디서 왔는지(툴팁) */
export function itemOrigin(itemId: string, catalog: Catalog | null, setName: SetNameLookup): string {
  if (itemId.startsWith('region:')) return catalog ? labelOfCode(catalog, itemId.slice('region:'.length)) : itemId;
  if (itemId.startsWith('set:')) {
    const setId = itemId.slice('set:'.length);
    return `${setName(setId) ?? setId} 세트 완성 보상`;
  }
  return '이벤트 보상';
}

type ServedItem = Pick<ItemResponse, 'itemId' | 'name' | 'emoji' | 'slot' | 'tier' | 'theme' | 'look'>;

/** 서버 아이템 → 화면 아이템. 정의가 사라진 아이템은 🎁·id 로 보인다(프로토타입과 같게). */
export function toDisplayItem(served: ServedItem, origin: string, owned?: Pick<OwnedItemResponse, 'favorite' | 'equipped' | 'source'>): DisplayItem {
  return {
    code: served.itemId,
    emoji: served.emoji || '🎁',
    name: served.name || served.itemId,
    slot: served.slot ? SLOT_TO_CLIENT[served.slot] : 'hand',
    tier: toTier(served.tier),
    theme: served.theme,
    look: served.look,
    from: origin,
    favorite: owned?.favorite ?? false,
    equipped: owned?.equipped ?? false,
    source: owned?.source ?? null,
  };
}

/** 카탈로그 정의(아직 갖지 않은 아이템 — 체크인 보상·갖고 싶은 것) → 화면 아이템 */
export function catalogItem(view: ItemView, origin: string): DisplayItem {
  return toDisplayItem(view, origin);
}
