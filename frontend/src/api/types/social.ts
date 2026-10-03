import type { Rarity } from './common';

/** SocialDtos.FriendResponse */
export interface FriendResponse {
  handle: string | null;
  following: boolean;
  follower: boolean;
  mutual: boolean;
}

/** GET /friends — FriendsResponse; POST /friends/{handle} 은 FriendResponse */
export interface FriendsResponse {
  handle: string | null;
  loggedIn: boolean;
  mutualCount: number;
  people: FriendResponse[];
}

export type FeedKind = 'VISIT' | 'THEME_COMPLETED' | 'LEVEL_UP' | 'BADGE_EARNED' | 'STREAK_MILESTONE' | 'PROVINCE_CONQUERED' | 'MYSTERY_FOUND';

export interface FeedItemResponse {
  handle: string | null;
  kind: FeedKind;
  regionCode: string | null;
  rarity: Rarity | null;
  themeId: string | null;
  level: number | null;
  badgeId: string | null;
  daysAgo: number;
  when: string;
  /** PROVINCE_CONQUERED(KR-11) */
  provinceCode: string | null;
  /** STREAK_MILESTONE */
  months: number | null;
  /** MYSTERY_FOUND(yyyy-MM-dd) */
  weekStart: string | null;
}

/** GET /feed — FeedResponse */
export interface FeedResponse {
  loggedIn: boolean;
  followingCount: number;
  items: FeedItemResponse[];
}

export interface FriendRankRow {
  explorerId: string;
  handle: string | null;
  me: boolean;
  rank: number;
  regionCount: number;
  level: number;
  titleName: string | null;
}

export interface BaselineResponse {
  provinceCode: string | null;
  nationwide: boolean;
  explorerCount: number;
  averageRegionCount: number;
  computedAt: string | null;
}

/** GET /rankings/friends — FriendRankingResponse */
export interface FriendRankingResponse {
  loggedIn: boolean;
  friendCount: number;
  mainProvince: string | null;
  rows: FriendRankRow[];
  baseline: BaselineResponse | null;
}

export interface MapRankRow {
  explorerId: string;
  handle: string | null;
  me: boolean;
  rank: number;
  territories: number;
  claims: number;
  legends: number;
}

/** GET /rankings/maps/{mapId} — MapRankingResponse */
export interface MapRankingResponse {
  mapId: string;
  disputedExcluded: number;
  rows: MapRankRow[];
}

/** GET /rankings/me/percentile — PercentileResponse */
export interface PercentileResponse {
  computed: boolean;
  topPercent: number | null;
  rank: number | null;
  population: number | null;
  regionCount: number | null;
  computedAt: string | null;
}

export interface CompareSide {
  handle: string | null;
  regionCount: number;
}

/** GET /compare/{handle} — CompareResponse (지역 코드 KR-xxxxx) */
export interface CompareResponse {
  me: CompareSide;
  other: CompareSide;
  mutual: boolean;
  onlyMine: string[];
  both: string[];
  onlyTheirs: string[];
  lead: number;
}
