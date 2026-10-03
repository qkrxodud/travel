import type { ItemView } from './catalog';
import type { Rarity } from './common';

/** POST /explorers · GET /explorers/me — ExplorationDtos.ExplorerResponse */
export interface ExplorerResponse {
  explorerId: string;
  personalMapId: string;
  anonymous: boolean;
  createdAt: string;
  /** 발급 응답에만 실린다(그 밖에는 null) */
  accessToken: string | null;
  handle: string | null;
}

/** PUT /me/handle — HandleResponse */
export interface HandleResponse {
  explorerId: string;
  handle: string;
}

/** 방문 한 건 — VisitResponse (regionCode 는 서버 코드 KR-xxxxx) */
export interface VisitResponse {
  regionCode: string;
  regionName: string;
  provinceCode: string;
  provinceName: string;
  rarity: Rarity;
  visitDate: string;
  memo: string;
  photoUrl: string | null;
  checkedInBy: string;
  verification: string;
  visitedAt: string;
  claim: boolean;
  disputed: boolean;
  generation: number;
}

export type XpSource = 'REGION_BASE' | 'PROVINCE_FIRST' | 'FIRST_CLAIM' | 'MYSTERY_BONUS';

export interface XpLineResponse {
  source: XpSource;
  label: string;
  amount: number;
}

export interface XpResponse {
  lines: XpLineResponse[];
  total: number;
  basis: string;
  note: string;
}

/** GET /visits/preview — PreviewResponse */
export interface PreviewResponse {
  mapId: string;
  regionCode: string;
  regionName: string;
  provinceCode: string;
  provinceName: string;
  rarity: Rarity;
  alreadyVisited: boolean;
  nth: number;
  firstInProvince: boolean;
  firstClaim: boolean;
  xp: XpResponse;
  items: ItemView[];
}

/** POST /visits — CheckInResponse */
export interface CheckInResponse {
  mapId: string;
  visit: VisitResponse;
  nth: number;
  firstInProvince: boolean;
  firstClaim: boolean;
  xp: XpResponse;
  items: ItemView[];
}

export interface ConquestResponse {
  visited: number;
  total: number;
  percent: number;
}

export interface ProvinceConquestResponse {
  code: string;
  name: string;
  visited: number;
  total: number;
  percent: number;
  conquered: boolean;
}

export interface ClaimResponse {
  regionCode: string;
  explorerId: string;
}

export type MapKind = 'PERSONAL' | 'SHARED';

/** GET /territory — TerritoryResponse */
export interface TerritoryResponse {
  mapId: string;
  mapName: string;
  mapKind: MapKind;
  conquest: ConquestResponse;
  provinces: ProvinceConquestResponse[];
  visits: VisitResponse[];
  claims: ClaimResponse[];
}

export type MapVisibility = 'PUBLIC' | 'PRIVATE';
export type MemberRole = 'OWNER' | 'MEMBER';

/** MapDtos.SettingsResponse */
export interface MapSettings {
  photoRequired: boolean;
  dailyCheckInCap: number;
  visibility: MapVisibility;
}

/** GET /maps 한 줄 — MapSummaryResponse */
export interface MapSummaryResponse {
  mapId: string;
  name: string;
  kind: MapKind;
  role: MemberRole | null;
  memberCount: number;
  inviteCode: string;
  createdAt: string;
}

export interface MemberResponse {
  explorerId: string;
  role: MemberRole;
  joinedAt: string;
  color: string;
  me: boolean;
  regionCount: number;
  claimCount: number;
}

export interface DisputeResponse {
  regionCode: string;
  explorerId: string;
}

/** GET /maps/{id} · POST /maps · POST /maps/join · PUT /maps/{id}/settings — MapDetailResponse */
export interface MapDetailResponse {
  mapId: string;
  name: string;
  kind: MapKind;
  countryCode: string;
  ownerId: string;
  inviteCode: string;
  settings: MapSettings;
  members: MemberResponse[];
  claims: ClaimResponse[];
  disputed: DisputeResponse[];
  departing: number;
  rules: string[];
  rejoined: boolean;
}

/** POST /maps/{id}/leave — LeaveResponse */
export interface LeaveResponse {
  mapId: string;
  hiddenRegionCount: number;
  restoreUntil: string;
  message: string;
}

/** POST /maps/{id}/invite-code — InviteCodeResponse */
export interface InviteCodeResponse {
  mapId: string;
  inviteCode: string;
}
