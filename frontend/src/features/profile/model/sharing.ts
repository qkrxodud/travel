/** 공유 표시 로직 — 공개 범위 선택지·안내 문구 */
import type { ProfileVisibility } from '../../../api/types/sharing';

export const VISIBILITY_OPTIONS: readonly (readonly [ProfileVisibility, string])[] = [['PRIVATE', '비공개'], ['FRIENDS', '친구 공개'], ['PUBLIC', '전체 공개']];

export function privacyNote(visibility: ProfileVisibility): string {
  if (visibility === 'FRIENDS') return '친구 공개 — 서로 팔로우한 친구에게만 공개 프로필·카드가 열려요. 그 밖의 사람에겐 비공개처럼 보이지 않아요.';
  if (visibility === 'PRIVATE') return '비공개(기본) — 공개 프로필·카드 링크가 열리지 않아요(존재 여부도 숨겨요). "공개하기"를 켜면 열려요.';
  return '전체 공개 — 공개 프로필엔 색칠·집계만 보여요. 메모·사진은 비공개, 날짜는 월 단위로만.';
}
