import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { progressionApi } from '../../api/progression';
import { SETTLED_ROOT, settleAfterChange, settleInterval } from '../../store/syncStore';
import { useUiStore } from '../../store/uiStore';

export const questKeys = {
  quests: () => [SETTLED_ROOT, 'quests'] as const,
};

/** GET /quests — 이번 달 월간 퀘스트 + 상시 도전(서버 값) */
export function useQuests() {
  return useQuery({ queryKey: questKeys.quests(), queryFn: progressionApi.quests, refetchInterval: settleInterval });
}

/** 보상 받기(1회) — XP 는 이벤트로 비동기 반영된다. */
export function useClaimQuest() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (questId: string) => progressionApi.claim(questId),
    onSuccess: () => settleAfterChange(queryClient, !useUiStore.getState().sampleMode),
  });
}

