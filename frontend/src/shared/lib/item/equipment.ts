/** 서버 장면(GET /scene) → 픽셀 렌더러 착용 상태 / 서버 가방(GET /inventory) → 화면 아이템. */
import type { InventoryResponse, SceneResponse, SceneSlot } from '../../../api/types/wardrobe';
import { itemOrigin, SLOT_TO_CLIENT, toDisplayItem, type DisplayItem, type SetNameLookup } from './displayItem';
import type { Equipment } from '../pixel';
import type { Catalog } from '../region/catalog';

export interface WornEquipment extends Equipment {
  hat?: DisplayItem;
  hand?: DisplayItem;
  badge?: DisplayItem;
  back?: DisplayItem;
  pet?: DisplayItem;
  bg?: DisplayItem;
  props: DisplayItem[];
}

const SCENE_SLOTS: readonly SceneSlot[] = ['HAT', 'HAND', 'BADGE', 'BAG', 'PET', 'BG'];

/** 착용 상태(장면이 아직 없으면 맨몸 남성 여행자) */
export function sceneEquipment(scene: SceneResponse | undefined, catalog: Catalog | null, setName: SetNameLookup): WornEquipment {
  const worn: WornEquipment = { gender: scene?.gender === 'F' ? 'f' : 'm', props: [] };
  if (!scene) return worn;
  for (const slot of SCENE_SLOTS) {
    const served = scene.slots[slot];
    if (!served) continue;
    const clientSlot = SLOT_TO_CLIENT[slot] as Exclude<keyof WornEquipment, 'gender' | 'props'>;
    worn[clientSlot] = toDisplayItem(served, itemOrigin(served.itemId, catalog, setName));
  }
  worn.props = scene.props.map(served => toDisplayItem(served, itemOrigin(served.itemId, catalog, setName)));
  return worn;
}

/** 보유 아이템(서버 순서) */
export function inventoryItems(inventory: InventoryResponse | undefined, catalog: Catalog | null, setName: SetNameLookup): DisplayItem[] {
  if (!inventory) return [];
  return inventory.items.map(owned => toDisplayItem(owned, itemOrigin(owned.itemId, catalog, setName), owned));
}
