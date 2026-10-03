/** ProgressionDtos */
export interface TitleRef {
  id: string;
  name: string;
}

export interface StreakResponse {
  /** 이번 달 기준 연속 개월(이번 달에 칠하면 가진 보호권으로 빈 달을 메울 수 있으면 유지) */
  months: number;
  lastMonth: string | null;
  activeThisMonth: boolean;
  /** 이번 달에 칠하면 쓰게 될 보호권 수(빈 달 수). 가진 것보다 많으면 칠할 때 끊긴다(8단계) */
  freezesNeeded: number;
  /** 지금 연속 구간 안에서 보호권으로 메운 달 전부(yyyy-MM, 오래된 순) — 스트릭 띠의 🧊 달 */
  frozenMonths: string[];
}

/** 가장 최근 보호권 사용 — FreezeUseResponse */
export interface FreezeUseResponse {
  /** 연속을 이은 달(yyyy-MM) */
  month: string;
  count: number;
  at: string;
}

/** 이번 달 월간 퀘스트로 받는 보호권 진행 — MonthlyFreezeResponse(서버 판정) */
export interface MonthlyFreezeResponse {
  month: string;
  /** 보상 받은 월간 퀘스트 수 */
  questsRewarded: number;
  /** 모두 받아야 하는 월간 퀘스트 수 */
  questsRequired: number;
  /** 모두 받으면 주는 보호권 수 */
  reward: number;
  /** 이번 달 몫을 채웠는지(보유가 가득이라 0개였어도 true) */
  earned: boolean;
  /** 이번 달 몫으로 실제로 늘어난 수(가득이면 0) */
  granted: number;
}

/** 보호권 — StreakFreezeResponse(8단계) */
export interface StreakFreezeResponse {
  held: number;
  max: number;
  lastUsed: FreezeUseResponse | null;
  thisMonth: MonthlyFreezeResponse;
}

/** 연속 탐험 마일스톤 — MilestoneResponse(8단계) */
export interface MilestoneResponse {
  months: number;
  xp: number;
  freezes: number;
  titleId: string;
  titleName: string;
  reached: boolean;
  reachedAt: string | null;
  /** 지금 연속에서 더 필요한 개월(받았으면 0) */
  remainingMonths: number;
}

/** 탐험가 단위 시·도 칠하기·정복 — ProvinceProgressResponse(8단계, 코드 KR-11) */
export interface ProvinceProgressResponse {
  code: string;
  name: string;
  visited: number;
  total: number;
  percent: number;
  complete: boolean;
  /** 정복 기록(왕관 — 취소해도 남는다) */
  conquered: boolean;
  conqueredAt: string | null;
}

export interface BadgeResponse {
  id: string;
  ico: string;
  name: string;
  desc: string;
  earned: boolean;
  earnedAt: string | null;
}

export interface TitleResponse {
  id: string;
  name: string;
  how: string;
  source: string;
  earned: boolean;
  selected: boolean;
}

export interface XpEntryResponse {
  source: string;
  amount: number;
  refId: string;
  at: string;
}

/** GET /progress · PUT /progress/title — ProgressResponse */
export interface ProgressResponse {
  explorerId: string;
  xp: number;
  level: number;
  levelTitle: string;
  currentLevelXp: number;
  nextLevelXp: number;
  title: TitleRef | null;
  selectedTitleId: string | null;
  streak: StreakResponse;
  badgeCount: number;
  badges: BadgeResponse[];
  titles: TitleResponse[];
  recentXp: XpEntryResponse[];
  streakFreeze: StreakFreezeResponse;
  nextMilestone: MilestoneResponse | null;
  milestones: MilestoneResponse[];
  provinces: ProvinceProgressResponse[];
  mysteryFoundCount: number;
}

export type MysteryRarity = 'RARE' | 'LEGEND';

/** 이번 주 미스터리 지역 — MysteryRegionResponse(코드 KR-xxxxx) */
export interface MysteryRegionResponse {
  code: string;
  name: string;
  provinceCode: string;
  provinceName: string;
  rarity: MysteryRarity;
}

/** GET /mystery/this-week — MysteryWeekResponse(8단계) */
export interface MysteryWeekResponse {
  weekStart: string;
  startsAt: string;
  endsAt: string;
  remainingSeconds: number;
  region: MysteryRegionResponse;
  bonusXp: number;
  received: boolean;
  receivedAt: string | null;
  foundCount: number;
  /** 지역 이름을 바로 공개해도 되는지(이번 주 보너스를 받았으면 true) */
  revealed: boolean;
}

export interface SetRegionResponse {
  code: string;
  name: string;
  collected: boolean;
}

export interface SetResponse {
  id: string;
  name: string;
  desc: string;
  title: string;
  have: number;
  total: number;
  completed: boolean;
  completedAt: string | null;
  rewardXp: number;
  regions: SetRegionResponse[];
}

/** GET /collection — CollectionResponse */
export interface CollectionResponse {
  mapId: string;
  completed: number;
  total: number;
  sets: SetResponse[];
}

export type QuestScope = 'MONTHLY' | 'ALWAYS';

export interface QuestResponse {
  id: string;
  scope: QuestScope;
  ico: string;
  name: string;
  desc: string;
  current: number;
  target: number;
  xp: number;
  title: string | null;
  achieved: boolean;
  claimed: boolean;
  claimedAt: string | null;
  claimable: boolean;
}

/** GET /quests — QuestsResponse */
export interface QuestsResponse {
  month: string;
  monthlyDone: number;
  monthly: QuestResponse[];
  always: QuestResponse[];
}

/** POST /quests/{id}/claim — ClaimResponse */
export interface QuestClaimResponse {
  questId: string;
  period: string;
  xp: number;
  claimedAt: string;
}
