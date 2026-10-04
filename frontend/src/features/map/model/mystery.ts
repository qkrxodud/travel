/**
 * 이번 주 미스터리 지역 카드 표시 로직(순수 함수). 지역·남은 초·보너스·받음 여부는 서버 값(GET /mystery/this-week) — 여기서는 문구만.
 */
import type { MysteryWeekResponse } from '../../../api/types/progression';

const MINUTE = 60;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

/** 남은 기간 "n일 n시간 남음" — 하루가 안 남으면 시간·분, 한 시간이 안 남으면 분 */
export function remainingText(seconds: number): string {
  const left = Math.max(0, Math.floor(seconds));
  if (left >= DAY) return `${Math.floor(left / DAY)}일 ${Math.floor((left % DAY) / HOUR)}시간 남음`;
  if (left >= HOUR) return `${Math.floor(left / HOUR)}시간 ${Math.floor((left % HOUR) / MINUTE)}분 남음`;
  if (left >= MINUTE) return `${Math.floor(left / MINUTE)}분 남음`;
  return '곧 다음 주 지역으로 바뀌어요';
}

export interface MysteryCardView {
  /** 지역 이름은 눌러서 지도에서 찾은 뒤에 보인다 */
  title: string;
  hint: string;
  remaining: string;
  bonus: string;
  received: boolean;
  /** 지역 이름을 보여 주는 중 */
  revealed: boolean;
}

const RARITY_LABEL = { RARE: '희귀', LEGEND: '전설' } as const;

/**
 * revealedWeek = 카드·마커를 눌러 지도에서 찾은 주(weekStart). 그 주가 이번 주일 때만 이름을 보여 준다 — 탭을 연 채 주가 넘어가면 새 지역은 다시 숨는다.
 * 서버가 공개해도 된다고 하면(이번 주 보너스를 받음) 누르지 않아도 이름을 보여 준다.
 */
export function mysteryCard(week: MysteryWeekResponse, revealedWeek: string | null): MysteryCardView {
  const revealed = revealedWeek === week.weekStart || week.revealed;
  const rarity = RARITY_LABEL[week.region.rarity];
  return {
    title: revealed ? `${week.region.provinceName} ${week.region.name}` : `어딘가의 ${rarity} 지역`,
    hint: week.received
      ? `이번 주 보너스를 받았어요 · 지금까지 ${week.foundCount}주 찾음`
      : revealed ? `${rarity} 지역 · 이번 주 안에 칠하면 보너스` : '❓ 마커나 이 카드를 누르면 지도에서 보여 줘요',
    remaining: remainingText(week.remainingSeconds),
    bonus: `+${week.bonusXp} XP`,
    received: week.received,
    revealed,
  };
}
