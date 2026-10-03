import { useMemo } from 'react';
import { toastError } from '../../../store/toastStore';
import { useUiStore } from '../../../store/uiStore';
import { useCatalog } from '../../../shared/queries/catalog';
import { useSetNameLookup } from '../../../shared/queries/collection';
import { useMyTerritory } from '../../../shared/queries/territory';
import { inventoryItems } from '../../../shared/lib/item/equipment';
import { styleRank } from '../model/bag';
import { useEditScene } from '../queries';
import { useEquipment, useInventory, useScene } from '../../../shared/queries/wardrobe';
import { CharacterStage } from './CharacterStage';
import { InventoryPanel } from './InventoryPanel';
import { WantList } from './WantList';

/** 가방 탭: 내 캐릭터(장면·슬롯·장식) · 갖고 싶은 것 · 특산물 아이템 — 보유·착용·점수는 서버 값 */
export function BagTab() {
  const tab = useUiStore(state => state.tab);
  const catalog = useCatalog();
  const setName = useSetNameLookup();
  const { data: inventory } = useInventory();
  const { data: scene } = useScene();
  const equipment = useEquipment(catalog, setName);
  const { visits } = useMyTerritory();
  const items = useMemo(() => inventoryItems(inventory, catalog, setName), [inventory, catalog, setName]);
  const editScene = useEditScene();
  const points = scene?.stylePoints ?? 0;

  const edit = (body: Parameters<typeof editScene.mutateAsync>[0]) => {
    editScene.mutateAsync(body).catch(error => toastError(error));
  };

  return (
    <section id="tab-bag" hidden={tab !== 'bag'} className="bagwrap">
      <div className="side">
        <div className="card">
          <h2>내 캐릭터 <span id="bag-style">{`꾸미기 ${points}점 · ${styleRank(points)}`}</span></h2>
          <CharacterStage equipment={equipment} items={items} wornCount={scene?.wornCount ?? 0} sceneProps={scene?.props.map(prop => prop.itemId) ?? []} onEdit={edit} />
        </div>
        <div className="card">
          <h2>갖고 싶은 것 <span>누르면 지도에서 위치를 보여줘요</span></h2>
          <WantList catalog={catalog} visited={visits} />
        </div>
      </div>
      <InventoryPanel items={items} sceneProps={scene?.props.map(prop => prop.itemId) ?? []} onEdit={edit} />
    </section>
  );
}
