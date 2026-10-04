/**
 * 지도 탭의 서버 변경(체크인·수정·취소·예시 채우기·이의)과 지도 탭 동작 훅. 읽기 쿼리(영토·지도 목록·상세)는
 * 여러 기능이 함께 쓰므로 shared/queries/territory 에 있다(키도 거기 mapKeys 하나).
 */
import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { useCallback } from 'react';
import { devApi } from '../../api/dev';
import { explorationApi, type CheckInInput } from '../../api/exploration';
import type { TerritoryResponse } from '../../api/types/exploration';
import { cardCanvas, recentCard } from '../../shared/lib/cards';
import { catalogItem, itemOrigin } from '../../shared/lib/item/displayItem';
import type { Catalog } from '../../shared/lib/region/catalog';
import type { MyVisit } from '../../shared/lib/territory/visits';
import { useCollection } from '../../shared/queries/collection';
import { recapKeys } from '../../shared/queries/recap';
import { usePercentile } from '../../shared/queries/social';
import { mapKeys } from '../../shared/queries/territory';
import { wishlistKeys } from '../../shared/queries/wishlist';
import { SETTLED_ROOT, settleAfterChange, settleInterval } from '../../store/syncStore';
import { toast, toastError } from '../../store/toastStore';
import { useUiStore } from '../../store/uiStore';
import { setChips } from './model/territory';

/** GET /visits/preview — 체크인 모달의 예상 XP·받을 아이템(열 때마다 새로) */
export function usePreview(code: string | null, mapId: string | null) {
  return useQuery({
    queryKey: mapKeys.preview(mapId, code ?? ''),
    queryFn: () => explorationApi.preview(code as string, mapId),
    enabled: !!code,
    staleTime: 0,
    gcTime: 0,
  });
}

/** 영토가 바뀐 뒤: 영토(+공유 지도 상세)를 다시 읽고, 진행·가방·도감·리캡 반영 대기 창을 연다(리캡 키도 SETTLED_ROOT 아래). */
async function afterVisitChange(queryClient: QueryClient, announce: boolean): Promise<void> {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: mapKeys.territories() }),
    queryClient.invalidateQueries({ queryKey: mapKeys.details() }),
  ]);
  void settleAfterChange(queryClient, announce);
}

export function useCheckIn() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: CheckInInput) => explorationApi.checkIn(input),
    onSuccess: async () => {
      useUiStore.getState().setSampleMode(false);
      await afterVisitChange(queryClient, true);
    },
  });
}

/** 기록 수정 — 날짜가 바뀌면 리캡의 달·연도도 바뀐다. */
export function useEditVisit() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ code, visitDate, memo, mapId }: { code: string; visitDate: string; memo: string; mapId: string | null }) =>
      explorationApi.editVisit(code, visitDate, memo, mapId),
    onSuccess: async () => {
      useUiStore.getState().setSampleMode(false);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: mapKeys.territories() }),
        queryClient.invalidateQueries({ queryKey: recapKeys.all() }),
      ]);
    },
  });
}

export function useCancelVisit() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ code, mapId }: { code: string; mapId: string | null }) => explorationApi.cancelVisit(code, mapId),
    onSuccess: async () => {
      useUiStore.getState().setSampleMode(false);
      await afterVisitChange(queryClient, true);
    },
  });
}

/** 예시 다시 채우기(local 전용 POST /dev/seed) — 알림 없이 반영 */
export function useSeed() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: devApi.seed,
    onSuccess: async () => {
      useUiStore.getState().setSampleMode(true);
      await afterVisitChange(queryClient, false);
    },
  });
}

/** 전부 지우기(local 전용 DELETE /dev/visits) */
export function useClearVisits() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: devApi.clearVisits,
    onSuccess: async () => {
      useUiStore.getState().setSampleMode(false);
      await afterVisitChange(queryClient, false);
    },
  });
}

export const revisitKeys = {
  status: (code: string) => [SETTLED_ROOT, 'revisit', code] as const,
};

/** GET /revisits/{code} — 고른 지역의 "다시 다녀왔어요" 판정(칠했는지·처음 칠한 해·도장 연도). 체크인·도장 뒤 반영 대기 창에서 함께 다시 읽는다. */
export function useRevisitStatus(code: string | null) {
  return useQuery({
    queryKey: revisitKeys.status(code ?? ''),
    queryFn: () => explorationApi.revisitStatus(code as string),
    enabled: !!code,
    refetchInterval: settleInterval,
    meta: { silent: true },
  });
}

