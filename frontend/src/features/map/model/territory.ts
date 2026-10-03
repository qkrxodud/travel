/**
 * 지도 탭 표시 로직(순수 함수). 정복률·시·도별 집계는 서버 값(GET /territory)을 쓰고, 여기서는 고르기·정렬·라벨만 한다.
 */
import { toClientCode } from '../../../api/client';
import type { MapDetailResponse, TerritoryResponse, VisitResponse } from '../../../api/types/exploration';
import type { SetResponse } from '../../../api/types/progression';
import { regionLabel, type Catalog, type RegionFeature } from '../../../shared/lib/region/catalog';
import type { MyVisit } from '../../../shared/lib/territory/visits';

/** 가장 최근에 체크인한 지역(캐릭터 위치) */
export function latestVisitCode(visits: ReadonlyMap<string, MyVisit>): string | null {
  let latest: MyVisit | null = null;
  for (const visit of visits.values()) if (!latest || (visit.at || 0) > (latest.at || 0)) latest = visit;
  return latest ? latest.code : null;
}

export interface ProvinceRow {
  /** 시·도 코드(KR-11) */
  code: string;
  name: string;
  visited: number;
  total: number;
  ratio: number;
  /** 👑 — 탐험가 단위 정복 기록(GET /progress.provinces, 취소해도 남는다) */
  crowned: boolean;
}

const NO_CROWNS: ReadonlySet<string> = new Set();

/**
 * 시·도별 정복률 줄(서버 값) — 정복률 높은 순, 같으면 방문 수 많은 순. 막대는 지금 보는 지도 기준(GET /territory),
 * 왕관은 탐험가 단위 정복 기록(crowns = 정복한 시·도 코드)이다.
 */
export function provinceRows(territory: TerritoryResponse | null, catalog: Catalog, crowns: ReadonlySet<string> = NO_CROWNS): ProvinceRow[] {
  const rows: ProvinceRow[] = territory
    ? territory.provinces.map(province => ({
      code: province.code, name: province.name, visited: province.visited, total: province.total,
      ratio: province.visited / province.total, crowned: crowns.has(province.code),
    }))
    : [...catalog.provinceByCode].map(([code, name]) => ({ code, name, visited: 0, total: catalog.provinceTotal.get(name) ?? 0, ratio: 0, crowned: crowns.has(code) }));
  return rows.sort((left, right) => right.ratio - left.ratio || right.visited - left.visited);
}

export interface LogEntry {
  key: string;
  date: string;
  label: string;
  memo: string;
  disputed: boolean;
}

const LOG_LIMIT = 40;

/** 탐험 일지(서버 순서 — 방문일 최근 순, 최대 40) */
export function logEntries(territory: TerritoryResponse | null, catalog: Catalog): LogEntry[] {
  if (!territory) return [];
  return territory.visits
    .map(visit => ({ visit, feature: catalog.byCode.get(toClientCode(visit.regionCode)) }))
    .filter((entry): entry is { visit: VisitResponse; feature: RegionFeature } => !!entry.feature)
    .slice(0, LOG_LIMIT)
    .map(({ visit, feature }) => ({
      key: `${visit.regionCode}:${visit.checkedInBy}`,
      date: visit.visitDate,
      label: regionLabel(feature),
      memo: visit.memo || '',
      disputed: visit.disputed,
    }));
}

/** 이 지역이 들어 있는 도감 세트(도감 서버 값의 지역 목록 기준) */
export function setsOfRegion(sets: readonly SetResponse[], code: string): SetResponse[] {
  return sets.filter(set => set.regions.some(region => toClientCode(region.code) === code));
}

export interface SetChip {
  id: string;
  name: string;
  have: number;
  total: number;
  done: boolean;
}

/** 세트 칩: 내 영토 기준 모은 수(extra = 지금 칠하려는 지역도 센다 — 체크인 모달) */
export function setChips(sets: readonly SetResponse[], code: string, mine: ReadonlySet<string>, extra?: string): SetChip[] {
  return setsOfRegion(sets, code).map(set => {
    const codes = set.regions.map(region => toClientCode(region.code));
    const have = codes.filter(regionCode => mine.has(regionCode) || regionCode === extra).length;
    return { id: set.id, name: set.name, have, total: codes.length, done: have === codes.length };
  });
}

/** 지도 툴팁 문구 */
export function regionTip(feature: RegionFeature, mine: boolean, sets: readonly SetResponse[]): string {
  const setNames = setsOfRegion(sets, feature.properties.code).map(set => set.name).join(', ');
  return `${regionLabel(feature)} · ${mine ? '내 영토' : '미탐험'}${feature.properties.tier === 'legend' ? ' · 전설' : ''}${setNames ? ' · 도감: ' + setNames : ''}`;
}

/** 공유 지도: 지역 → 선점자 explorerId / 선점자 색 */
export function claimMaps(territory: TerritoryResponse | null, detail: MapDetailResponse | null | undefined): { claimer: Map<string, string>; color: Map<string, string> } {
  const claimer = new Map<string, string>();
  const color = new Map<string, string>();
  if (!territory || !detail || territory.mapKind !== 'SHARED') return { claimer, color };
  const colorOf = new Map(detail.members.map(member => [member.explorerId, member.color]));
  for (const claim of territory.claims) {
    const memberColor = colorOf.get(claim.explorerId);
    if (!memberColor) continue;
    const code = toClientCode(claim.regionCode);
    claimer.set(code, claim.explorerId);
    color.set(code, memberColor);
  }
  return { claimer, color };
}

/** 오늘(로컬) YYYY-MM-DD — 체크인 모달 기본 날짜 */
export function localIsoDate(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

/**
 * 선택한 지역 카드의 "첫 방문 시 +N" 표시 여부 — 지금 지도에서 내가 그 시·도에 칠한 곳이 하나도 없으면 보인다(기존 화면과 같은 기준).
 * 보너스 값은 서버 reward-rules, 실제 지급 여부(탐험가당 1회·지도 무관)는 체크인 모달의 서버 preview(firstInProvince)가 정확한 값이다.
 */
export function showsProvinceFirstHint(catalog: Catalog, code: string, mine: Iterable<string>): boolean {
  const province = catalog.byCode.get(code)?.properties.prov;
  if (!province) return false;
  for (const visited of mine) if (catalog.byCode.get(visited)?.properties.prov === province) return false;
  return true;
}
