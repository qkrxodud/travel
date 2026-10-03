/**
 * 내 방문(지금 보는 지도에서 내가 칠한 곳)과 공유 지도 멤버 표시 — 지도·가방·공유 지도·프로필이 함께 쓰는 순수 함수.
 */
import { toClientCode } from '../../../api/client';
import type { MapDetailResponse, MemberResponse, TerritoryResponse, VisitResponse } from '../../../api/types/exploration';

/** 내 방문 한 건(화면 코드) */
export interface MyVisit {
  code: string;
  date: string;
  memo: string;
  /** 처리 시각(ms) — 캐릭터는 가장 최근 체크인 지역에 선다 */
  at: number;
}

/** 지도에서 "나"인 멤버(공유 지도). 개인 지도면 null. */
export function meIn(detail: MapDetailResponse | null | undefined): MemberResponse | null {
  return detail?.members.find(member => member.me) ?? null;
}

/**
 * 내 영토 = 이 지도에서 내가 칠한 곳. 공유 지도에는 다른 멤버의 방문도 함께 오므로 내 것만 고른다
 * (공유 지도인데 멤버 정보가 아직 없으면 고를 수 없으니 null — 화면은 준비 전으로 본다).
 */
export function myVisits(territory: TerritoryResponse, detail: MapDetailResponse | null | undefined): Map<string, MyVisit> | null {
  let mine: VisitResponse[] = territory.visits;
  if (territory.mapKind === 'SHARED') {
    const me = meIn(detail);
    if (!me) return null;
    mine = territory.visits.filter(visit => visit.checkedInBy === me.explorerId);
  }
  // 지역 코드 순(프로토타입은 코드를 키로 한 객체라 숫자 키 오름차순으로 돌았다 — 리캡의 동점 순서가 그 순서를 따른다)
  const ordered = mine.map(visit => ({
    code: toClientCode(visit.regionCode), date: visit.visitDate, memo: visit.memo || '', at: Date.parse(visit.visitedAt),
  })).sort((left, right) => left.code.localeCompare(right.code));
  return new Map(ordered.map(entry => [entry.code, entry]));
}

/** 멤버 표시 이름(공유 지도 — 나 / 멤버 앞 4자리) */
export function memberName(member: Pick<MemberResponse, 'me' | 'explorerId'>): string {
  return member.me ? '나' : '멤버 ' + member.explorerId.slice(0, 4);
}
