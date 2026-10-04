/**
 * 계절 한정 테마(9단계) 표시 로직(순수 함수). 회차·기간·남은 초·진행·완성·보상은 서버 값(GET /seasons/current) — 여기서는 문구만 만든다.
 * 도감 탭 "계절 한정" 섹션, 지도 탭 배지, 친구 소식 회차 이름, 완성 알림이 쓴다.
 */
import type { NextSeasonResponse, SeasonRoundResponse, SeasonsResponse } from '../../../api/types/progression';

const MINUTE = 60;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;
/** 회차 기간은 서울 날짜로 정해진다(양 끝 포함, endsAt 은 마지막 날 다음 날 00:00) */
const SEOUL_OFFSET_MS = 9 * HOUR * 1000;

/** 남은 기간 — 이틀 넘게 남으면 날 수만, 그보다 적으면 시간·분, 다 지났으면 끝났다고 */
export function seasonRemainingText(seconds: number): string {
  const left = Math.max(0, Math.floor(seconds));
  if (left >= 2 * DAY) return `${Math.floor(left / DAY)}일 남음`;
  if (left >= HOUR) return `${Math.floor(left / HOUR)}시간 남음`;
  if (left >= MINUTE) return `${Math.floor(left / MINUTE)}분 남음`;
  if (left > 0) return '곧 끝나요';
  return '끝났어요';
}

/** 서울 날짜 "10월 1일" */
function seoulDate(epochMs: number): string {
  const seoul = new Date(epochMs + SEOUL_OFFSET_MS);
  return `${seoul.getUTCMonth() + 1}월 ${seoul.getUTCDate()}일`;
}

/** 회차 기간 "10월 1일 ~ 11월 30일"(endsAt 은 닫히는 순간이라 마지막 날은 그 전날) */
export function seasonPeriodText(round: Pick<SeasonRoundResponse, 'startsAt' | 'endsAt'>): string {
  return `${seoulDate(Date.parse(round.startsAt))} ~ ${seoulDate(Date.parse(round.endsAt) - 1)}`;
}

export interface SeasonRoundView {
  title: string;
  have: string;
  percent: number;
  remaining: string;
  period: string;
  reward: string;
  /** 완성 기록이 있는지(내가 보상을 받지 못했어도) */
  completed: boolean;
}

/** 회차 카드 문구 — 완성 보상(XP·칭호·배경)은 서버 값, 완성 뒤 합류해 보상이 없는 멤버는 그렇다고 알린다 */
export function seasonRoundView(round: SeasonRoundResponse): SeasonRoundView {
  const prize = `칭호 「${round.titleName}」 + ${round.xp} XP · ${round.year} 계절 배경`;
  let reward: string;
  if (round.completed && round.rewarded) reward = `완성 · 칭호 「${round.titleName}」 +${round.xp} XP · ${round.year} 계절 배경을 받았어요`;
  else if (round.completed) reward = '완성 · 완성한 뒤에 함께해 이 회차 보상은 없어요';
  else if (!round.open) reward = '기간이 끝나 이 회차 기록은 여기서 멈췄어요';
  else reward = `보상: ${prize}`;
  return {
    title: `${round.emoji} ${round.name}`,
    have: `${round.have} / ${round.total}`,
    percent: round.total ? Math.round(100 * round.have / round.total) : 0,
    remaining: round.open ? seasonRemainingText(round.remainingSeconds) : '끝났어요',
    period: seasonPeriodText(round),
    reward,
    completed: round.completed,
  };
}

/** 지금 열린 회차(없으면 null — 계절 기간이 아니면 지도 탭 배지를 숨긴다) */
export function openRound(seasons: SeasonsResponse | undefined): SeasonRoundResponse | null {
  return seasons?.current.find(round => round.open) ?? seasons?.current[0] ?? null;
}

/** 지도 탭 배지 문구 "🍁 2026 단풍 명소 3/10 · 58일 남음" */
export function seasonBadgeText(round: SeasonRoundResponse): string {
  return `${round.emoji} ${round.name} ${round.have}/${round.total} · ${round.completed ? '완성' : seasonRemainingText(round.remainingSeconds)}`;
}

/** 다음 회차 안내 "다음 회차 🌸 2027 벚꽃 명소 · 3월 20일부터" */
export function nextRoundText(next: NextSeasonResponse): string {
  return `다음 회차 ${next.emoji} ${next.name} · ${seoulDate(Date.parse(next.startsAt))}부터`;
}

/** 지난 회차 한 줄 "2025 단풍 명소 · 7/10 · 미완성" */
export function pastRoundText(round: SeasonRoundResponse): string {
  return `${round.emoji} ${round.name} · ${round.have}/${round.total} · ${round.completed ? '완성' : '미완성'}`;
}

/**
 * 회차 id → 표시 이름. 서버가 알려 준 회차(지금·다음·지난)에 있으면 그 이름, 없으면 같은 계절 회차 이름에서 연도만 바꾸고,
 * 그것도 없으면 id 그대로(친구가 내가 모르는 회차를 완성했을 때).
 */
export function seasonRoundName(roundId: string, seasons: SeasonsResponse | undefined): string {
  const known: { roundId: string; seasonId: string; name: string }[] = seasons ? [...seasons.current, ...seasons.history, ...(seasons.next ? [seasons.next] : [])] : [];
  const exact = known.find(round => round.roundId === roundId);
  if (exact) return exact.name;
  const [seasonId, year] = roundId.split('-');
  const sameSeason = known.find(round => round.seasonId === seasonId);
  if (sameSeason && year) return `${year} ${sameSeason.name.replace(/^\d{4}\s*/, '')}`;
  return roundId;
}

/** 이번에 새로 완성해 내가 보상을 받은 회차(알림) */
export function newlyCompletedRounds(before: SeasonsResponse, after: SeasonsResponse): SeasonRoundResponse[] {
  const doneBefore = new Set([...before.current, ...before.history].filter(round => round.completed).map(round => round.roundId));
  return [...after.current, ...after.history].filter(round => round.completed && round.rewarded && !doneBefore.has(round.roundId));
}
