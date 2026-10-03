import { useSyncExternalStore } from 'react';
import type { SceneRequest } from '../../../api/types/wardrobe';
import type { DisplayItem } from '../../../shared/lib/item/displayItem';
import { SLOT_TO_SERVER } from '../../../shared/lib/item/displayItem';
import { pixelRenderer, sprites } from '../../../shared/lib/pixel';
import type { ClientSlot } from '../../../shared/lib/pixel/looks';
import { toast } from '../../../store/toastStore';
import type { WornEquipment } from '../../../shared/lib/item/equipment';
import { isSetReward } from '../model/bag';
import { SlotTile } from './SlotTile';

interface CharacterStageProps {
  equipment: WornEquipment;
  items: DisplayItem[];
  wornCount: number;
  sceneProps: string[];
  onEdit: (body: SceneRequest) => void;
}

const LEFT: readonly ClientSlot[] = ['hat', 'hand', 'back'];
const RIGHT: readonly ClientSlot[] = ['badge', 'pet', 'bg'];

/** 장면(배경·장식·캐릭터) + 슬롯 칸(누르면 벗기) + 성별 + 착용 통계 */
export function CharacterStage({ equipment, items, wornCount, sceneProps, onEdit }: CharacterStageProps) {
  useSyncExternalStore(sprites.subscribe, sprites.version);
  const unequip = (key: string) => {
    if (!key) {
      toast('!', '빈 슬롯', '아래 가방에서 장비를 눌러 장착하세요');
      return;
    }
    if (key.startsWith('prop:')) onEdit({ props: sceneProps.filter(id => id !== key.slice('prop:'.length)) });
    else {
      const slot = SLOT_TO_SERVER[key as ClientSlot];
      if (slot) onEdit({ unequip: [slot] });
    }
  };
  const slotItem = (slot: ClientSlot) => equipment[slot as Exclude<keyof WornEquipment, 'gender' | 'props'>];
  const legends = items.filter(item => !isSetReward(item) && item.tier === 'legend').length;
  const setRewards = items.filter(isSetReward).length;
  return (
    <>
      <div className="gender" role="group" aria-label="기본 캐릭터">
        <button data-gender="m" aria-pressed={equipment.gender === 'm'} onClick={() => onEdit({ gender: 'M' })}>남성 여행자</button>
        <button data-gender="f" aria-pressed={equipment.gender === 'f'} onClick={() => onEdit({ gender: 'F' })}>여성 여행자</button>
      </div>
      <div className="stage">
        <div className="charbox" id="bag-char">
          <img src={pixelRenderer.sceneUrl(equipment)} alt="내 캐릭터 장면" style={{ display: 'block', width: '100%', height: '100%', imageRendering: 'pixelated' }} />
        </div>
        <div className="slotcol left" id="slots-left">
          {LEFT.map(slot => <SlotTile key={slot} slot={slot} item={slotItem(slot)} unequipKey={slot} onClick={unequip} />)}
        </div>
        <div className="slotcol right" id="slots-right">
          {RIGHT.map(slot => <SlotTile key={slot} slot={slot} item={slotItem(slot)} unequipKey={slot} onClick={unequip} />)}
        </div>
      </div>
      <div className="scene-slots" id="bag-scene">
        {[0, 1, 2].map(i => {
          const prop = equipment.props[i];
          return <SlotTile key={i} slot="prop" item={prop} unequipKey={prop ? 'prop:' + prop.code : ''} onClick={unequip} />;
        })}
      </div>
      <div className="statrow" id="bag-stats">
        <span><b>{wornCount}</b> 착용</span><span><b>{items.length}</b> 보유</span><span><b>{legends}</b> 전설 풍경</span><span><b>{setRewards}</b> 세트 배경</span>
      </div>
    </>
  );
}
