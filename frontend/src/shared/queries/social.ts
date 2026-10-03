/** 소셜 쿼리 키(랭킹 탭이 열릴 때 socialKeys.all 로 한 번에 무효화) + 여러 화면이 읽는 상위 %. */
import { useQuery } from '@tanstack/react-query';
import { socialApi } from '../../api/social';

export const socialKeys = {
  all: () => ['social'] as const,
  friends: () => ['social', 'friends'] as const,
  feed: () => ['social', 'feed'] as const,
  friendRanking: () => ['social', 'friend-ranking'] as const,
  mapRanking: (mapId: string) => ['social', 'map-ranking', mapId] as const,
  percentile: () => ['social', 'percentile'] as const,
  compare: (handle: string) => ['social', 'compare', handle] as const,
};

/** 상위 %(일 1회 배치 값) — 실패하면 "—" */
export function usePercentile() {
  return useQuery({ queryKey: socialKeys.percentile(), queryFn: socialApi.percentile, meta: { silent: true } });
}
