/** 계절 한정 테마(9단계) — 도감 탭 "계절 한정" 섹션·지도 탭 배지·친구 소식 회차 이름·알림이 함께 읽는 서버 값(개인 지도 기준, 도감과 같다). */
import { useQuery } from '@tanstack/react-query';
import { useCallback } from 'react';
import { progressionApi } from '../../api/progression';
import { SETTLED_ROOT, settleInterval } from '../../store/syncStore';
import { seasonRoundName } from '../lib/progress/season';

export const seasonKeys = {
  current: () => [SETTLED_ROOT, 'seasons'] as const,
};

/** GET /seasons/current — 진행·완성은 체크인 뒤 이벤트로 늦게 반영된다 */
export function useSeasons() {
  return useQuery({ queryKey: seasonKeys.current(), queryFn: progressionApi.seasons, refetchInterval: settleInterval });
}

/** 회차 id → 표시 이름("2026 단풍 명소") — 친구 소식 문구 */
export function useSeasonNameLookup(): (roundId: string) => string {
  const { data } = useSeasons();
  return useCallback((roundId: string) => seasonRoundName(roundId, data), [data]);
}
