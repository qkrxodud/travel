/** 퀘스트 탭 표시 로직 — 스트릭(서버: lastMonth 에서 끝나는 n개월)을 최근 12개월 띠에 표시한다. */
import type { StreakResponse } from '../../../api/types/progression';

const pad = (value: number) => String(value).padStart(2, '0');

/** today 기준 n개월 뒤(음수면 앞) YYYY-MM */
export function monthShift(today: Date, months: number): string {
  const shifted = new Date(today.getFullYear(), today.getMonth() + months, 1);
  return `${shifted.getFullYear()}-${pad(shifted.getMonth() + 1)}`;
}

/** 연속 구간에 든 달(YYYY-MM) */
export function streakRun(streak: StreakResponse): Set<string> {
  const run = new Set<string>();
  if (!streak.lastMonth || streak.months <= 0) return run;
  const [year, month] = streak.lastMonth.split('-').map(Number);
  for (let i = 0; i < streak.months; i++) {
    const at = new Date(year, month - 1 - i, 1);
    run.add(`${at.getFullYear()}-${pad(at.getMonth() + 1)}`);
  }
  return run;
}

export interface StreakCell {
  month: string;
  label: string;
  on: boolean;
  now: boolean;
}

/** 최근 12개월 띠(오래된 달부터) */
export function streakStrip(streak: StreakResponse, today: Date, currentMonth: string): StreakCell[] {
  const run = streakRun(streak);
  return Array.from({ length: 12 }, (_unused, i) => {
    const month = monthShift(today, i - 11);
    return { month, label: `${month.slice(5)}월`, on: run.has(month), now: month === currentMonth };
  });
}

/** 스트릭 안내 문구 */
export function streakText(streak: StreakResponse): string {
  const months = streak.months;
  if (months === 0) return '이번 달 새 지역 1곳만 칠하면 스트릭이 시작돼요.';
  return streak.activeThisMonth
    ? `${months}개월 연속 탐험 중. 다음 달에도 1곳이면 유지됩니다.`
    : `${months}개월째. 이번 달 아직 0곳 — 이달 안에 1곳 칠해야 끊기지 않아요.`;
}

/** 진행 막대 폭(%) */
export const percentOf = (current: number, target: number): number => Math.round(100 * current / target);
