/**
 * 스트릭 보호권 표시(헤더 🧊×n · 퀘스트 탭 진행 줄). 보유 수·최대 수·이번 달 몫(받은 퀘스트 수·필요한 수·주는 수·채움·실제 지급)은
 * 모두 서버 값(streakFreeze) — 여기서는 문구만 만든다.
 */
import type { StreakFreezeResponse } from '../../../api/types/progression';

export interface FreezeBadge {
  /** 🧊×n */
  text: string;
  /** 🧊 — 이모지 글꼴로 따로 그린다 */
  icon: string;
  /** ×n */
  count: string;
  /** 하나도 없으면 흐리게 */
  empty: boolean;
  /** 얻는 방법 안내 */
  hint: string;
}

const FREEZE_ICON = '🧊';

/** 헤더 스트릭 옆 보호권 표시 */
export function freezeBadge(freeze: StreakFreezeResponse | undefined): FreezeBadge {
  const held = freeze?.held ?? 0;
  const hint = freeze
    ? `스트릭 보호권 — 월간 퀘스트 보상 ${freeze.thisMonth.questsRequired}개를 모두 받으면 ${freeze.thisMonth.reward}개(최대 ${freeze.max}개)`
    : '스트릭 보호권';
  return { text: `${FREEZE_ICON}×${held}`, icon: FREEZE_ICON, count: `×${held}`, empty: held === 0, hint };
}

/** 퀘스트 탭 보호권 진행 줄: "이번 달 보상 n/4 받음" + 이번 달 몫의 상태(서버 판정) */
export function freezeProgressText(freeze: StreakFreezeResponse): string {
  const month = freeze.thisMonth;
  const head = `이번 달 보상 ${month.questsRewarded}/${month.questsRequired} 받음`;
  if (month.earned) {
    return month.granted > 0
      ? `${head} — 이번 달 보호권 ${month.granted}개를 받았어요`
      : `${head} — 이번 달 몫을 채웠지만 보호권이 가득이라 더 받지 않았어요`;
  }
  if (freeze.held >= freeze.max) return `${head} — 보호권을 최대(${freeze.max}개) 갖고 있어요`;
  return `${head} — 모두 받으면 보호권 ${month.reward}개`;
}
