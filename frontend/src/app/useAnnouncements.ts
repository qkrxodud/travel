import { useEffect, useRef } from 'react';
import { useInventory } from '../shared/queries/wardrobe';
import { useProgress } from '../shared/queries/progress';
import { useSyncStore } from '../store/syncStore';
import { toast } from '../store/toastStore';

/**
 * 반영 대기 창에서 새로 생긴 것을 알린다: 뱃지 획득·레벨 업(GET /progress), 세트 배경 해금(GET /inventory).
 * 예시 채우기·전부 지우기·첫 로드·다른 탐험가로 바뀐 경우는 알리지 않는다.
 */
export function useAnnouncements(): void {
  const { data: progress } = useProgress();
  const { data: inventory } = useInventory();
  const lastProgress = useRef(progress);
  const lastInventory = useRef(inventory);

  useEffect(() => {
    const before = lastProgress.current;
    lastProgress.current = progress;
    if (!before || !progress || before === progress || before.explorerId !== progress.explorerId) return;
    if (!useSyncStore.getState().announce) return;
    const had = new Set(before.badges.filter(badge => badge.earned).map(badge => badge.id));
    progress.badges.filter(badge => badge.earned && !had.has(badge.id)).forEach(badge => toast(badge.ico, `뱃지 획득 · ${badge.name}`, badge.desc));
    if (progress.level > before.level) toast('▲', `레벨 업 · Lv.${progress.level}`, progress.levelTitle);
  }, [progress]);

  useEffect(() => {
    const before = lastInventory.current;
    lastInventory.current = inventory;
    if (!before || !inventory || before === inventory || !useSyncStore.getState().announce) return;
    const had = new Set(before.items.map(owned => owned.itemId));
    inventory.items.filter(owned => !had.has(owned.itemId) && owned.source === 'SET_REWARD')
      .forEach(owned => toast('✦', `세트 배경 해금 · ${owned.name ?? owned.itemId}`, '가방에서 배경으로 쓸 수 있어요'));
  }, [inventory]);
}
