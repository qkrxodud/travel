import type { MouseEvent } from 'react';
import type { SceneRequest } from '../../../api/types/wardrobe';
import { achievementOf, REVISIT_VARIANT, SLOT_NAME, SLOT_TO_SERVER, type DisplayItem } from '../../../shared/lib/item/displayItem';
import { ItemImage } from '../../../shared/ui/ItemImage';
import { toast, toastError } from '../../../store/toastStore';
import { useUiStore } from '../../../store/uiStore';
import { BAG_FILTERS, filterAndSort, isSetReward } from '../model/bag';
import { useFavorite } from '../queries';

/** 업적 보상 표시(시·도 정복 👑 · 연속 탐험 🔥 · 계절 한정은 그 배경 이모지) */
const ACHIEVEMENT_MARK = { conquest: '👑', streak: '🔥' } as const;
const achievementMark = (kind: 'conquest' | 'streak' | 'season', item: DisplayItem) => (kind === 'season' ? item.emoji : ACHIEVEMENT_MARK[kind]);

const MAX_PROPS = 3;

interface InventoryPanelProps {
  items: DisplayItem[];
  sceneProps: string[];
  onEdit: (body: SceneRequest) => void;
}

/** 특산물 아이템(필터 + 타일). 누르면 착용 · 다시 누르면 해제, ★ 즐겨찾기 */
export function InventoryPanel({ items, sceneProps, onEdit }: InventoryPanelProps) {
  const filter = useUiStore(state => state.bagFilter);
  const setBagFilter = useUiStore(state => state.setBagFilter);
  const favorite = useFavorite();
  const list = filterAndSort(items, filter);

  const toggle = (item: DisplayItem) => {
    if (item.slot === 'prop') {
      const props = [...sceneProps];
      const at = props.indexOf(item.code);
      if (at >= 0) props.splice(at, 1);
      else if (props.length < MAX_PROPS) props.push(item.code);
      else {
        toast('!', '장식은 3개까지', '하나를 벗고 다시 눌러주세요');
        return;
      }
      onEdit({ props });
      return;
    }
    const slot = SLOT_TO_SERVER[item.slot];
    if (!slot) return;
    if (item.equipped) onEdit({ unequip: [slot] });
    else onEdit({ equip: { [slot]: item.code } });
  };
  const star = (event: MouseEvent, item: DisplayItem) => {
    event.stopPropagation();
    favorite.mutateAsync({ itemId: item.code, favorite: !item.favorite }).catch(error => toastError(error));
  };

  return (
    <div className="card">
      <h2>특산물 아이템 <span>누르면 착용 · 다시 누르면 해제</span></h2>
      <div className="filters" id="bag-filters">
        {BAG_FILTERS.map(([key, label]) => (
          <button key={key} data-filter={key} aria-pressed={filter === key} onClick={() => setBagFilter(key)}>{label}</button>
        ))}
      </div>
      <div id="bag-inv">
        {list.length ? (
          <div className="inv">
            {list.map(item => {
              const achievement = achievementOf(item.code);
              const revisited = item.variant === REVISIT_VARIANT;
              return (
                <button
                  key={item.code}
                  className={`item ${item.tier} ${item.equipped ? 'on' : ''} ${isSetReward(item) ? 'set' : ''}`}
                  data-equip={item.code}
                  title={`${item.name} · ${item.from}${revisited ? ' · 재방문 2회차 색' : ''}`}
                  data-origin={item.from}
                  data-variant={item.variant}
                  onClick={() => toggle(item)}
                >
                  <ItemImage item={item} />
                  {achievement ? <span className={`ach ${achievement}`} title={item.from}>{achievementMark(achievement, item)}</span> : null}
                  {revisited ? <span className="variant" title="재방문 도장으로 받은 2회차 색">2회차</span> : null}
                  <span className="sb">{SLOT_NAME[item.slot]}</span>
                  <span className="n">{item.name}</span>
                  <span className={`fav ${item.favorite ? 'on' : ''}`} data-fav={item.code} role="button" aria-pressed={item.favorite} title="즐겨찾기" aria-label="즐겨찾기" onClick={event => star(event, item)}>
                    {item.favorite ? '★' : '☆'}
                  </span>
                </button>
              );
            })}
          </div>
        ) : <p className="empty">아직 이 종류의 장비가 없어요. 지도를 더 칠해보세요.</p>}
      </div>
    </div>
  );
}