/** POST /revisits/{code} — 도장(+XP·뱃지·색 변형은 이벤트로 늦게 반영 → 반영 대기 창, 알림 켬) */
export function useStamp() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (code: string) => explorationApi.stamp(code),
    // 다시 읽기를 기다리지 않는다(버튼은 바로 풀리고, 값은 반영 대기 창이 채운다)
    onSuccess: () => {
      void settleAfterChange(queryClient, true);
    },
    // 누르는 사이 판정이 바뀌었으면(해·하루 상한) 안내를 서버 판정으로 다시 맞춘다
    onError: (_error, code) => {
      void queryClient.invalidateQueries({ queryKey: revisitKeys.status(code) });
    },
  });
}

/** PUT /wishlist/{code} — 꽂은 뒤 전체 목록을 돌려주므로 그 값으로 바꾼다. 거절되면(가득·이미 칠함) 목록과 판정을 다시 읽어 토글을 맞춘다 */
export function usePin() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (code: string) => explorationApi.pin(code),
    onSuccess: wishlist => queryClient.setQueryData(wishlistKeys.list(), wishlist),
    onError: (_error, code) => {
      void queryClient.invalidateQueries({ queryKey: wishlistKeys.list() });
      void queryClient.invalidateQueries({ queryKey: revisitKeys.status(code) });
    },
  });
}

/** DELETE /wishlist/{code} */
export function useUnpin() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (code: string) => explorationApi.unpin(code),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: wishlistKeys.list() });
    },
  });
}

export function useDispute() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ mapId, code, memberId, disputed }: { mapId: string; code: string; memberId: string; disputed: boolean }) =>
      explorationApi.dispute(mapId, code, memberId, disputed),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: mapKeys.territories() }),
  });
}

/** 지도 탭 동작(지역 클릭·체크인 취소·여행 카드) — 컴포넌트는 그리기만 하고 흐름은 여기서. */
export function useMapActions(catalog: Catalog | null, visits: ReadonlyMap<string, MyVisit> | null, mapId: string | null) {
  const { mutateAsync: cancelVisitAsync, isPending: cancelling } = useCancelVisit();
  const { data: collection } = useCollection();
  const { data: percentile } = usePercentile();
  const queryClient = useQueryClient();

  const cancel = useCallback(async (code: string) => {
    try {
      await cancelVisitAsync({ code, mapId });
      toast('✓', '영토에서 제거', catalog?.byCode.get(code)?.properties.name ?? code);
    } catch (error) {
      toastError(error);
    }
  }, [cancelVisitAsync, catalog, mapId]);

  const clickRegion = useCallback((code: string) => {
    const store = useUiStore.getState();
    store.select(code);
    if (store.mode !== 'paint') return;
    if (visits?.has(code)) void cancel(code);
    else store.openCheckin(code);
  }, [visits, cancel]);

  /**
   * 여행 카드(화면에서 그린 1200×630) — 지금 내 영토 기준. 개인 지도면 "전국 n%"는 서버 정복률(conquest.percent)을 쓴다 —
   * 그리는 순간 캐시의 영토를 읽는다(체크인 직후 "기록하고 카드"는 mutation 이 영토를 다시 읽은 뒤에 부른다).
   * 공유 지도의 서버 정복률은 지도 전체 기준이라 대응 값이 없어 카드가 내 방문 수로 센다.
   */
  const showRecentCard = useCallback((code: string, current: ReadonlyMap<string, MyVisit> | null = visits) => {
    if (!catalog || !current) return;
    const territory = queryClient.getQueryData<TerritoryResponse>(mapKeys.territory(mapId));
    const conquestPercent = territory?.mapKind === 'PERSONAL' ? territory.conquest.percent : null;
    const view = catalog.itemByRegion.get(code);
    const url = recentCard(cardCanvas, {
      catalog,
      code,
      visits: current,
      sets: setChips(collection?.sets ?? [], code, new Set(current.keys())),
      item: view ? catalogItem(view, itemOrigin(view.itemId, catalog, () => undefined)) : null,
      topPercent: percentile?.computed ? String(percentile.topPercent) : '—',
      conquestPercent,
    });
    if (url) useUiStore.getState().showCard(url);
  }, [catalog, visits, collection, percentile, queryClient, mapId]);

  return { clickRegion, cancel, showRecentCard, cancelling };
}
