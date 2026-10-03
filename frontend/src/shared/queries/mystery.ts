/** 이번 주 미스터리 지역(8단계) — 지도 탭(❓ 마커·카드)과 알림이 함께 읽는 서버 값. 체크인 뒤 보너스 받음이 이벤트로 늦게 반영된다. */
import { useQuery } from '@tanstack/react-query';
import { progressionApi } from '../../api/progression';
import { SETTLED_ROOT, settleInterval } from '../../store/syncStore';

export const mysteryKeys = {
  thisWeek: () => [SETTLED_ROOT, 'mystery'] as const,
};

/** GET /mystery/this-week */
export function useMysteryThisWeek() {
  return useQuery({ queryKey: mysteryKeys.thisWeek(), queryFn: progressionApi.mysteryThisWeek, refetchInterval: settleInterval });
}
