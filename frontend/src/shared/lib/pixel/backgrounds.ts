/**
 * 장면 배경 — 프로토타입 BG_THEME·bgOps(저해상도 배경)·cozyScene(고해상도 코지 풍경) 이전본.
 * 시드 난수로 그리므로 같은 테마는 늘 같은 그림이다.
 */
import { darken } from './color';
import { OUTLINE } from './equipment';
import type { FillStyle, Painter, Point } from './painter';

export interface BackgroundTheme {
  sky: readonly string[];
  ground: readonly [string, string];
  draw: string | null;
  cozy?: 'field' | 'sea' | 'night' | 'salt' | 'forest' | 'snow' | 'river';
  premium?: boolean;
  legend?: boolean;
}

export const BG_THEME: Readonly<Record<string, BackgroundTheme>> = {
  plain: { sky: ['#78bfe8', '#a9dcf5', '#e2f3fb'], ground: ['#7fc24a', '#5a9a34'], draw: null, cozy: 'field' },
  beach: { sky: ['#9ad8ff', '#dff3ff'], ground: ['#f3e2b3', '#d9c48c'], draw: 'sea' },
  sea: { sky: ['#8ecae6', '#d6ecf5'], ground: ['#f3e2b3', '#d9c48c'], draw: 'seaship' },
  sunset: { sky: ['#6a1b9a', '#ff8c42', '#ffd166'], ground: ['#3d2b3a', '#2a1f29'], draw: 'sun' },
  fireworks: { sky: ['#0b1026', '#1b2a4a'], ground: ['#1a1f2b', '#11151d'], draw: 'fireworks' },
  night: { sky: ['#0b1026', '#243b6b'], ground: ['#1a1f2b', '#11151d'], draw: 'citynight' },
  city: { sky: ['#bcd7e8', '#e8f1f6'], ground: ['#9aa7b5', '#7b8794'], draw: 'city' },
  cherry: { sky: ['#dbeafe', '#fce4ec'], ground: ['#9ccc65', '#7cb342'], draw: 'cherry' },
  reeds: { sky: ['#ffb74d', '#ffe0b2'], ground: ['#c9a24c', '#a3802e'], draw: 'reeds' },
  mountain: { sky: ['#a7c4e2', '#e3eef8'], ground: ['#6b8e4e', '#4f6b3a'], draw: 'mountain' },
  sunrise: { sky: ['#ff7b54', '#ffb26b', '#ffe29a'], ground: ['#f3e2b3', '#d9c48c'], draw: 'sunrise', premium: true },
  harbor: { sky: ['#0b1026', '#1d3557'], ground: ['#3a3f4b', '#2a2e38'], draw: 'harbor', premium: true },
  palace: { sky: ['#dbeafe', '#f1f5f9'], ground: ['#c9b79c', '#a8977c'], draw: 'palace', premium: true },
  islands: { sky: ['#7ec8ff', '#d9f1ff'], ground: ['#f3e2b3', '#d9c48c'], draw: 'islands', premium: true },
  stadium: { sky: ['#0b1026', '#13224a'], ground: ['#3e9b4f', '#2f7d3d'], draw: 'stadium', premium: true },
  market: { sky: ['#2b1d2e', '#4a2c4f'], ground: ['#5c4a3a', '#45362b'], draw: 'market', premium: true },
  dmz: { sky: ['#5e6b8a', '#c9a8c9', '#ffd4a3'], ground: ['#6b8e4e', '#4f6b3a'], draw: 'dmz', premium: true },
  ridge: { sky: ['#3b4a6b', '#8aa3bd', '#ffe0b2'], ground: ['#3f5a3a', '#2f452b'], draw: 'ridge', premium: true },
  hanriver: { sky: ['#0b1026', '#1a2a52'], ground: ['#1a2a52', '#0f1c3a'], draw: 'hanriver', premium: true },
  /* 전설 지역 풍경 */
  dokdo: { sky: ['#5fb0e8', '#a9dcf5', '#e2f3fb'], ground: ['#6b8e4e', '#4f6b3a'], draw: 'dokdo', legend: true, cozy: 'sea' },
  baengnyeong: { sky: ['#a9d6f5', '#eaf6ff'], ground: ['#e8dcc0', '#cdbf9c'], draw: 'baengnyeong', legend: true, cozy: 'sea' },
  firefly: { sky: ['#061020', '#0d2436', '#17403a'], ground: ['#1d3a2a', '#152c20'], draw: 'firefly', legend: true, cozy: 'night' },
  jindo: { sky: ['#8ecae6', '#fff1d6'], ground: ['#e8dcc0', '#cdbf9c'], draw: 'jindo', legend: true, cozy: 'sea' },
  saltfield: { sky: ['#ff8c42', '#ffc078', '#ffe9c2'], ground: ['#d9c48c', '#b8a46a'], draw: 'saltfield', legend: true, cozy: 'salt' },
  pine: { sky: ['#cfe6f3', '#f4f9fb'], ground: ['#5e7a4a', '#46603a'], draw: 'pine', legend: true, cozy: 'forest' },
  orchard: { sky: ['#78bfe8', '#a9dcf5', '#e2f3fb'], ground: ['#7cb342', '#558b2f'], draw: 'orchard', legend: true, cozy: 'field' },
  punchbowl: { sky: ['#b9d3ea', '#f2f7fb'], ground: ['#f4f8fb', '#d7e3ee'], draw: 'punchbowl', legend: true, cozy: 'snow' },
  icelake: { sky: ['#9fc5e8', '#eaf3fb'], ground: ['#f4f8fb', '#d7e3ee'], draw: 'icelake', legend: true, cozy: 'snow' },
  river: { sky: ['#8ecae6', '#e8f6ff'], ground: ['#7cb342', '#558b2f'], draw: 'river', legend: true, cozy: 'river' },
};

export function themeOf(key: string): BackgroundTheme {
  return BG_THEME[key] || BG_THEME.plain;
}

/** 저해상도 배경 격자(96×60) */
export const BG_WIDTH = 96;
export const BG_HEIGHT = 60;
export const BG_GROUND = 48;

/** 장면 캔버스(192×120 논리 픽셀)와 캐릭터 자리 */
export const SCENE_WIDTH = 192;
export const SCENE_HEIGHT = 120;
export const GROUND_Y = 96;
export const CHAR_SCALE = 1.5;
export const CHAR_X = 96 - 20 * 1.5;
export const CHAR_Y = 96 - 56 * 1.5 + 2;

