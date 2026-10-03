/** 꾸미기 서버 상태(가방·장면) — 가방 탭·지도 위 캐릭터·앱 셸이 함께 읽는다. */
import { useQuery } from '@tanstack/react-query';
import { useMemo } from 'react';
import { wardrobeApi } from '../../api/wardrobe';
import { SETTLED_ROOT, settleInterval } from '../../store/syncStore';
import { sceneEquipment, type WornEquipment } from '../lib/item/equipment';
import type { SetNameLookup } from '../lib/item/displayItem';
import type { Catalog } from '../lib/region/catalog';

export const wardrobeKeys = {
  inventory: () => [SETTLED_ROOT, 'inventory'] as const,
  scene: () => [SETTLED_ROOT, 'scene'] as const,
};

/** GET /inventory — 보유 아이템(지급은 체크인 후 이벤트로) */
export function useInventory() {
  return useQuery({ queryKey: wardrobeKeys.inventory(), queryFn: wardrobeApi.inventory, refetchInterval: settleInterval });
}

/** GET /scene — 착용·장식·성별·꾸미기 점수(서버 계산) */
export function useScene() {
  return useQuery({ queryKey: wardrobeKeys.scene(), queryFn: wardrobeApi.scene, refetchInterval: settleInterval });
}

/** 지금 착용 상태(지도 위 캐릭터·가방 장면 공용) */
export function useEquipment(catalog: Catalog | null, setName: SetNameLookup): WornEquipment {
  const { data: scene } = useScene();
  return useMemo(() => sceneEquipment(scene, catalog, setName), [scene, catalog, setName]);
}
