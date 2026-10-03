/** ProgressionDtos */
export interface TitleRef {
  id: string;
  name: string;
}

export interface StreakResponse {
  months: number;
  lastMonth: string | null;
  activeThisMonth: boolean;
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
