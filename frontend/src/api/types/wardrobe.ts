import type { ItemLook, ServerSlot } from './catalog';
import type { Rarity } from './common';

/** WardrobeDtos.ItemResponse — 정의가 사라진 아이템은 itemId 말고 전부 null */
export interface ItemResponse {
  itemId: string;
  name: string | null;
  emoji: string | null;
  slot: ServerSlot | null;
  tier: Rarity | null;
  theme: string | null;
  look: ItemLook | null;
  regionCode: string | null;
}

export type ItemSource = 'REGION' | 'SET_REWARD' | 'EVENT';

/** OwnedItemResponse */
export interface OwnedItemResponse extends ItemResponse {
  source: ItemSource;
  acquiredAt: string;
  favorite: boolean;
  equipped: boolean;
}

/** GET /inventory — InventoryResponse */
export interface InventoryResponse {
  count: number;
  items: OwnedItemResponse[];
}

export type Gender = 'M' | 'F';
/** 장면 슬롯(장식 PROP 은 props 목록) */
export type SceneSlot = Exclude<ServerSlot, 'PROP'>;

/** GET /scene · PUT /scene — SceneResponse */
export interface SceneResponse {
  gender: Gender;
  slots: Partial<Record<SceneSlot, ItemResponse | null>>;
  props: ItemResponse[];
  stylePoints: number;
  wornCount: number;
  updatedAt: string | null;
}

/** PUT /scene — SceneRequest (비어 있는 필드는 그대로) */
export interface SceneRequest {
  gender?: Gender;
  equip?: Partial<Record<SceneSlot, string>>;
  unequip?: SceneSlot[];
  props?: string[];
}
