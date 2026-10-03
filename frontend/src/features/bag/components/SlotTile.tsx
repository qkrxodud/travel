import { SLOT_NAME, type DisplayItem } from '../../../shared/lib/item/displayItem';
import type { ClientSlot } from '../../../shared/lib/pixel/looks';
import { TIER_LABEL } from '../../../shared/lib/region/catalog';
import { ItemImage } from '../../../shared/ui/ItemImage';

interface SlotTileProps {
  slot: ClientSlot;
  item: DisplayItem | undefined;
  /** 누르면 벗을 칸(prop:{id}·슬롯 이름, 비어 있으면 '') */
  unequipKey: string;
  onClick: (unequipKey: string) => void;
}

/** 장면 슬롯 칸(.stile) */
export function SlotTile({ slot, item, unequipKey, onClick }: SlotTileProps) {
  return (
    <button
      className={`stile ${item ? item.tier : 'empty'}`}
      data-unequip={item ? unequipKey : ''}
      title={item ? `${item.name} · 누르면 벗기` : `${SLOT_NAME[slot]} 비어 있음`}
      onClick={() => onClick(item ? unequipKey : '')}
    >
      {item ? <ItemImage item={item} /> : <span className="ph" />}
      <span className="sl">{SLOT_NAME[slot]}</span>
      {item ? <span className="tr">{TIER_LABEL[item.tier]}</span> : null}
    </button>
  );
}
