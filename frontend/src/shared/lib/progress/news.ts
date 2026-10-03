/**
 * 진행 값이 바뀔 때 새로 생긴 게임 소식(보호권 사용 · 연속 탐험 마일스톤 · 시·도 정복)을 고른다 — 서버가 판정한 값의 변화만 본다.
 * 알림(토스트)과 정복 순간 지도 하이라이트가 쓴다.
 */
import type { FreezeUseResponse, MilestoneResponse, ProgressResponse, ProvinceProgressResponse } from '../../../api/types/progression';

export interface GameNews {
  /** 이번에 새로 쓴 보호권 기록(없으면 null) */
  freezeUsed: FreezeUseResponse | null;
  milestones: MilestoneResponse[];
  conquered: ProvinceProgressResponse[];
}

type GameProgress = Pick<ProgressResponse, 'streakFreeze' | 'milestones' | 'provinces'>;

export function gameNews(before: GameProgress, after: GameProgress): GameNews {
  const lastUsed = after.streakFreeze.lastUsed;
  const freezeUsed = lastUsed && lastUsed.at !== before.streakFreeze.lastUsed?.at ? lastUsed : null;
  const reachedBefore = new Set(before.milestones.filter(milestone => milestone.reached).map(milestone => milestone.months));
  const conqueredBefore = new Set(before.provinces.filter(province => province.conquered).map(province => province.code));
  return {
    freezeUsed,
    milestones: after.milestones.filter(milestone => milestone.reached && !reachedBefore.has(milestone.months)),
    conquered: after.provinces.filter(province => province.conquered && !conqueredBefore.has(province.code)),
  };
}

/** 정복 기록이 있는 시·도 코드(KR-11) — 지도 테두리·왕관 */
export function conqueredProvinceCodes(provinces: readonly ProvinceProgressResponse[] | undefined): ReadonlySet<string> {
  return new Set((provinces ?? []).filter(province => province.conquered).map(province => province.code));
}
