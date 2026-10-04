/**
 * 아이템 룩(그림 종류·색) — 프로토타입 lookOf 와 같은 규칙. 룩 자체는 서버 카탈로그(ItemView.look)가 준다.
 * 화면은 슬롯에 맞지 않는 룩을 그 슬롯의 기본 형태로 바꿔 그리기만 한다.
 */
import type { ItemLook } from '../../../api/types/catalog';
import { darken, mix, WHITE_RGB } from './color';

/** 화면 슬롯: 손·키링·모자·배낭·동행·배경·장식(+ 오라) */
export type ClientSlot = 'hat' | 'hand' | 'badge' | 'back' | 'pet' | 'bg' | 'prop' | 'aura';

/** 픽셀 페인터가 그리는 데 필요한 아이템 정보. code 는 캐시 키(아이템 id). */
export interface PixelItem {
  code: string;
  emoji: string;
  slot: ClientSlot;
  look: ItemLook | null;
  theme: string | null;
  /** 2 = 재방문 2회차 색 변형(look 이 이미 변형 룩) — 같은 아이템 id 라도 그림이 달라 캐시 키에 넣는다 */
  variant?: number;
}

/** 그림 캐시 키(아이템 id + 색 변형) */
export function pixelKey(item: Pick<PixelItem, 'code' | 'variant'>): string {
  return item.variant && item.variant > 1 ? `${item.code}~v${item.variant}` : item.code;
}

export interface Look {
  type: string;
  primary: string;
  secondary: string;
  /** 배경 룩만: 하늘 끝 색 */
  accent?: string;
}

/** 손에 드는 것 */
export const HAND_TYPES: ReadonlySet<string> = new Set(['camera', 'map', 'stick', 'umbrella', 'lantern', 'cup', 'rod', 'kite', 'bouquet', 'book', 'ukulele', 'telescope', 'flag', 'fan', 'balloon', 'board', 'basket']);
export const HAT_TYPES: ReadonlySet<string> = new Set(['straw', 'cap', 'beanie', 'bucket', 'bandana', 'flower', 'conical', 'mask', 'crown', 'circlet', 'bow', 'ears', 'headphones', 'hornband', 'fedora', 'sunhat', 'beret', 'star']);
export const BADGE_TYPES: ReadonlySet<string> = new Set(['keyring']);
export const PET_TYPES: ReadonlySet<string> = new Set(['dog', 'rodent', 'cow', 'sheep', 'bird', 'dino', 'seal', 'fishpet']);
export const PROP_TYPES: ReadonlySet<string> = new Set(['parasol', 'hanok', 'statue', 'tree', 'bamboo', 'railbike', 'candle', 'vase']);

/** 배경 이모지 → 풍경 테마(서버 theme 이 없을 때) */
export const BG_EMOJI: Readonly<Record<string, string>> = {
  '🌅': 'sunset', '🎆': 'fireworks', '🌾': 'reeds', '🥾': 'mountain', '⛴️': 'sea', '🏛️': 'city', '🌉': 'night', '🏙️': 'city', '🏯': 'palace', '🏘️': 'cherry',
};
/** 오라 이모지 → 색 */
export const AURA_COLOR: Readonly<Record<string, string>> = {
  '🦭': '#90caf9', '🐟': '#4fc3f7', '❄️': '#e3f2fd', '🐕': '#ffd166', '🧂': '#ffffff', '🌲': '#66bb6a', '✨': '#ffd166', '🍎': '#ff5252', '🌊': '#29b6f6', '🍀': '#9ccc65',
};

type LookTriple = readonly [string, string, string];
const DEFAULT_LOOK: Readonly<Partial<Record<ClientSlot, LookTriple>>> = {
  hand: ['map', '#e9c46a', '#2e7d32'],
  badge: ['keyring', '#9aa7b5', '#f4c542'],
  hat: ['beanie', '#9aa7b5', '#2b3542'],
  back: ['bag', '#8d6e63', '#5c3b2e'],
  pet: ['dog', '#d9a05b', '#2b3542'],
  prop: ['vase', '#8d5524', '#f7f3ea'],
};
const HAND_DEFAULT: LookTriple = ['map', '#e9c46a', '#2e7d32'];

/** 배경 테마 키(서버 theme → 이모지 → plain) */
export function backgroundThemeKey(item: Pick<PixelItem, 'theme' | 'emoji'>): string {
  return item.theme || BG_EMOJI[item.emoji] || 'plain';
}

/** 아이템을 어떻게 그릴지(프로토타입 lookOf). 배경 테마 색은 sky/ground 를 받아 채운다. */
export function lookOf(item: PixelItem, themeColors: (themeKey: string) => { sky: readonly string[]; ground: readonly string[] }): Look {
  if (item.slot === 'aura') return { type: 'aura', primary: AURA_COLOR[item.emoji] || '#b48af0', secondary: '#ffd166' };
  if (item.slot === 'bg') {
    const theme = themeColors(item.theme || BG_EMOJI[item.emoji] || 'plain');
    return { type: 'bg', primary: theme.sky[0], secondary: theme.ground[0], accent: theme.sky[theme.sky.length - 1] };
  }
  let triple: LookTriple | null = item.look ? [item.look.type, item.look.primary, item.look.secondary] : null;
  if (item.slot === 'hat' && triple && !HAT_TYPES.has(triple[0])) triple = ['beanie', triple[1], triple[2]];
  if (item.slot === 'back' && triple && triple[0] !== 'bag') triple = ['bag', mix(triple[1], 0.25, WHITE_RGB), darken(triple[1], 0.2)];
  if (item.slot === 'hand' && triple && !HAND_TYPES.has(triple[0])) triple = ['map', triple[1], triple[2]];
  if (item.slot === 'badge' && triple && !BADGE_TYPES.has(triple[0])) triple = ['keyring', triple[1], triple[2]];
  if (item.slot === 'pet' && triple && !PET_TYPES.has(triple[0])) triple = DEFAULT_LOOK.pet ?? null;
  if (item.slot === 'prop' && triple && !PROP_TYPES.has(triple[0])) triple = DEFAULT_LOOK.prop ?? null;
  if (!triple) triple = DEFAULT_LOOK[item.slot] || HAND_DEFAULT;
  return { type: triple[0], primary: triple[1], secondary: triple[2] || triple[1] };
}
