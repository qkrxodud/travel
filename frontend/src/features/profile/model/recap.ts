/**
 * 연간 리캡(프로필 탭) 문장 — 숫자·순위·동점은 서버 값(GET /me/recap, 리캡 카드 PNG 와 같은 계산)이고 여기서는 문장만 만든다.
 * 값이 아직 없으면(첫 로딩·실패) 0곳·"—" 로 보인다(기존 화면의 빈 리캡과 같은 모양).
 */
import type { RecapResponse } from '../../../api/types/sharing';
import { TIER_LABEL, toTier } from '../../../shared/lib/region/catalog';

const EMPTY = '—';
const NO_MONTHS: readonly number[] = Array.from({ length: 12 }, () => 0);

export interface RecapView {
  /** [항목 이름, 값 문장] — 화면 표시 순서 */
  cells: readonly (readonly [string, string])[];
  /** 1~12월 칸 수(index 0 = 1월) */
  months: readonly number[];
}

export function recapView(recap: RecapResponse | null | undefined): RecapView {
  const top = recap?.topProvince;
  const rarest = recap?.rarest;
  const busiest = recap?.busiestMonth;
  return {
    cells: [
      ['새 영토', `${recap?.newRegions ?? 0}곳`],
      ['가장 많이 간 시·도', top ? `${top.provinceName} ${top.count}곳` : EMPTY],
      ['가장 희귀한 곳', rarest ? `${rarest.name} (${TIER_LABEL[toTier(rarest.rarity)]})` : EMPTY],
      ['새로 밟은 시·도', `${recap?.newProvinces ?? 0}곳`],
      ['완성한 세트', `${recap?.setsCompleted ?? 0}개`],
      ['가장 바쁜 달', busiest ? `${busiest.month}월 ${busiest.count}곳` : EMPTY],
    ],
    months: recap?.monthCounts.length === 12 ? recap.monthCounts : NO_MONTHS,
  };
}
