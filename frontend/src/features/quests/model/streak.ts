/**
 * 퀘스트 탭 표시 로직 — 스트릭(서버: lastMonth 에서 끝나는 n개월)을 최근 12개월 띠에, 연속 탐험 마일스톤을 진행 막대로 보여 준다.
 * 연속 개월·보호권·마일스톤 달성·남은 개월은 서버 값이다. 여기서는 띠 칸·문구·막대 폭만 만든다.
 */
import type { MilestoneResponse, StreakFreezeResponse, StreakResponse } from '../../../api/types/progression';

const pad = (value: number) => String(value).padStart(2, '0');

/** today 기준 n개월 뒤(음수면 앞) YYYY-MM */
export function monthShift(today: Date, months: number): string {
  const shifted = new Date(today.getFullYear(), today.getMonth() + months, 1);
  return `${shifted.getFullYear()}-${pad(shifted.getMonth() + 1)}`;
}

/** YYYY-MM 에서 n개월 뒤(음수면 앞) */
function shiftMonth(month: string, months: number): string {
  const [year, monthNumber] = month.split('-').map(Number);
  return monthShift(new Date(year, monthNumber - 1, 1), months);
}

/** 연속 구간에 든 달(YYYY-MM) — 보호권으로 메운 달(서버 frozenMonths)은 건너뛴다(연속 개월에 세지 않는다) */
export function streakRun(streak: StreakResponse): Set<string> {
  const frozen = new Set(streak.frozenMonths);
  const run = new Set<string>();
  if (!streak.lastMonth || streak.months <= 0) return run;
  let month = streak.lastMonth;
  while (run.size < streak.months) {
    if (!frozen.has(month)) run.add(month);
    month = shiftMonth(month, -1);
  }
  return run;
}

export interface StreakCell {
  month: string;
  label: string;
  on: boolean;
  /** 보호권으로 메운 달 */
  frozen: boolean;
  now: boolean;
}

/** 최근 12개월 띠(오래된 달부터) — 이번 달은 서버가 알려 준 달(currentMonth), 🧊 달은 서버가 알려 준 보호권으로 메운 달이다 */
export function streakStrip(streak: StreakResponse, currentMonth: string): StreakCell[] {
  const frozen = new Set(streak.frozenMonths);
  const run = streakRun(streak);
  return Array.from({ length: 12 }, (_unused, i) => {
    const month = shiftMonth(currentMonth, i - 11);
    return { month, label: `${month.slice(5)}월`, on: run.has(month), frozen: frozen.has(month), now: month === currentMonth };
  });
}

/** 스트릭 안내 문구 — 이번 달에 칠할 때 보호권을 쓰게 되는지(freezesNeeded·보유 수)까지 알려 준다 */
export function streakText(streak: StreakResponse, freeze?: StreakFreezeResponse): string {
  const months = streak.months;
  const needed = streak.freezesNeeded;
  if (needed > 0 && freeze && needed > freeze.held) {
    return `보호권이 ${needed - freeze.held}개 모자라요 — 이번 달에 칠하면 1개월부터 다시 시작돼요.`;
  }
  if (months === 0) return '이번 달 새 지역 1곳만 칠하면 스트릭이 시작돼요.';
  if (needed > 0) return `${months}개월째. 빈 달이 ${needed}개월 — 이번 달에 1곳 칠하면 보호권 ${needed}개로 이어져요.`;
  return streak.activeThisMonth
    ? `${months}개월 연속 탐험 중. 다음 달에도 1곳이면 유지됩니다.`
    : `${months}개월째. 이번 달 아직 0곳 — 이달 안에 1곳 칠해야 끊기지 않아요.`;
}

/** 진행 막대 폭(%) */
export const percentOf = (current: number, target: number): number => Math.round(100 * current / target);

export interface MilestoneRow {
  months: number;
  label: string;
  /** 진행 막대 폭(%) — 서버의 남은 개월로 */
  percent: number;
  reached: boolean;
  /** "+50 XP · 칭호 「꾸준한 탐험가」 · 보호권 +1" */
  reward: string;
  /** "✓ 달성" 또는 "2개월 남음" */
  status: string;
}

/** 연속 탐험 마일스톤 줄(서버 순서 — 짧은 것부터) */
export function milestoneRows(milestones: readonly MilestoneResponse[]): MilestoneRow[] {
  return milestones.map(milestone => ({
    months: milestone.months,
    label: `${milestone.months}개월`,
    percent: milestone.reached ? 100 : percentOf(Math.max(0, milestone.months - milestone.remainingMonths), milestone.months),
    reached: milestone.reached,
    reward: `+${milestone.xp} XP · 칭호 「${milestone.titleName}」${milestone.freezes ? ` · 보호권 +${milestone.freezes}` : ''}`,
    status: milestone.reached ? '✓ 달성' : `${milestone.remainingMonths}개월 남음`,
  }));
}

/** 다음 마일스톤 안내 */
export function nextMilestoneText(next: MilestoneResponse | null): string {
  return next
    ? `다음 마일스톤 ${next.months}개월까지 ${next.remainingMonths}개월 남았어요.`
    : '연속 탐험 마일스톤을 모두 달성했어요.';
}