/** 선형 합동 난수(프로토타입 seededRnd) */
export function seededRandom(seed: number): () => number {
  let state = seed;
  return () => {
    state = (state * 1664525 + 1013904223) % 4294967296;
    return state / 4294967296;
  };
}

/** [x, y, w, h, color] */
export type Band = [number, number, number, number, string];
/** [x, y, color] */
export type Dot = [number, number, string];

type Palette = Readonly<Record<string, string>>;

/** 저해상도 배경 그리기 명령(색 띠 + 점) — 프로토타입 bgOps */
export function backgroundOps(theme: BackgroundTheme): { bands: Band[]; px: Dot[] } {
  const bands: Band[] = [];
  const px: Dot[] = [];
  const rnd = seededRandom(7);
  const rect = (left: number, top: number, width: number, height: number, color: string) => bands.push([left, top, width, height, color]);
  const dark = '#1b2430';
  const sky = theme.sky;
  const layers = sky.length;
  for (let i = 0; i < layers; i++) rect(0, Math.round(BG_GROUND * i / layers), BG_WIDTH, Math.round(BG_GROUND * (i + 1) / layers) - Math.round(BG_GROUND * i / layers), sky[i]);
  /** 문자 격자 그림: palette 의 글자만 찍는다 */
  const grid = (rows: readonly string[], palette: Palette, originX: number, originY: number) =>
    rows.forEach((row, rowIndex) => [...row].forEach((glyph, colIndex) => {
      if (glyph !== '.' && palette[glyph]) px.push([originX + colIndex, originY + rowIndex, palette[glyph]]);
    }));
  const floor = Math.floor;
  const ripples = (count: number, top: number, span: number, color: string) => {
    for (let i = 0; i < count; i++) {
      const rippleX = floor(rnd() * BG_WIDTH);
      const rippleY = top + floor(rnd() * span);
      px.push([rippleX, rippleY, color], [rippleX + 1, rippleY, color]);
    }
  };
  const gulls = (spots: readonly (readonly [number, number])[]) => spots.forEach(([gullX, gullY]) =>
    px.push([gullX, gullY, dark], [gullX + 1, gullY - 1, dark], [gullX + 2, gullY, dark], [gullX + 3, gullY - 1, dark], [gullX + 4, gullY, dark]));
  const peaks = (list: readonly (readonly [number, number, string])[], slope: number, base: number, snowRows: number) => list.forEach(([centerX, height, color]) => {
    for (let row = 0; row < height; row++) {
      const half = floor(row * slope);
      rect(centerX - half, base - height + row, half * 2 + 1, 1, row < snowRows ? '#ffffff' : color);
    }
  });
  switch (theme.draw) {
    case 'sea':
    case 'seaship':
      rect(0, 26, BG_WIDTH, 22, '#2b8fd6');
      rect(0, 26, BG_WIDTH, 1, '#bde5ff');
      ripples(14, 29, 16, '#9fd4ff');
      if (theme.draw === 'seaship') grid(['....O....', '....Ob...', '....Obb..', '....Obbb.', '....O....', 'OOOOOOOOO', '.OaaaaaO.', '..OOOOO..'], { O: dark, a: '#f7f3ea', b: '#e63946' }, 70, 22);
      break;
    case 'sun':
      grid(['..OOOO..', '.OaaaaO.', 'OaaaaaaO', 'OaaaaaaO', 'OaaaaaaO', '.OaaaaO.', '..OOOO..'], { O: '#ff8c42', a: '#ffd166' }, 62, 14);
      rect(0, 34, BG_WIDTH, 14, '#4a2c5a');
      ripples(10, 36, 10, '#ff8c42');
      break;
    case 'fireworks':
      for (let i = 0; i < 40; i++) px.push([floor(rnd() * BG_WIDTH), floor(rnd() * 30), '#e8edf2']);
      ([[20, 10, '#ff5252'], [62, 8, '#ffd166'], [80, 18, '#2fc3ad'], [42, 16, '#b48af0']] as const).forEach(([centerX, centerY, color]) => {
        ([[0, -4], [0, 4], [-4, 0], [4, 0], [-3, -3], [3, 3], [-3, 3], [3, -3], [0, -2], [0, 2], [-2, 0], [2, 0]] as const)
          .forEach(([offsetX, offsetY]) => px.push([centerX + offsetX, centerY + offsetY, color]));
        px.push([centerX, centerY, '#ffffff']);
      });
      rect(0, 36, BG_WIDTH, 12, '#0d1a33');
      for (let i = 0; i < 16; i++) px.push([floor(rnd() * BG_WIDTH), 38 + floor(rnd() * 8), '#ffd166']);
      break;
    case 'city':
    case 'citynight': {
      const night = theme.draw === 'citynight';
      if (night) for (let i = 0; i < 30; i++) px.push([floor(rnd() * BG_WIDTH), floor(rnd() * 22), '#e8edf2']);
      let cursor = 0;
      while (cursor < BG_WIDTH) {
        const width = 5 + floor(rnd() * 7);
        const height = 10 + floor(rnd() * 22);
        rect(cursor, BG_GROUND - height, width, height, night ? '#141a2b' : '#6b7a8c');
        rect(cursor, BG_GROUND - height, width, 1, night ? '#2b3a5c' : '#8f9fb0');
        for (let windowY = BG_GROUND - height + 2; windowY < BG_GROUND - 2; windowY += 3) {
          for (let windowX = cursor + 1; windowX < cursor + width - 1; windowX += 2) if (rnd() < (night ? 0.55 : 0.4)) px.push([windowX, windowY, night ? '#ffd166' : '#e3f2fd']);
        }
        cursor += width + 1;
      }
      if (night) {
        rect(0, 30, BG_WIDTH, 1, '#ffd166');
        for (let i = 4; i < BG_WIDTH; i += 12) rect(i, 24, 1, 7, '#9aa7b5');
      }
      break;
    }
    case 'cherry':
      for (let i = 0; i < 6; i++) grid(['...OOOOOO...', '.OOaabaaaaO.', 'OaabaaabaaaO', 'OaaaaaaaaaaO', '.OaabaaaaaO.', '..OOOaaOOO..', '....OccO....', '....OccO....', '....OccO....'], { O: '#c2185b', a: '#f8bbd0', b: '#fce4ec', c: '#8d5524' }, i * 17 - 4, BG_GROUND - 9);
      for (let i = 0; i < 20; i++) px.push([floor(rnd() * BG_WIDTH), floor(rnd() * 40), '#f8bbd0']);
      break;
    case 'reeds':
      for (let reedX = 1; reedX < BG_WIDTH; reedX += 2 + floor(rnd() * 2)) {
        const height = 8 + floor(rnd() * 10);
        for (let reedY = BG_GROUND - height; reedY < BG_GROUND; reedY++) px.push([reedX, reedY, reedY < BG_GROUND - height + 3 ? '#f3e2b3' : '#c9a24c']);
      }
      break;
    case 'mountain':
      peaks([[10, 30, '#5f7d9a'], [50, 34, '#5f7d9a'], [84, 28, '#5f7d9a'], [30, 22, '#8aa3bd'], [70, 24, '#8aa3bd']], 1.1, BG_GROUND, 4);
      break;
    case 'sunrise':
      rect(0, 24, BG_WIDTH, 24, '#2b8fd6');
      rect(0, 24, BG_WIDTH, 1, '#ffe29a');
      grid(['...OOOOOO...', '.OOaaaaaaOO.', 'OaaaaaaaaaaO', 'OaaaaaaaaaaO', 'OaaaaaaaaaaO', 'OaaaaaaaaaaO'], { O: '#ff7b54', a: '#fff3b0' }, 42, 13);
      for (let glowX = 0; glowX < BG_WIDTH; glowX += 3) {
        for (let glowY = 26; glowY < 46; glowY += 4) if (Math.abs(glowX - 48) < (glowY - 20) * 1.1) px.push([glowX, glowY + ((glowX / 3) % 2), '#ffe29a']);
      }
      ripples(14, 27, 16, '#9fd4ff');
      gulls([[14, 8], [22, 11], [70, 6], [80, 12]]);
      break;
    case 'harbor':
      for (let i = 0; i < 30; i++) px.push([floor(rnd() * BG_WIDTH), floor(rnd() * 20), '#e8edf2']);
      rect(0, 28, BG_WIDTH, 20, '#102a52');
      rect(0, 28, BG_WIDTH, 1, '#4a6fa5');
      grid(['...OO...', '..OaaO..', '..OccO..', '..OaaO..', '.OaaaaO.', '.ObbbbO.', '.OaaaaO.', '.ObbbbO.', '.OaaaaO.', 'OOOOOOOO'], { O: dark, a: '#f7f3ea', b: '#e63946', c: '#ffd166' }, 78, 14);
      for (let lightX = 2; lightX < BG_WIDTH; lightX += 5) px.push([lightX, 30 + floor(rnd() * 14), '#ffd166']);
      for (let mastX = 0; mastX < 60; mastX += 2) {
        rect(mastX, 22 - floor(rnd() * 6), 1, 6, '#1a2a52');
        if (rnd() < 0.5) px.push([mastX, 20, '#ffd166']);
      }
      break;
    case 'palace':
      grid(['..............OO..............', '...........OOOaaOOO...........', '........OOOaaaaaaaaOOO........', '.....OOOaaaaaaaaaaaaaaOOO.....', '..OOOaaaaaaaaaaaaaaaaaaaaOOO..', 'OOaaaaaaaaaaaaaaaaaaaaaaaaaaOO', 'OOOOOOOOOOOOOOOOOOOOOOOOOOOOOO', '.ObbbbObbbbbObbbbObbbbbObbbbO.', '.OccccOcccccOccccOcccccOccccO.', '.ObbbbObbbbbObbbbObbbbbObbbbO.', '.OccccOcccccOccccOcccccOccccO.', '.ObbbbObbbbbObbbbObbbbbObbbbO.', 'OOOOOOOOOOOOOOOOOOOOOOOOOOOOOO'], { O: '#2b3542', a: '#3f5f4f', b: '#b23a3a', c: '#2fa37a' }, 33, BG_GROUND - 13);
      [6, 86].forEach(lanternX => grid(['..OcO..', '.OOOOO.', 'OaaabaO', 'OaaabaO', 'OabbbaO', 'OaaabaO', '.OOOOO.', '...O...', '...O...', '...O...'], { O: dark, a: '#e63946', b: '#ff8a80', c: '#f4c542' }, lanternX, BG_GROUND - 10));
      for (let i = 0; i < 16; i++) px.push([floor(rnd() * BG_WIDTH), floor(rnd() * 30), '#ffd166']);
      break;
    case 'islands':
      rect(0, 26, BG_WIDTH, 22, '#2b8fd6');
      rect(0, 26, BG_WIDTH, 1, '#d9f1ff');
      ([[10, 5, 10], [60, 4, 8], [82, 7, 12]] as const).forEach(([centerX, height, width]) => {
        for (let row = 0; row < height; row++) rect(centerX - width + row, 26 - height + row, (width - row) * 2, 1, row < 2 ? '#558b2f' : '#2e5a3a');
      });
      grid(['....O....', '....Oa...', '....Oaa..', '....Oaaa.', '....Oaa..', '....O....', 'OOOOOOOOO', '.ObbbbbO.', '..OOOOO..'], { O: dark, a: '#f7f3ea', b: '#e63946' }, 30, 20);
      ripples(16, 29, 16, '#9fd4ff');
      for (let i = 0; i < 10; i++) px.push([floor(rnd() * BG_WIDTH), floor(rnd() * 20), '#ffffff']);
      break;
    case 'stadium':
      [8, 86].forEach(poleX => {
        rect(poleX, 6, 2, 42, '#9aa7b5');
        grid(['OOOOOOOO', 'OaaaaaaO', 'OaaaaaaO', 'OOOOOOOO'], { O: '#2b3542', a: '#fff6cc' }, poleX - 3, 4);
        for (let beamY = 8; beamY < 44; beamY += 2) px.push([poleX + (poleX < 48 ? 3 : -2), beamY, '#fff6cc']);
      });
      rect(0, 30, BG_WIDTH, 18, '#2b3542');
      for (let seatY = 31; seatY < 47; seatY += 2) {
        for (let seatX = 1; seatX < BG_WIDTH; seatX += 2) if (rnd() < 0.6) px.push([seatX, seatY, ['#e63946', '#f7f3ea', '#ffd166', '#2fc3ad'][floor(rnd() * 4)]]);
      }
      break;
    case 'market':
      for (let stallX = 0; stallX < BG_WIDTH; stallX += 6) {
        grid(['.OcO.', 'OOOOO', 'OaabO', 'OaabO', 'OOOOO'], { O: dark, a: '#e63946', b: '#ff8a80', c: '#f4c542' }, stallX, 6 + ((stallX / 6) % 2) * 3);
        rect(stallX + 2, 2, 1, 4, '#5c4a3a');
      }
      rect(0, 2, BG_WIDTH, 1, '#8d6e63');
      for (let i = 0; i < 14; i++) {
        const steamX = floor(rnd() * BG_WIDTH);
        const steamY = 24 + floor(rnd() * 20);
        px.push([steamX, steamY, 'rgba(255,255,255,.35)'], [steamX + 1, steamY - 1, 'rgba(255,255,255,.25)']);
      }
      rect(0, 34, BG_WIDTH, 14, '#3a2d33');
      for (let shopX = 0; shopX < BG_WIDTH; shopX += 8) {
        rect(shopX, 30, 7, 18, '#4a3a40');
        rect(shopX + 1, 32, 5, 4, '#ffd166');
      }
      break;
    case 'dmz':
      ([[20, 14], [60, 18], [90, 12]] as const).forEach(([centerX, height]) => {
        for (let row = 0; row < height; row++) {
          const half = floor(row * 1.8);
          rect(centerX - half, BG_GROUND - 10 - height + row, half * 2 + 1, 1, '#4a5d7a');
        }
      });
      rect(0, BG_GROUND - 10, BG_WIDTH, 10, '#5b7a4a');
      for (let postX = 3; postX < BG_WIDTH; postX += 7) rect(postX, BG_GROUND - 16, 1, 6, '#2b3542');
      for (let postX = 3; postX < BG_WIDTH - 7; postX += 7) {
        for (let wire = 1; wire < 7; wire += 2) px.push([postX + wire, BG_GROUND - 14 - (wire % 4 === 1 ? 1 : 0), '#2b3542']);
      }
      ([[12, 10], [26, 7], [72, 9]] as const).forEach(([birdX, birdY]) => grid(['..O..', '.OaO.', 'OaaaO', '.OaO.', '.ObO.', '.ObO.'], { O: dark, a: '#f7f3ea', b: '#e63946' }, birdX, birdY));
      break;
    case 'ridge':
      grid(['..OOOO..', '.OaaaaO.', 'OaaaaaaO', 'OaaaaaaO', '.OaaaaO.', '..OOOO..'], { O: '#ff8c42', a: '#ffe29a' }, 70, 12);
      peaks([[16, 30, '#2f4a3a'], [48, 36, '#2f4a3a'], [84, 28, '#2f4a3a'], [32, 22, '#5a7a68'], [68, 24, '#5a7a68'], [8, 16, '#8aa3bd']], 1.3, BG_GROUND, 0);
      for (let i = 0; i < 24; i++) rect(floor(rnd() * BG_WIDTH), BG_GROUND - 14 + floor(rnd() * 10), 4 + floor(rnd() * 6), 1, 'rgba(255,255,255,.45)');
      break;
    case 'hanriver': {
      for (let i = 0; i < 30; i++) px.push([floor(rnd() * BG_WIDTH), floor(rnd() * 18), '#e8edf2']);
      let cursor = 0;
      while (cursor < BG_WIDTH) {
        const width = 4 + floor(rnd() * 6);
        const height = 8 + floor(rnd() * 16);
        rect(cursor, 30 - height, width, height, '#141a2b');
        for (let windowY = 32 - height; windowY < 28; windowY += 3) {
          for (let windowX = cursor + 1; windowX < cursor + width - 1; windowX += 2) if (rnd() < 0.5) px.push([windowX, windowY, '#ffd166']);
        }
        cursor += width + 1;
      }
      rect(0, 30, BG_WIDTH, 18, '#0f1c3a');
      rect(0, 26, BG_WIDTH, 2, '#6b7a8c');
      for (let towerX = 6; towerX < BG_WIDTH; towerX += 16) {
        rect(towerX, 18, 1, 8, '#9aa7b5');
        for (let cable = 1; cable < 8; cable++) px.push([towerX - cable, 18 + cable, '#9aa7b5'], [towerX + cable, 18 + cable, '#9aa7b5']);
        px.push([towerX, 17, '#ff5252']);
      }
      for (let glowX = 2; glowX < BG_WIDTH; glowX += 4) rect(glowX, 31 + floor(rnd() * 3), 1, 3 + floor(rnd() * 5), 'rgba(255,209,102,.45)');
      for (let i = 0; i < 12; i++) px.push([floor(rnd() * BG_WIDTH), 20 + floor(rnd() * 8), '#2fc3ad']);
      break;
    }
    case 'dokdo':
      rect(0, 24, BG_WIDTH, 24, '#2b8fd6');
      rect(0, 24, BG_WIDTH, 1, '#dff0ff');
      ripples(16, 27, 18, '#9fd4ff');
      ([[22, 14, 9], [40, 10, 6]] as const).forEach(([centerX, height, width]) => {
        for (let row = 0; row < height; row++) {
          const half = floor(width * (row + 2) / (height + 2));
          rect(centerX - half, 25 - height + row, half * 2 + 1, 1, row < 2 ? '#7a8a7a' : '#4a5a5a');
        }
      });
      grid(['..OOOO..', '.OaaaaO.', 'OaaaaaaO', 'OaaaaaaO', '.OaaaaO.', '..OOOO..'], { O: '#ffb347', a: '#fff3b0' }, 72, 6);
      gulls([[8, 6], [14, 9], [60, 4]]);
      break;
    case 'baengnyeong':
      rect(0, 28, BG_WIDTH, 20, '#5ab0e0');
      rect(0, 28, BG_WIDTH, 1, '#eaf6ff');
      ripples(14, 31, 14, '#bfe6ff');
      ([[14, 40], [60, 42], [84, 38]] as const).forEach(([rockX, rockY]) => {
        rect(rockX - 8, rockY, 16, 4, '#7a8a8a');
        rect(rockX - 6, rockY - 1, 12, 1, '#9aa7a7');
        grid(['..OOOO...', '.OaaaaOO.', 'OaaaaaaaO', 'OOOOOOOO.'], { O: '#2b3542', a: '#9aa7b5' }, rockX - 4, rockY - 4);
      });
      ([[30, 8], [50, 12]] as const).forEach(([cloudX, cloudY]) => grid(['.aa.aa.', 'aaaaaaa', '.aaaaa.'], { a: '#ffffff' }, cloudX, cloudY));
      break;
    case 'firefly':
      ([[10, 24], [30, 30], [50, 26], [70, 32], [90, 28]] as const).forEach(([centerX, height]) => {
        for (let row = 0; row < height; row++) {
          const half = floor(row * 0.45);
          rect(centerX - half, BG_GROUND - height + row, half * 2 + 1, 1, row % 2 ? '#0f2e22' : '#143a2a');
        }
      });
      grid(['.OOO.', 'OaaaO', 'OaaaO', '.OOO.'], { O: '#c9d9e0', a: '#f4f1ea' }, 78, 4);
      for (let i = 0; i < 46; i++) {
        const glowX = floor(rnd() * BG_WIDTH);
        const glowY = 6 + floor(rnd() * 40);
        px.push([glowX, glowY, i % 3 ? '#c6ff6b' : '#fff59d']);
        if (i % 4 === 0) px.push([glowX + 1, glowY, '#8bc34a'], [glowX - 1, glowY, '#8bc34a'], [glowX, glowY + 1, '#8bc34a'], [glowX, glowY - 1, '#8bc34a']);
      }
      break;
    case 'jindo':
      rect(0, 24, BG_WIDTH, 24, '#3fa0d8');
      rect(0, 24, BG_WIDTH, 1, '#fff1d6');
      for (let row = 24; row < 48; row++) {
        const half = 3 + floor((row - 24) * 0.6);
        rect(48 - half, row, half * 2, 1, '#e8dcc0');
      }
      for (let i = 0; i < 14; i++) {
        const rippleX = floor(rnd() * BG_WIDTH);
        const rippleY = 27 + floor(rnd() * 18);
        if (Math.abs(rippleX - 48) > 14) px.push([rippleX, rippleY, '#bfe6ff'], [rippleX + 1, rippleY, '#bfe6ff']);
      }
      ([[12, 5, 10], [84, 6, 12]] as const).forEach(([centerX, height, width]) => {
        for (let row = 0; row < height; row++) rect(centerX - width + row, 24 - height + row, (width - row) * 2, 1, row < 2 ? '#558b2f' : '#2e5a3a');
      });
      for (let i = 0; i < 12; i++) px.push([floor(rnd() * BG_WIDTH), floor(rnd() * 20), '#ffffff']);
      break;
    case 'saltfield':
      grid(['..OOOO..', '.OaaaaO.', 'OaaaaaaO', 'OaaaaaaO', '.OaaaaO.', '..OOOO..'], { O: '#ff6b35', a: '#ffe29a' }, 44, 10);
      for (let pondY = 26; pondY < BG_GROUND; pondY += 5) {
        for (let pondX = 0; pondX < BG_WIDTH; pondX += 12) {
          rect(pondX + 1, pondY + 1, 10, 3, pondY % 10 ? '#ffd9a8' : '#ffe9c2');
          rect(pondX, pondY, 12, 1, '#8d6e63');
          rect(pondX, pondY, 1, 5, '#8d6e63');
        }
      }
      for (let i = 0; i < 10; i++) px.push([floor(rnd() * BG_WIDTH), 27 + floor(rnd() * 18), '#ffffff']);
      ([[10, 22], [80, 20]] as const).forEach(([rakeX, rakeY]) => grid(['..O..', '.OaO.', 'OaaaO', '.OaO.', '..O..', '..O..'], { O: dark, a: '#8d5524' }, rakeX, rakeY));
      break;
    case 'pine':
      for (let trunkX = 2; trunkX < BG_WIDTH; trunkX += 9 + floor(rnd() * 4)) {
        const height = 30 + floor(rnd() * 14);
        rect(trunkX, BG_GROUND - height, 3, height, '#b5651d');
        rect(trunkX + 1, BG_GROUND - height, 1, height, '#d98b3a');
        for (let layer = 0; layer < 4; layer++) {
          const branchY = BG_GROUND - height + layer * 5;
          const half = 4 + layer;
          rect(trunkX + 1 - half, branchY, half * 2 + 1, 2, layer % 2 ? '#2e5a3a' : '#3f7a4a');
        }
      }
      for (let i = 0; i < 20; i++) rect(floor(rnd() * BG_WIDTH), 30 + floor(rnd() * 14), 5 + floor(rnd() * 8), 1, 'rgba(255,255,255,.5)');
      break;
    case 'orchard':
      ([[20, 10], [60, 12], [90, 9]] as const).forEach(([centerX, height]) => {
        for (let row = 0; row < height; row++) {
          const half = floor(row * 2.2);
          rect(centerX - half, 36 - height + row, half * 2 + 1, 1, '#9ccc65');
        }
      });
      for (let treeX = 4; treeX < BG_WIDTH; treeX += 14) {
        rect(treeX + 2, 36, 2, 12, '#8d5524');
        grid(['..OOOO..', '.OaaaaaO', 'OaaaaaaO', 'OabaaabO', 'OaaabaaO', '.OaaaaO.', '..OOOO..'], { O: '#2e5a3a', a: '#4caf50', b: '#e63946' }, treeX - 1, 28);
      }
      break;
    case 'punchbowl':
      ([[0, 26, '#6b8bb0'], [96, 26, '#6b8bb0'], [48, 10, '#8fb0d0']] as const).forEach(([centerX, height, color]) => {
        for (let row = 0; row < height; row++) {
          const half = floor(row * 1.6);
          rect(centerX - half, BG_GROUND - height + row, half * 2 + 1, 1, row < 3 ? '#ffffff' : color);
        }
      });
      for (let row = 40; row < BG_GROUND; row++) rect(0, row, BG_WIDTH, 1, '#eef4fa');
      for (let i = 0; i < 30; i++) px.push([floor(rnd() * BG_WIDTH), floor(rnd() * 46), '#ffffff']);
      grid(['.OO.', 'OaaO', 'OaaO', '.OO.'], { O: '#d7e3ee', a: '#ffffff' }, 80, 6);
      break;
    case 'icelake':
      ([[14, 18], [50, 22], [86, 16]] as const).forEach(([centerX, height]) => {
        for (let row = 0; row < height; row++) {
          const half = floor(row * 1.4);
          rect(centerX - half, 30 - height + row, half * 2 + 1, 1, row < 3 ? '#ffffff' : '#7fa3c7');
        }
      });
      rect(0, 30, BG_WIDTH, 18, '#cfe8f7');
      for (let i = 0; i < 12; i++) {
        const crackX = floor(rnd() * BG_WIDTH);
        const crackY = 32 + floor(rnd() * 14);
        rect(crackX, crackY, 4 + floor(rnd() * 6), 1, '#9fc5e8');
      }
      ([[20, '#e63946'], [48, '#1e88e5'], [76, '#f4c542']] as const).forEach(([tentX, color]) => grid(['...O...', '..OaO..', '.OaaaO.', 'OaaaaaO', 'OOOOOOO'], { O: dark, a: color }, tentX, 36));
      break;
    case 'river':
      ([[20, 12], [70, 14]] as const).forEach(([centerX, height]) => {
        for (let row = 0; row < height; row++) {
          const half = floor(row * 2.5);
          rect(centerX - half, 34 - height + row, half * 2 + 1, 1, '#9ccc65');
        }
      });
      rect(0, 34, BG_WIDTH, 14, '#4fb3e8');
      rect(0, 34, BG_WIDTH, 1, '#bfe6ff');
      ripples(14, 36, 10, '#bfe6ff');
      grid(['...OOOO...', '..OaaaaO..', '.OaabaaaO.', 'OaaaaaaaaO', 'OaabaaaaaO', 'OaaaaaaabO', '.OOOOOOOO.'], { O: '#4a5a5a', a: '#7a8a7a', b: '#9aa7a7' }, 56, 30);
      break;
    default:
      break;
  }
  if (theme.premium) for (let i = 0; i < 18; i++) px.push([floor(rnd() * BG_WIDTH), floor(rnd() * BG_GROUND), ['#ffd166', '#ffffff', '#b48af0'][i % 3]]);
  rect(0, BG_GROUND, BG_WIDTH, BG_HEIGHT - BG_GROUND, theme.ground[0]);
  rect(0, BG_GROUND, BG_WIDTH, 1, dark);
  for (let i = 0; i < 40; i++) px.push([floor(rnd() * BG_WIDTH), BG_GROUND + 2 + floor(rnd() * (BG_HEIGHT - BG_GROUND - 3)), theme.ground[1]]);
  return { bands, px };
}

