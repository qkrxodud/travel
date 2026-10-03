import { useEffect } from 'react';
import { useInventory, useScene } from '../shared/queries/wardrobe';
import { useSession } from '../shared/queries/session';
import { useMyCards } from '../features/profile/queries';
import { useProgress } from '../shared/queries/progress';
import { usePercentile } from '../shared/queries/social';
import { useUiStore } from '../store/uiStore';

function setFlag(name: string, value: string): void {
  document.documentElement.dataset[name] = value;
}

/**
 * <html data-*> 표식 — 서버 값이 화면에 반영됐다는 신호(프로토타입과 같은 이름, E2E 가 기다리는 기준).
 * data-territory·data-map-id 는 지도 탭, data-social·data-vs 는 랭킹 탭이 단다.
 */
export function useDocumentFlags(): void {
  const { data: session, isFetched: sessionFetched } = useSession();
  const progress = useProgress();
  const inventory = useInventory();
  const scene = useScene();
  const { data: percentile, isFetched: percentileFetched } = usePercentile();
  const profileTab = useUiStore(state => state.tab === 'profile');
  const { data: cards } = useMyCards(profileTab);

  useEffect(() => {
    if (sessionFetched) setFlag('loggedIn', session?.loggedIn ? 'true' : 'false');
  }, [session, sessionFetched]);

  useEffect(() => {
    if (progress.isSuccess) setFlag('progress', String(progress.dataUpdatedAt));
  }, [progress.isSuccess, progress.dataUpdatedAt]);

  useEffect(() => {
    if (inventory.isSuccess && scene.isSuccess) setFlag('wardrobe', String(inventory.dataUpdatedAt + scene.dataUpdatedAt));
  }, [inventory.isSuccess, scene.isSuccess, inventory.dataUpdatedAt, scene.dataUpdatedAt]);

  useEffect(() => {
    if (percentileFetched) setFlag('percentile', percentile?.computed ? String(percentile.topPercent) : 'none');
  }, [percentile, percentileFetched]);

  useEffect(() => {
    if (cards) setFlag('profileVisibility', cards.visibility);
  }, [cards]);
}
