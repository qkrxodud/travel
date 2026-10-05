import type { Rarity } from './common';

/** catalog ItemView.Look — 픽셀 페인터 룩(타입·주색·보조색) */
export interface ItemLook {
  type: string;
  primary: string;
  secondary: string;
}

/** 서버 장착 슬롯(EquipSlot). 화면은 BAG 을 "배낭"(back) 으로 그린다. */
export type ServerSlot = 'HAT' | 'HAND' | 'BADGE' | 'BAG' | 'PET' | 'BG' | 'PROP';

/** GET /catalog/items 한 줄 — catalog ItemView */
export interface ItemView {
  itemId: string;
  regionCode: string | null;
  name: string;
  emoji: string;
  slot: ServerSlot;
  tier: Rarity;
  theme: string | null;
  look: ItemLook | null;
  grantRule: string;
  grantRef: string | null;
  validFrom: string | null;
  validTo: string | null;
  /** 재방문 2회차 색 변형 룩(지역 특산물만, 그 외 null — 9단계) */
  variantLook: ItemLook | null;
}

/** GET /catalog/provinces 한 줄 — catalog ProvinceView */
export interface ProvinceView {
  code: string;
  name: string;
  fullName: string;
  displayOrder: number;
  regionCount: number;
}

/** GET /catalog/reward-rules — catalog RewardRulesView */
export interface RewardRulesView {
  xpByRarity: Record<Rarity, number>;
  provinceFirstBonus: number;
  setCompleteBonus: number;
  claimBonus: number;
  /** 이번 주 미스터리 보너스(8단계) */
  mysteryBonus: number;
  /** 시·도 정복 보너스(8단계) */
  provinceConquestBonus: number;
  /** 계절 한정 테마 완성 보너스(9단계) */
  seasonCompleteBonus: number;
  /** 재방문 도장 보너스(9단계) */
  revisitStampBonus: number;
  /** 가고 싶은 곳 다녀옴 보너스(9단계) */
  wishFulfilledBonus: number;
}

/** GET /catalog/regions.geojson 의 Feature.properties (서버 코드 KR-xxxxx) */
export interface RegionFeatureProperties {
  code: string;
  name: string;
  provinceCode: string;
  province: string;
  rarity: Rarity;
}

export interface RegionGeometry {
  type: 'Polygon' | 'MultiPolygon';
  coordinates: number[][][] | number[][][][];
}

export interface RegionFeatureCollection {
  type: 'FeatureCollection';
  features: { type: 'Feature'; properties: RegionFeatureProperties; geometry: RegionGeometry }[];
}

// ---- 13s단계 관리자 계절 회차 지역 목록(catalog AdminSeasonDtos, X-Admin-Token) — 계약 _workspace/13s_contracts.md §2 ----

/** 회차 지역 하나의 출처 — tourapi = 한국관광공사 TourAPI 근거, ai-estimate = AI 추정(검증 전) */
export type LineupRegionProvenance = 'tourapi' | 'ai-estimate';
/** 회차 지역 목록 전체의 출처 요약 */
export type LineupSummaryProvenance = LineupRegionProvenance | 'mixed';
/** 근거 종류 — FESTIVAL = 축제(기간 있음), ATTRACTION = 관광지(기간 null) */
export type LineupEvidenceKind = 'FESTIVAL' | 'ATTRACTION';
/** 지금 자동 수집이 돈다면 할 일 */
export type LineupNextPlan = 'NONE' | 'PREVIEW' | 'COLLECT' | 'COLLECT_AND_CONFIRM';
/** 확정한 쪽 — OPENING = 확정 없이 열려 그때 기본 목록으로 고정 */
export type LineupConfirmedBy = 'AUTO' | 'ADMIN' | 'OPENING';
/** 마지막 수집 시도 결과 */
export type LineupAttemptOutcome = 'COLLECTED' | 'PARTIAL' | 'NOT_CONFIGURED' | 'KEY_REJECTED' | 'QUOTA_EXCEEDED' | 'BAD_RESPONSE' | 'UNREACHABLE';

/** EvidenceResponse — 날짜는 서울 날짜 yyyy-MM-dd, fetchedAt 은 TourAPI 조회 시각(Instant) */
export interface LineupEvidenceResponse {
  /** TourAPI 콘텐츠 id */
  contentId: string;
  title: string;
  /** 축제 첫날(관광지는 null) */
  startDate: string | null;
  /** 축제 마지막 날(관광지는 null) */
  endDate: string | null;
  fetchedAt: string;
  evidenceKind: LineupEvidenceKind;
}

/** LineupRegionResponse — code 는 KR-xxxxx, 카탈로그에서 못 찾으면 name·provinceCode null */
export interface LineupRegionResponse {
  code: string;
  name: string | null;
  provinceCode: string | null;
  provenance: LineupRegionProvenance;
  evidence: LineupEvidenceResponse[];
}

/** LineupResponse — 이 회차에 쓰는(쓸) 목록. confirmedBy null = 아직 열리지 않은 회차의 기본 목록(AI 추정) */
export interface LineupResponse {
  provenance: LineupSummaryProvenance;
  source: string | null;
  confirmedBy: LineupConfirmedBy | null;
  confirmedAt: string | null;
  /** 근거 자료를 읽은 시각(기본 목록이면 null) */
  collectedAt: string | null;
  regions: LineupRegionResponse[];
}

/** CandidateResponse — 확정 전 후보 */
export interface LineupCandidateResponse {
  provenance: LineupSummaryProvenance;
  source: string | null;
  collectedAt: string;
  /** TourAPI 근거 지역 수 */
  evidencedRegions: number;
  regions: LineupRegionResponse[];
  warnings: string[];
}

/** AttemptResponse — 마지막 수집 시도 */
export interface LineupAttemptResponse {
  at: string;
  outcome: LineupAttemptOutcome;
  failed: boolean;
  warnings: string[];
}

/** RoundLineupResponse — GET /admin/seasons/{roundId} · POST …/refresh · PUT …/confirm */
export interface RoundLineupResponse {
  roundId: string;
  seasonId: string;
  name: string;
  emoji: string;
  year: number;
  startsAt: string;
  /** 닫히는 순간 — 회차 기간 [startsAt, endsAt) */
  endsAt: string;
  /** 열렸거나 지나 목록이 고정됨 — 갱신·확정 불가 */
  locked: boolean;
  /** 자동 수집·확정을 시작하는 시각(시작 leadDays 일 전) */
  collectionOpensAt: string;
  nextPlan: LineupNextPlan;
  inEffect: LineupResponse;
  candidate: LineupCandidateResponse | null;
  lastAttempt: LineupAttemptResponse | null;
  warnings: string[];
}

/** TourApiStatusResponse — 키 값은 응답에 없다(configured 만) */
export interface TourApiStatusResponse {
  configured: boolean;
  /** 오늘(서울 날짜) 이 서비스의 호출 수 */
  callsToday: number;
  dailyLimit: number;
  exhausted: boolean;
  source: string;
  warnings: string[];
}

/** ScheduleResponse — 자동 수집 정책 */
export interface LineupScheduleResponse {
  leadDays: number;
  recollectAfterHours: number;
  autoConfirm: boolean;
  autoConfirmMinRegions: number;
}

/** GET /admin/seasons — SeasonLineupsResponse. rounds = 지금 열린 회차(있으면) → 계절마다 다음 회차 */
export interface SeasonLineupsResponse {
  tourApi: TourApiStatusResponse;
  schedule: LineupScheduleResponse;
  rounds: RoundLineupResponse[];
}
