import type { MyVisit } from '../../../shared/lib/territory/visits';
import { SLOT_NAME } from '../../../shared/lib/item/displayItem';
import { wantedItems } from '../model/bag';
import type { Catalog } from '../../../shared/lib/region/catalog';
import { ItemImage } from '../../../shared/ui/ItemImage';
import { useUiStore } from '../../../store/uiStore';

/** 갖고 싶은 것 — 누르면 지도에서 위치를 보여 준다 */
export function WantList({ catalog, visited }: { catalog: Catalog | null; visited: ReadonlyMap<string, MyVisit> | null }) {
  const showOnMap = useUiStore(state => state.showOnMap);
  const wanted = catalog ? wantedItems(catalog, visited) : [];
  return (
    <div id="bag-want">
      <div className="inv">
        {wanted.map(({ code, item }, i) => (
          // 전설 지역이 배경 아이템이면 두 줄에 모두 나온다(프로토타입과 같게) — 키는 순번으로
          <button key={`${i}:${item.code}`} className={`item locked ${item.tier}`} data-show={code} title="지도에서 보기" onClick={() => showOnMap(code)}>
            <ItemImage item={item} />
            <span className="sb">{SLOT_NAME[item.slot]}</span>
            <span className="n">{item.name}</span>
          </button>
        ))}
      </div>
    </div>
  );
}
