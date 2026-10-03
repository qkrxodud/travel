/** 진행(XP·레벨·칭호·스트릭·뱃지) — 헤더·퀘스트·랭킹·프로필이 함께 읽는 서버 값. */
import { useQuery } from '@tanstack/react-query';
import { progressionApi } from '../../api/progression';
import { SETTLED_ROOT, settleInterval } from '../../store/syncStore';

export const progressKeys = {
  progress: () => [SETTLED_ROOT, 'progress'] as const,
};

/** GET /progress — 서버 값, 이벤트로 비동기 반영 */
export function useProgress() {
  return useQuery({ queryKey: progressKeys.progress(), queryFn: progressionApi.progress, refetchInterval: settleInterval });
}
