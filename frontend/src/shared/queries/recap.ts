/**
 * 연간 리캡(GET /me/recap) — 서버 값(sharing YearRecap, 리캡 카드 PNG 와 같은 계산)을 그대로 쓴다. 화면은 문장만 만든다.
 * 키가 SETTLED_ROOT 아래라 체크인·취소·보상 받기 뒤 반영 대기 창에서 함께 다시 읽는다(완성한 세트는 이벤트로 늦게 반영된다).
 * 방문 기록 수정(날짜가 바뀌면 달·연도가 바뀐다)은 지도 기능이 recapKeys.all() 로 무효화한다.
 */
import { useQuery } from '@tanstack/react-query';
import { sharingApi } from '../../api/sharing';
import { SETTLED_ROOT, settleInterval } from '../../store/syncStore';

export const recapKeys = {
  all: () => [SETTLED_ROOT, 'recap'] as const,
  recap: (year: number, mapId: string | null) => [SETTLED_ROOT, 'recap', year, mapId] as const,
};

/** 지금 보는 지도(null = 개인 지도)에서 내가 칠한 곳의 year 년 리캡 */
export function useRecap(year: number, mapId: string | null) {
  return useQuery({
    queryKey: recapKeys.recap(year, mapId),
    queryFn: () => sharingApi.recap(year, mapId),
    refetchInterval: settleInterval,
    meta: { silent: true },
  });
}