/** 고해상도 코지 풍경(192×120) — 프로토타입 cozyScene */
export function cozyScene(pen: Painter, theme: BackgroundTheme, rnd: () => number): void {
  const width = SCENE_WIDTH;
  const ground = GROUND_Y;
  const kind = theme.cozy;
  const night = kind === 'night';
  const snow = kind === 'snow';
  const flat: FillStyle = { flat: true, outline: false };
  const floor = Math.floor;
  const grass = snow ? ['#eef4fa', '#dbe7f1', '#c5d6e4'] : night ? ['#2f6a3a', '#245a2e', '#1a4524'] : ['#7fc24a', '#a3d95e', '#5a9a34'];
  const bush = night ? ['#1f4a2a', '#2a5e34'] : snow ? ['#5e8a5a', '#7faa78'] : ['#3f7d3a', '#5aa34a'];
  // 하늘
  const sky = theme.sky;
  for (let i = 0; i < sky.length; i++) pen.fill(pen.rect(0, Math.round(ground * i / sky.length), width, Math.round(ground * (i + 1) / sky.length) - Math.round(ground * i / sky.length) + 1), sky[i], flat);
  // 별 / 구름
  if (night) {
    for (let i = 0; i < 50; i++) pen.dot(floor(rnd() * width), floor(rnd() * 60), i % 5 ? '#e8edf2' : '#ffd166');
    pen.fill(pen.ellipse(160, 16, 7, 7), '#fff6cc', { hi: 0.1, sh: 0.05, outline: false });
    pen.fill(pen.ellipse(164, 14, 6, 6), sky[0], flat);
  } else {
    ([[30, 14, 1], [110, 10, 1.3], [170, 20, 0.9]] as const).forEach(([cloudX, cloudY, size]) => {
      ([[0, 0, 9, 4], [-7, 2, 6, 3], [8, 2, 6, 3], [2, -3, 6, 3]] as const).forEach(([offsetX, offsetY, radiusX, radiusY]) =>
        pen.fill(pen.ellipse(cloudX + offsetX * size, cloudY + offsetY * size, radiusX * size, radiusY * size), '#ffffff', flat));
      pen.fill(pen.ellipse(cloudX, cloudY + 3 * size, 11 * size, 2 * size), '#dfeef8', flat);
    });
  }
  // 원경 산
  const range = night ? ['#0f2e3a', '#163d44'] : snow ? ['#9fb6cc', '#b9cde0'] : ['#6f9ab0', '#8fb6c9'];
  ([[20, 36, 60, range[1]], [70, 44, 70, range[1]], [150, 40, 70, range[1]], [40, 26, 46, range[0]], [120, 30, 56, range[0]], [185, 24, 50, range[0]]] as const)
    .forEach(([centerX, height, spread, color]) => {
      pen.fill(pen.poly([[centerX - spread, ground], [centerX - spread * 0.35, ground - height * 0.75], [centerX, ground - height], [centerX + spread * 0.3, ground - height * 0.8], [centerX + spread, ground]]), color, flat);
      if (!night) pen.fill(pen.poly([[centerX - spread * 0.14, ground - height * 0.82], [centerX, ground - height], [centerX + spread * 0.12, ground - height * 0.86], [centerX + spread * 0.04, ground - height * 0.72], [centerX - spread * 0.06, ground - height * 0.7]]), snow ? '#ffffff' : '#eef6fb', flat);
    });
  // 중경
  if (kind === 'sea') {
    pen.fill(pen.rect(0, ground - 34, width, 34), '#3f9bd8', flat);
    pen.fill(pen.rect(0, ground - 34, width, 2), '#bfe6ff', flat);
    for (let i = 0; i < 70; i++) {
      const waveX = floor(rnd() * width);
      const waveY = ground - 31 + floor(rnd() * 28);
      pen.fill(pen.rect(waveX, waveY, 2 + floor(rnd() * 3), 1), i % 3 ? '#8fd0f3' : '#ffffff', flat);
    }
    if (theme.draw === 'dokdo') {
      ([[40, 20, 14], [66, 14, 9]] as const).forEach(([centerX, height, spread]) => {
        pen.fill(pen.poly([[centerX - spread, ground - 30], [centerX - spread * 0.5, ground - 30 - height * 0.7], [centerX - spread * 0.1, ground - 30 - height], [centerX + spread * 0.4, ground - 30 - height * 0.6], [centerX + spread, ground - 30]]), '#5a6b6b', { ol: '#3a4646', wide: true, sh: 0.25 });
        pen.fill(pen.ellipse(centerX, ground - 30 - height + 2, spread * 0.4, 2), '#8aa08a', flat);
      });
    }
    if (theme.draw === 'baengnyeong') {
      ([[30, ground - 10], [120, ground - 6], [160, ground - 14]] as const).forEach(([sealX, sealY]) => {
        pen.fill(pen.ellipse(sealX, sealY, 14, 4), '#8a9a9a', { ol: '#5a6a6a' });
        pen.fill(pen.ellipse(sealX - 2, sealY - 5, 8, 3.5), '#b9c4cc', { ol: '#5f6b7a' });
        pen.fill(pen.ellipse(sealX + 5, sealY - 7, 3.5, 3), '#b9c4cc', { ol: '#5f6b7a' });
        pen.dot(sealX + 6, sealY - 8, '#1b2430');
      });
    }
    if (theme.draw === 'jindo') {
      pen.fill(pen.poly([[88, ground - 34], [104, ground - 34], [122, ground], [70, ground]]), '#e8dcc0', { ol: '#c9b88c' });
      pen.fill(pen.poly([[150, ground - 34], [170, ground - 48], [192, ground - 42], [192, ground - 34]]), '#5a9a34', flat);
    }
    ([[14, ground - 36], [140, ground - 42]] as const).forEach(([birdX, birdY]) => {
      pen.fill(pen.poly([[birdX, birdY], [birdX + 2, birdY - 2], [birdX + 4, birdY], [birdX + 6, birdY - 2], [birdX + 8, birdY]]), night ? '#9aa7b5' : '#4a5a6a', flat);
    });
    // 작은 배
    pen.fill(pen.poly([[150, ground - 20], [172, ground - 20], [168, ground - 15], [154, ground - 15]]), '#8d5524', { ol: OUTLINE });
    pen.fill(pen.rect(160, ground - 32, 1, 12), '#5c3b2e', flat);
    pen.fill(pen.poly([[161, ground - 31], [169, ground - 22], [161, ground - 22]]), '#ffffff', { ol: '#c9b88c' });
  } else if (kind === 'field' || kind === 'river' || kind === 'salt') {
    pen.fill(pen.ellipse(40, ground + 6, 90, 26), grass[2], flat);
    pen.fill(pen.ellipse(150, ground + 10, 100, 30), grass[0], flat);
    pen.fill(pen.ellipse(150, ground + 10, 100, 30), grass[0], flat);
    for (let i = 0; i < 120; i++) {
      const tuftX = floor(rnd() * width);
      const tuftY = ground - 22 + floor(rnd() * 22);
      pen.dot(tuftX, tuftY, i % 2 ? grass[1] : grass[2]);
    }
    if (theme.draw === 'orchard') {
      for (let treeX = 10; treeX < width; treeX += 30) {
        const treeY = ground - 6 - ((treeX / 30) % 2) * 6;
        pen.fill(pen.rect(treeX - 2, treeY - 12, 4, 14), '#8d5524', { ol: OUTLINE });
        pen.fill(pen.ellipse(treeX, treeY - 18, 12, 10), '#4caf50', { ol: '#2e5a3a', wide: true });
        pen.fill(pen.ellipse(treeX - 4, treeY - 21, 6, 4), '#6fcf63', flat);
        for (let apple = 0; apple < 6; apple++) pen.fill(pen.ellipse(treeX - 8 + floor(rnd() * 16), treeY - 24 + floor(rnd() * 12), 1.6, 1.6), '#e63946', { ol: '#8b1e2b' });
      }
    }
    if (theme.draw === 'river') {
      pen.fill(pen.poly([[0, ground - 20], [width, ground - 26], [width, ground - 2], [0, ground - 4]]), '#4fb3e8', flat);
      pen.fill(pen.rect(0, ground - 20, width, 1), '#bfe6ff', flat);
      for (let i = 0; i < 30; i++) pen.fill(pen.rect(floor(rnd() * width), ground - 18 + floor(rnd() * 14), 3, 1), '#bfe6ff', flat);
      pen.fill(pen.poly([[88, ground - 6], [92, ground - 24], [104, ground - 30], [118, ground - 24], [122, ground - 6]]), '#7a8a7a', { ol: '#4a5a5a', wide: true });
      pen.fill(pen.ellipse(104, ground - 27, 6, 3), '#9aa7a7', flat);
      pen.fill(pen.ellipse(106, ground - 32, 3, 3), '#4caf50', { ol: '#2e5a3a' });
    }
    if (theme.draw === 'saltfield') {
      for (let pondY = ground - 26; pondY < ground - 2; pondY += 8) {
        for (let pondX = 0; pondX < width; pondX += 24) {
          pen.fill(pen.rect(pondX + 1, pondY + 1, 22, 6), (pondX / 24 + pondY) % 2 ? '#ffe9c2' : '#ffd9a8', flat);
          pen.fill(pen.rect(pondX, pondY, 24, 1), '#8d6e63', flat);
          pen.fill(pen.rect(pondX, pondY, 1, 8), '#8d6e63', flat);
        }
      }
      for (let i = 0; i < 20; i++) pen.dot(floor(rnd() * width), ground - 25 + floor(rnd() * 22), '#ffffff');
      ([[26, ground - 30], [150, ground - 34]] as const).forEach(([signX, signY]) => {
        pen.fill(pen.rect(signX, signY, 2, 12), '#8d5524', { ol: OUTLINE });
        pen.fill(pen.rect(signX - 4, signY - 6, 10, 7), '#9aa7b5', { ol: OUTLINE });
      });
      pen.fill(pen.ellipse(96, 30, 9, 9), '#ffe29a', { hi: 0.1, sh: 0, outline: false });
    }
  } else if (kind === 'forest' || kind === 'night') {
    pen.fill(pen.ellipse(96, ground + 10, 120, 32), grass[2], flat);
    if (theme.draw === 'pine') {
      for (let trunkX = 6; trunkX < width; trunkX += 14 + floor(rnd() * 6)) {
        const height = 40 + floor(rnd() * 16);
        pen.fill(pen.rect(trunkX, ground - height, 4, height), '#b5651d', { ol: '#6b3e1a' });
        pen.fill(pen.rect(trunkX + 1, ground - height, 1, height), '#d98b3a', flat);
        for (let layer = 0; layer < 4; layer++) {
          const branchY = ground - height + layer * 7;
          const spread = 5 + layer * 2;
          pen.fill(pen.ellipse(trunkX + 2, branchY, spread, 3), layer % 2 ? '#2e5a3a' : '#3f7a4a', flat);
        }
      }
      for (let i = 0; i < 24; i++) pen.fill(pen.rect(floor(rnd() * width), ground - 30 + floor(rnd() * 20), 6 + floor(rnd() * 10), 1), 'rgba(255,255,255,.5)', flat);
    }
    if (theme.draw === 'firefly') {
      ([[16, 44], [50, 56], [86, 48], [122, 60], [158, 50], [186, 42]] as const).forEach(([centerX, height]) => {
        pen.fill(pen.poly([[centerX - 16, ground], [centerX - 8, ground - height * 0.6], [centerX, ground - height], [centerX + 8, ground - height * 0.6], [centerX + 16, ground]]), '#0f2e22', flat);
        pen.fill(pen.poly([[centerX - 10, ground], [centerX - 4, ground - height * 0.55], [centerX + 1, ground - height * 0.85], [centerX + 6, ground - height * 0.55], [centerX + 10, ground]]), '#163a2a', flat);
      });
      for (let i = 0; i < 60; i++) {
        const glowX = floor(rnd() * width);
        const glowY = 10 + floor(rnd() * 80);
        const color = i % 3 ? '#c6ff6b' : '#fff59d';
        pen.dot(glowX, glowY, color);
        if (i % 3 === 0) {
          pen.dot(glowX + 1, glowY, '#8bc34a');
          pen.dot(glowX - 1, glowY, '#8bc34a');
          pen.dot(glowX, glowY + 1, '#8bc34a');
          pen.dot(glowX, glowY - 1, '#8bc34a');
        }
      }
    }
  } else if (kind === 'snow') {
    pen.fill(pen.ellipse(40, ground + 6, 90, 26), '#dbe7f1', flat);
    pen.fill(pen.ellipse(150, ground + 10, 100, 30), '#eef4fa', flat);
    for (let i = 0; i < 60; i++) pen.dot(floor(rnd() * width), floor(rnd() * ground), '#ffffff');
    if (theme.draw === 'icelake') {
      pen.fill(pen.poly([[0, ground - 22], [width, ground - 26], [width, ground - 2], [0, ground - 4]]), '#cfe8f7', flat);
      for (let i = 0; i < 16; i++) pen.fill(pen.rect(floor(rnd() * width), ground - 20 + floor(rnd() * 16), 6 + floor(rnd() * 10), 1), '#9fc5e8', flat);
      ([[40, '#e63946'], [96, '#1e88e5'], [152, '#f4c542']] as const).forEach(([tentX, color]) => {
        pen.fill(pen.poly([[tentX, ground - 26], [tentX + 12, ground - 8], [tentX - 12, ground - 8]]), color, { ol: OUTLINE, wide: true });
        pen.fill(pen.poly([[tentX, ground - 16], [tentX + 4, ground - 8], [tentX - 4, ground - 8]]), darken(color, 0.4), flat);
      });
    }
    if (theme.draw === 'punchbowl') {
      for (let shrubX = 0; shrubX < width; shrubX += 22) {
        pen.fill(pen.ellipse(shrubX + 8, ground - 10, 9, 7), '#eef4fa', { ol: '#b9cde0' });
        pen.fill(pen.rect(shrubX + 7, ground - 4, 2, 6), '#8d5524', flat);
      }
    }
  }
  // 근경: 덤불 · 바위 · 흙길
  if (!snow) {
    for (let bushX = -4; bushX < width; bushX += 26 + floor(rnd() * 10)) {
      const bushY = ground - 2;
      ([[0, 0, 9, 6], [-7, 2, 6, 4], [7, 2, 6, 4]] as const).forEach(([offsetX, offsetY, radiusX, radiusY]) =>
        pen.fill(pen.ellipse(bushX + offsetX, bushY + offsetY, radiusX, radiusY), bush[0], { ol: darken(bush[0], 0.35) }));
      pen.fill(pen.ellipse(bushX - 2, bushY - 3, 4, 2), bush[1], flat);
    }
  }
  ([[30, ground + 8], [164, ground + 14]] as const).forEach(([rockX, rockY]) => {
    pen.fill(pen.ellipse(rockX, rockY, 6, 3.5), snow ? '#c5d6e4' : '#9aa7a7', { ol: '#5a6a6a', wide: true });
    pen.fill(pen.ellipse(rockX - 2, rockY - 1.5, 2.5, 1), '#ffffff', flat);
  });
  if (!snow) {
    pen.fill(pen.poly([[70, ground + 24], [122, ground + 24], [110, ground + 2], [84, ground + 2]]), night ? '#5a4a3a' : '#d9b27a', flat);
    for (let i = 0; i < 30; i++) pen.dot(76 + floor(rnd() * 44), ground + 4 + floor(rnd() * 20), night ? '#6b5a48' : '#c49a62');
  }
  // 땅 질감
  for (let i = 0; i < 80; i++) {
    const grainX = floor(rnd() * width);
    const grainY = ground + 1 + floor(rnd() * 23);
    if (grainX > 70 && grainX < 122 && !snow) continue;
    pen.dot(grainX, grainY, i % 2 ? grass[1] : grass[2]);
  }
}

export type { Point };
