/**
 * 재방문 도장(9단계) 표시 로직(순수 함수). 받을 수 있는지·이유·처음 칠한 해·도장 연도·XP 는 서버 판정(GET /revisits/{code}) — 여기서는 문구만.
 */
import type { ErrorCode } from '../../../api/types/common';
import type { StampResponse, StampStatusResponse } from '../../../api/types/exploration';

export interface RevisitView {
  canStamp: boolean;
  label: string;
  /** 지금 못 받는 이유(받을 수 있으면 null) */
  why: string | null;
  /** 처음 칠한 해 안내 */
  since: string;
  /** 받은 도장 연도(오름차순) */
  years: number[];
}

/** 지금 못 받는 이유 안내 — 서버가 알려 준 이유·해만 쓴다 */
export function revisitRefusalText(status: StampStatusResponse): string | null {
  switch (status.reason) {
    case null:
      return null;
    case 'NOT_PAINTED':
      return '칠한 곳에서만 재방문 도장을 받을 수 있어요';
    case 'SAME_YEAR':
      return `${status.firstYear ?? status.year}년에 처음 칠한 곳이에요 — ${status.availableFromYear ?? status.year + 1}년부터 도장을 받을 수 있어요`;
    case 'ALREADY_STAMPED':
      return `${status.year}년 도장은 이미 받았어요 — ${status.availableFromYear ?? status.year + 1}년에 다시 받을 수 있어요`;
    case 'DAILY_CAP':
      return '오늘은 칠하기와 도장을 합쳐 하루 상한에 닿았어요 — 내일 다시 받을 수 있어요';
  }
}

/** 지역 상세의 "다시 다녀왔어요" — 탐험가 단위로 칠하지 않은 곳이면 보이지 않는다(null) */
export function revisitView(status: StampStatusResponse | undefined): RevisitView | null {
  if (!status?.painted) return null;
  return {
    canStamp: status.canStamp,
    label: `다시 다녀왔어요 (+${status.xp} XP)`,
    why: status.canStamp ? null : revisitRefusalText(status),
    since: status.firstYear !== null ? `${status.firstYear}년에 처음 칠함` : '',
    years: status.stampedYears,
  };
}

/** 도장을 받았을 때 알림 */
export function stampedToast(stamp: StampResponse): { title: string; sub: string } {
  return { title: `재방문 도장 · ${stamp.year}`, sub: `${stamp.regionName ?? stamp.regionCode.replace(/^KR-/, '')}에 다시 다녀왔어요 · +${stamp.xp} XP · 도장 ${stamp.stampCount}개째` };
}

/** 도장을 못 받았을 때(누른 사이 판정이 바뀐 경우) 오류 코드별 안내 — 제목은 공용 오류 제목, 본문은 여기서 */
export function stampErrorText(code: ErrorCode | undefined, regionName: string): string | null {
  switch (code) {
    case 'REVISIT_NOT_PAINTED':
      return `${regionName} — 아직 칠하지 않은 곳이라 도장을 받을 수 없어요`;
    case 'REVISIT_SAME_YEAR':
      return '처음 칠한 해에는 도장을 받을 수 없어요 — 다음 해부터 받을 수 있어요';
    case 'REVISIT_ALREADY_STAMPED':
      return `올해 ${regionName} 도장은 이미 받았어요 — 내년에 다시 받을 수 있어요`;
    case 'DAILY_CAP_EXCEEDED':
      return '오늘은 칠하기와 도장을 합쳐 하루 상한에 닿았어요 — 내일 다시 눌러 주세요';
    default:
      return null;
  }
}
