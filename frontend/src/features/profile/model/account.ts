/** 계정 표시 로직 */
import type { MergeNotice } from '../../../api/types/account';

/** 로그인·병합 안내 문구 */
export function noticeText(notice: MergeNotice | null | undefined): string | null {
  if (!notice) return null;
  if (notice.outcome === 'MERGED') {
    const moved = notice.merge ? notice.merge.movedRegions : 0;
    const added = notice.merge?.newRegions ? ` (새 지역 ${notice.merge.newRegions}곳)` : '';
    return `익명 기록 ${moved}곳을 계정으로 옮겼어요${added}`;
  }
  if (notice.outcome === 'LINKED') return '지금까지의 영토를 계정에 연결했어요';
  if (notice.outcome === 'CREATED') return '새 계정 탐험가를 만들었어요';
  return null;
}
