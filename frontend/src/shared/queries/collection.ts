import { useQuery } from '@tanstack/react-query';
import { useCallback } from 'react';
import { progressionApi } from '../../api/progression';
import { SETTLED_ROOT, settleInterval } from '../../store/syncStore';

export const collectionKeys = {
  collection: () => [SETTLED_ROOT, 'collection'] as const,
};

/** GET /collection — 지도 기준 도감(서버 값). 완성 기록은 지역을 취소해도 남는다. */
export function useCollection() {
  return useQuery({ queryKey: collectionKeys.collection(), queryFn: progressionApi.collection, refetchInterval: settleInterval });
}

/** 세트 id → 이름(도감 서버 값) — 아이템 툴팁·친구 소식 문구 */
export function useSetNameLookup(): (setId: string) => string | undefined {
  const { data } = useCollection();
  const sets = data?.sets;
  return useCallback((setId: string) => sets?.find(set => set.id === setId)?.name, [sets]);
}
