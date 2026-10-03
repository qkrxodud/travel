import { useQuery } from '@tanstack/react-query';
import { catalogApi } from '../../api/catalog';
import { buildCatalog, type Catalog } from '../lib/region/catalog';

export const catalogKeys = {
  index: () => ['catalog', 'index'] as const,
};

/** 카탈로그(GeoJSON·시·도·아이템 정의·보상 규칙) — 정적 참조 데이터라 한 번만 받는다. */
export function useCatalog(): Catalog | null {
  const { data } = useQuery({
    queryKey: catalogKeys.index(),
    queryFn: async () => {
      const [geo, provinces, items, rules] = await Promise.all([
        catalogApi.regionsGeoJson(), catalogApi.provinces(), catalogApi.items(), catalogApi.rewardRules(),
      ]);
      return buildCatalog(geo, provinces, items, rules);
    },
    staleTime: Infinity,
    gcTime: Infinity,
    structuralSharing: false,
  });
  return data ?? null;
}
