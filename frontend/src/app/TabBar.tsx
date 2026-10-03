import { useQueryClient } from '@tanstack/react-query';
import { useInventory } from '../shared/queries/wardrobe';
import { useCollection } from '../shared/queries/collection';
import { sharingKeys } from '../features/profile/queries';
import { useQuests } from '../features/quests/queries';
import { socialKeys } from '../shared/queries/social';
import { useUiStore, type Tab } from '../store/uiStore';

const LABELS: readonly (readonly [Tab, string])[] = [['map', '지도'], ['bag', '가방'], ['sets', '도감'], ['quests', '퀘스트'], ['rank', '랭킹'], ['profile', '프로필']];

/** 탭 바. 랭킹·프로필 탭은 누를 때마다 그 탭의 서버 값을 다시 읽는다(프로토타입과 같게). */
export function TabBar() {
  const queryClient = useQueryClient();
  const tab = useUiStore(state => state.tab);
  const setTab = useUiStore(state => state.setTab);
  const { data: inventory } = useInventory();
  const { data: collection } = useCollection();
  const { data: quests } = useQuests();
  const counts: Partial<Record<Tab, number>> = {
    bag: inventory?.items.length ?? 0,
    sets: collection?.completed ?? 0,
    quests: quests?.monthly.filter(quest => quest.claimable).length ?? 0,
  };
  const choose = (next: Tab) => {
    setTab(next);
    if (next === 'rank') void queryClient.invalidateQueries({ queryKey: socialKeys.all() });
    if (next === 'profile') void queryClient.invalidateQueries({ queryKey: sharingKeys.all() });
  };
  return (
    <nav className="tabs" role="tablist" id="tabs">
      {LABELS.map(([key, label]) => (
        <button key={key} role="tab" aria-selected={tab === key} data-tab={key} onClick={() => choose(key)}>
          {label}
          {key in counts ? <span className="n" id={`n-${key}`}>{counts[key] || ''}</span> : null}
        </button>
      ))}
    </nav>
  );
}

