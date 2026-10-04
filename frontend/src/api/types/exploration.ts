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

/** 재방문 도장을 지금 못 받는 이유 — StampRefusal(9단계) */
export type RevisitRefusal = 'NOT_PAINTED' | 'SAME_YEAR' | 'ALREADY_STAMPED' | 'DAILY_CAP';

/** POST /revisits/{code} 201 — RevisitDtos.StampResponse (코드 KR-xxxxx) */
export interface StampResponse {
  regionCode: string;
  /** 카탈로그에서 못 찾으면 null */
  regionName: string | null;
  year: number;
  firstYear: number;
  stampedAt: string;
  /** 이 도장으로 받는 XP(진행 반영은 비동기) */
  xp: number;
  /** 이 도장을 포함한 내 도장 수 */
  stampCount: number;
}

/** GET /revisits 한 줄 — StampItem */
export interface StampItem {
  regionCode: string;
  regionName: string | null;
  provinceCode: string | null;
  year: number;
  stampedAt: string;
}

/** GET /revisits — StampBookResponse(최근 먼저) */
export interface StampBookResponse {
  count: number;
  xpPerStamp: number;
  stamps: StampItem[];
}

/** GET /revisits/{code} — StampStatusResponse("다시 다녀왔어요" 버튼 안내, POST 와 같은 판정) */
export interface StampStatusResponse {
  regionCode: string;
  /** 카탈로그에서 못 찾으면 null(폐지 지역 등) */
  regionName: string | null;
  /** 탐험가 단위로(어느 지도든) 칠한 지역인지 */
  painted: boolean;
  /** 처음 칠한 해(처리 시각 기준, 안 칠했으면 null) */
  firstYear: number | null;
  /** 지금 연도(서버 시각) */
  year: number;
  /** 이 지역 도장 연도(오름차순) */
  stampedYears: number[];
  canStamp: boolean;
  reason: RevisitRefusal | null;
  /** SAME_YEAR·ALREADY_STAMPED 일 때 받을 수 있게 되는 해 */
  availableFromYear: number | null;
  xp: number;
}

/** 가고 싶은 곳 핀 상태 */
export type WishStatus = 'WANTED' | 'VISITED';

/** WishlistDtos.WishItem (코드 KR-xxxxx) */
export interface WishItem {
  regionCode: string;
  /** 카탈로그에서 못 찾으면 null(폐지 지역 등) */
  regionName: string | null;
  provinceCode: string | null;
  status: WishStatus;
  pinnedAt: string;
  fulfilledAt: string | null;
}

/** GET /wishlist · PUT /wishlist/{code} — WishlistResponse(items 는 최근에 꽂은 순) */
export interface WishlistResponse {
  /** 아직 다녀오지 않은 핀 상한 */
  max: number;
  pendingCount: number;
  fulfilledCount: number;
  xpPerWish: number;
  items: WishItem[];
}
