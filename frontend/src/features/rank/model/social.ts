/** 랭킹 탭 표시 로직(순수 함수) — 순위·지역 수·비교 결과는 서버 값, 여기서는 이름·색·문구만. */
import { toClientCode } from '../../../api/client';
import type { CompareResponse, FeedItemResponse, FriendResponse } from '../../../api/types/social';
import { labelOfCode, type Catalog } from '../../../shared/lib/region/catalog';

const AVATAR_COLORS = ['#d9541b', '#3b6fd6', '#8b4bd6', '#c2187a', '#5d6b3a', '#0b6b5f', '#b5651d'];

/** handle 로 고정된 아바타 색 */
export function avatarColor(key: string | null | undefined): string {
  let hash = 0;
  for (const glyph of String(key)) hash = (hash * 31 + glyph.charCodeAt(0)) >>> 0;
  return AVATAR_COLORS[hash % AVATAR_COLORS.length];
}

/** @handle (없으면 "탐험가") */
export const displayHandle = (handle: string | null | undefined): string => (handle ? '@' + handle : '탐험가');

/** 아바타 글자(handle 첫 글자 대문자) */
export const initialOf = (handle: string | null | undefined, fallback = '?'): string => (handle || fallback)[0].toUpperCase();

export type Relation = 'mutual' | 'following' | 'follower';
export const relationOf = (person: FriendResponse): Relation => (person.mutual ? 'mutual' : person.following ? 'following' : 'follower');
export const RELATION_LABEL: Readonly<Record<Relation, string>> = { mutual: '친구', following: '팔로잉', follower: '나를 팔로우' };

/** 지도 전체 지역 수 대비 % */
export const shareOfAll = (count: number, total: number): number => (total ? Math.round(100 * count / total) : 0);

/** 친구 소식 문장 조각: [강조(굵게), 나머지] */
export function feedParts(item: FeedItemResponse, catalog: Catalog | null, setName: (id: string) => string | undefined, badgeName: (id: string) => string | undefined): { strong: string | null; text: string; prefix?: string } {
  if (item.kind === 'VISIT') {
    const label = item.regionCode && catalog ? labelOfCode(catalog, item.regionCode) : item.regionCode ?? '';
    const rarity = item.rarity === 'LEGEND' ? ' · 전설 지역' : item.rarity === 'RARE' ? ' · 희귀 지역' : '';
    return { strong: label, text: `에 발 도장${rarity}` };
  }
  if (item.kind === 'THEME_COMPLETED') return { strong: setName(item.themeId ?? '') ?? item.themeId ?? '', text: ' 테마를 완성했어요' };
  if (item.kind === 'LEVEL_UP') return { strong: `Lv.${item.level}`, text: ' 달성' };
  if (item.kind === 'STREAK_MILESTONE') return { strong: `${item.months}개월 연속 탐험`, text: '을 달성했어요' };
  if (item.kind === 'PROVINCE_CONQUERED') {
    const province = (item.provinceCode && catalog?.provinceByCode.get(item.provinceCode)) || item.provinceCode || '';
    return { strong: province, text: `${objectParticle(province)} 정복했어요` };
  }
  if (item.kind === 'MYSTERY_FOUND') {
    const region = item.regionCode && catalog ? catalog.byCode.get(toClientCode(item.regionCode))?.properties.name ?? item.regionCode : item.regionCode ?? '';
    return { prefix: '이번 주 미스터리 지역 ', strong: region, text: `${objectParticle(region)} 찾았어요` };
  }
  return { strong: null, prefix: `뱃지 「${badgeName(item.badgeId ?? '') ?? item.badgeId ?? ''}」를 얻었어요`, text: '' };
}

/** 목적격 조사(을/를) — 마지막 글자에 받침이 있으면 "을" */
export function objectParticle(word: string): string {
  const last = word.charCodeAt(word.length - 1);
  const hangul = last >= 0xac00 && last <= 0xd7a3;
  return hangul && (last - 0xac00) % 28 !== 0 ? '을' : '를';
}

export type CompareClass = 'vs-mine' | 'vs-both' | 'vs-theirs';

/** 영토 비교 지도 색(화면 코드 → 클래스) */
export function compareClasses(compare: CompareResponse): Map<string, CompareClass> {
  const classes = new Map<string, CompareClass>();
  compare.onlyMine.forEach(code => classes.set(toClientCode(code), 'vs-mine'));
  compare.both.forEach(code => classes.set(toClientCode(code), 'vs-both'));
  compare.onlyTheirs.forEach(code => classes.set(toClientCode(code), 'vs-theirs'));
  return classes;
}

/** 지역 목록 요약(6곳 + 외 n곳) */
export function regionNames(codes: readonly string[], catalog: Catalog | null): string {
  const names = codes.slice(0, 6).map(code => (catalog ? labelOfCode(catalog, code) : code)).join(', ');
  return names + (codes.length > 6 ? ` 외 ${codes.length - 6}곳` : '');
}

/** 앞섬·뒤짐 문구 */
export function leadText(lead: number): string {
  return lead > 0 ? `${lead}곳 앞섬` : lead < 0 ? `${-lead}곳 뒤짐` : '동점';
}
