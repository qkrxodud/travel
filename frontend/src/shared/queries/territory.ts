/**
 * 지금 보는 지도의 영토·지도 목록·공유 지도 상세 — 지도·가방·랭킹·프로필·공유 지도·앱 셸이 함께 읽는 서버 상태.
 * 변경(체크인·지도 만들기 등) mutation 은 각 기능의 queries.ts 에 있고, 키는 이 파일의 mapKeys 하나만 쓴다.
 */
import { useQuery, type QueryClient } from '@tanstack/react-query';
import { useMemo } from 'react';
import { explorationApi } from '../../api/exploration';
import type { MapSummaryResponse } from '../../api/types/exploration';
import { territorySettleInterval } from '../../store/syncStore';
import { useUiStore } from '../../store/uiStore';
import { myVisits } from '../lib/territory/visits';

export const mapKeys = {
  territory: (mapId: string | null) => ['territory', mapId] as const,
  territories: () => ['territory'] as const,
  maps: () => ['maps', 'list'] as const,
  detail: (mapId: string) => ['maps', 'detail', mapId] as const,
  details: () => ['maps', 'detail'] as const,
  preview: (mapId: string | null, code: string) => ['visit-preview', mapId, code] as const,
};

/** GET /territory — 지금 보는 지도의 영토(정복률·시·도별·일지의 기준) */
export function useTerritory(mapId: string | null) {
  return useQuery({ queryKey: mapKeys.territory(mapId), queryFn: () => explorationApi.territory(mapId), refetchInterval: territorySettleInterval, meta: { silent: true } });
}

/** GET /maps — 지도 선택 목록 */
export function useMaps() {
  return useQuery({ queryKey: mapKeys.maps(), queryFn: explorationApi.maps });
}

/** GET /maps/{id} — 공유 지도일 때 멤버·색·설정·초대코드 */
export function useMapDetail(mapId: string | null) {
  return useQuery({
    queryKey: mapKeys.detail(mapId ?? ''),
    queryFn: () => explorationApi.mapDetail(mapId as string),
    enabled: !!mapId,
    meta: { silent: true },
  });
}

/** 지금 보는 지도의 영토 + (공유 지도면) 멤버 정보 + 내 방문. visits 가 null 이면 아직 준비 전. */
export function useMyTerritory() {
  const mapId = useUiStore(state => state.mapId);
  const territoryQuery = useTerritory(mapId);
  const territory = territoryQuery.data ?? null;
  const sharedMapId = territory?.mapKind === 'SHARED' ? territory.mapId : null;
  const detailQuery = useMapDetail(sharedMapId);
  const detail = sharedMapId ? detailQuery.data ?? null : null;
  const visits = useMemo(() => (territory ? myVisits(territory, detail) : null), [territory, detail]);
  return { mapId, territory, territoryQuery, detail, visits };
}

/** 지도 목록을 다시 읽고 그 지도로 옮긴다(개인 지도면 mapId=null). */
export async function switchToMap(queryClient: QueryClient, mapId: string | null): Promise<void> {
  await queryClient.invalidateQueries({ queryKey: mapKeys.maps() });
  const maps = queryClient.getQueryData<MapSummaryResponse[]>(mapKeys.maps()) ?? [];
  const personal = maps.find(map => map.kind === 'PERSONAL');
  useUiStore.getState().setMapId(personal && mapId === personal.mapId ? null : mapId);
}
