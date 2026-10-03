import { useEffect, useRef } from 'react';
import { gameNews } from '../shared/lib/progress/news';
import { useCatalog } from '../shared/queries/catalog';
import { useMysteryThisWeek } from '../shared/queries/mystery';
import { useInventory } from '../shared/queries/wardrobe';
import { useProgress } from '../shared/queries/progress';
import { useSyncStore } from '../store/syncStore';
import { toast } from '../store/toastStore';
import { useUiStore } from '../store/uiStore';

/**
 * 반영 대기 창에서 새로 생긴 것을 알린다: 뱃지 획득·레벨 업·(8단계) 보호권 사용·연속 탐험 마일스톤·시·도 정복(GET /progress),
 * 이번 주 미스터리 보너스(GET /mystery/this-week), 세트 배경 해금(GET /inventory).
 * 시·도 정복은 지도 테두리도 잠깐 반짝인다(한 번짜리 지도 명령 — 재렌더가 다시 걸지 않는다).
 * 예시 채우기·전부 지우기·첫 로드·다른 탐험가로 바뀐 경우는 알리지 않는다.
 */
export function useAnnouncements(): void {
  const { data: progress } = useProgress();
  const { data: inventory } = useInventory();
  const { data: mystery } = useMysteryThisWeek();
  const catalog = useCatalog();
  const lastProgress = useRef(progress);
  const lastInventory = useRef(inventory);
  const lastMystery = useRef(mystery);
  const conquestBonus = catalog?.provinceConquestBonus;

  useEffect(() => {
    const before = lastProgress.current;
    lastProgress.current = progress;
    if (!before || !progress || before === progress || before.explorerId !== progress.explorerId) return;
    if (!useSyncStore.getState().announce) return;
    const had = new Set(before.badges.filter(badge => badge.earned).map(badge => badge.id));
    progress.badges.filter(badge => badge.earned && !had.has(badge.id)).forEach(badge => toast(badge.ico, `뱃지 획득 · ${badge.name}`, badge.desc));
    if (progress.level > before.level) toast('▲', `레벨 업 · Lv.${progress.level}`, progress.levelTitle);
    const news = gameNews(before, progress);
    if (news.freezeUsed) toast('🧊', `보호권 ${news.freezeUsed.count}개로 스트릭을 지켰어요`, `${progress.streak.months}개월 연속 탐험 유지`);
    news.milestones.forEach(milestone => toast('🔥', `연속 탐험 ${milestone.months}개월 달성`,
      `칭호 「${milestone.titleName}」 · +${milestone.xp} XP${milestone.freezes ? ` · 보호권 +${milestone.freezes}` : ''}`));
    news.conquered.forEach(province => toast('👑', `${province.name} 정복!`, `${conquestBonus !== undefined ? `+${conquestBonus} XP · ` : ''}대표 장식을 가방에 넣었어요`));
    if (news.conquered.length) {
      useUiStore.getState().sendMapCommand({ kind: 'flash-provinces', provinces: news.conquered.map(province => catalog?.provinceByCode.get(province.code) ?? province.name) });
    }
  }, [progress, catalog, conquestBonus]);

  useEffect(() => {
    const before = lastMystery.current;
    lastMystery.current = mystery;
    if (!before || !mystery || before === mystery || !useSyncStore.getState().announce) return;
    if (mystery.weekStart === before.weekStart && mystery.received && !before.received) {
      toast('❓', '이번 주 미스터리 지역을 찾았어요', `${mystery.region.provinceName} ${mystery.region.name} · +${mystery.bonusXp} XP`);
    }
  }, [mystery]);

  useEffect(() => {
    const before = lastInventory.current;
    lastInventory.current = inventory;
    if (!before || !inventory || before === inventory || !useSyncStore.getState().announce) return;
    const had = new Set(before.items.map(owned => owned.itemId));
    inventory.items.filter(owned => !had.has(owned.itemId) && owned.source === 'SET_REWARD')
      .forEach(owned => toast('✦', `세트 배경 해금 · ${owned.name ?? owned.itemId}`, '가방에서 배경으로 쓸 수 있어요'));
  }, [inventory]);
}
