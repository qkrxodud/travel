/** 가고 싶은 곳(9단계, 비공개) — 지도 탭(상세 토글·핀 레이어·목록)과 다녀옴 축하 알림이 함께 읽는 서버 값. 다녀옴은 체크인 뒤 이벤트로 늦게 반영된다. */
import { useQuery } from '@tanstack/react-query';
import { explorationApi } from '../../api/exploration';
import { SETTLED_ROOT, settleInterval } from '../../store/syncStore';

export const wishlistKeys = {
  list: () => [SETTLED_ROOT, 'wishlist'] as const,
};

/** GET /wishlist */
export function useWishlist() {
  return useQuery({ queryKey: wishlistKeys.list(), queryFn: explorationApi.wishlist, refetchInterval: settleInterval });
}
