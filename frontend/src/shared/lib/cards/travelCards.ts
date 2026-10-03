/**
 * 화면에서 바로 그리는 자랑 카드(1200×630 PNG data URL) — 프로토타입 recentCard(여행 카드)·vsCard(비교 카드) 이전본.
 * (영토·최근 여행·연간 리캡 카드는 서버가 그린 PNG 를 쓴다 — 프로필 탭.)
 */
import { geoMercator, geoPath, type GeoPermissibleObjects } from 'd3';
import type { PixelItem } from '../pixel';
import { TIER_LABEL, type Catalog, type RegionFeature } from '../region/catalog';

const WIDTH = 1200;
const HEIGHT = 630;
const MAP_EXTENT: [[number, number], [number, number]] = [[50, 50], [560, 580]];

export interface CardCanvas {
  create: (width: number, height: number) => HTMLCanvasElement;
  /** 아이템 아이콘 캔버스(픽셀 렌더러) */
  itemCanvas: (item: PixelItem) => CanvasImageSource;
}

const font = (size: number, display = false, weight = 400) =>
  `${weight} ${size}px ${display ? '"Do Hyeon", "Noto Sans KR"' : '"Noto Sans KR"'}, sans-serif`;

function blankCard(canvas: CardCanvas): [HTMLCanvasElement, CanvasRenderingContext2D] | null {
  const card = canvas.create(WIDTH, HEIGHT);
  const context = card.getContext('2d');
  if (!context) return null;
  context.fillStyle = '#10211f';
  context.fillRect(0, 0, WIDTH, HEIGHT);
  context.strokeStyle = 'rgba(255,255,255,.05)';
  context.lineWidth = 1;
  for (let i = 0; i < WIDTH; i += 40) { context.beginPath(); context.moveTo(i, 0); context.lineTo(i, HEIGHT); context.stroke(); }
  for (let i = 0; i < HEIGHT; i += 40) { context.beginPath(); context.moveTo(0, i); context.lineTo(WIDTH, i); context.stroke(); }
  context.textBaseline = 'top';
  return [card, context];
}

function drawMap(context: CanvasRenderingContext2D, features: RegionFeature[], fillOf: (code: string) => string | null, highlight?: RegionFeature): void {
  const collection = { type: 'FeatureCollection', features } as unknown as GeoPermissibleObjects;
  const draw = geoPath(geoMercator().fitExtent(MAP_EXTENT, collection), context);
  features.forEach(feature => {
    context.beginPath();
    draw(feature as unknown as GeoPermissibleObjects);
    context.fillStyle = fillOf(feature.properties.code) || '#1f3330';
    context.fill();
    context.strokeStyle = '#10211f';
    context.lineWidth = 0.8;
    context.stroke();
  });
  if (highlight) {
    context.beginPath();
    draw(highlight as unknown as GeoPermissibleObjects);
    context.strokeStyle = '#ffd166';
    context.lineWidth = 3;
    context.stroke();
    const [centerX, centerY] = draw.centroid(highlight as unknown as GeoPermissibleObjects);
    context.beginPath();
    context.arc(centerX, centerY, 16, 0, Math.PI * 2);
    context.strokeStyle = '#ffd166';
    context.lineWidth = 2;
    context.stroke();
  }
}

function footer(context: CanvasRenderingContext2D): void {
  context.font = font(20);
  context.fillStyle = '#5f8f87';
  context.fillText('territory.kr/u/kobi', 640, 560);
  context.fillStyle = '#2fc3ad';
  context.fillRect(640, 548, 4, 34);
}

export interface RecentCardInput {
  catalog: Catalog;
  code: string;
  /** 내 방문(화면 코드 → 날짜·메모·처리 시각) */
  visits: ReadonlyMap<string, { date: string; memo: string; at: number }>;
  /** 이 지역 세트(모은 수) */
  sets: readonly { name: string; have: number; total: number }[];
  item: PixelItem | null;
  topPercent: string;
  /** 서버 정복률(개인 지도 GET /territory conquest.percent). 없으면(공유 지도) 내 방문 수 / 전체 지역 수 */
  conquestPercent?: number | null;
}

/** 여행 카드(체크인 모달 "기록하고 카드" · 선택한 지역 "여행 카드") */
export function recentCard(canvas: CardCanvas, input: RecentCardInput): string | null {
  const feature = input.catalog.byCode.get(input.code);
  const drawn = blankCard(canvas);
  if (!feature || !drawn) return null;
  const [card, context] = drawn;
  const visit = input.visits.get(input.code);
  const tier = feature.properties.tier;
  const ordered = [...input.visits.entries()].sort((left, right) => left[1].at - right[1].at);
  const nth = ordered.findIndex(([code]) => code === input.code) + 1;
  drawMap(context, input.catalog.features, code => (input.visits.has(code) ? (code === input.code ? '#ffd166' : '#2fc3ad') : null), feature);
  context.font = font(22, false, 500); context.fillStyle = '#8fb3ad'; context.fillText(`${nth}번째 영토 · ${visit?.date || ''}`, 640, 70);
  context.font = font(96, true); context.fillStyle = tier === 'legend' ? '#d7b3ff' : '#fff'; context.fillText(feature.properties.name, 634, 105);
  context.font = font(28, false, 500); context.fillStyle = '#cfe6e1';
  context.fillText(`${feature.properties.prov} · ${TIER_LABEL[tier]} 지역 · +${input.catalog.xpByTier[tier]} XP`, 640, 220);
  let lineY = 290;
  if (visit?.memo) { context.font = font(30, true); context.fillStyle = '#ffd166'; context.fillText(`“${visit.memo}”`, 640, lineY); lineY += 60; }
  input.sets.forEach(set => {
    context.font = font(22); context.fillStyle = '#8fb3ad'; context.fillText(`${set.name} 세트`, 640, lineY);
    context.font = font(30, true); context.fillStyle = '#fff'; context.fillText(`${set.have} / ${set.total}${set.have === set.total ? ' 완성' : ''}`, 880, lineY - 4);
    lineY += 48;
  });
  if (input.item) {
    context.imageSmoothingEnabled = false;
    context.drawImage(canvas.itemCanvas(input.item), 1000, 60, 150, 150);
  }
  const percent = input.conquestPercent ?? Math.round(100 * input.visits.size / input.catalog.features.length);
  context.font = font(22); context.fillStyle = '#8fb3ad'; context.fillText(`전국 ${percent}% · 상위 ${input.topPercent}%`, 640, Math.max(lineY, 470));
  footer(context);
  return card.toDataURL('image/png');
}

export interface VsCardInput {
  catalog: Catalog;
  otherName: string;
  /** 화면 코드 */
  onlyMine: readonly string[];
  both: readonly string[];
  onlyTheirs: readonly string[];
}

/** 영토 비교 카드(랭킹 탭 "비교 카드 만들기") — 서버 비교 결과로 그린다 */
export function vsCard(canvas: CardCanvas, input: VsCardInput): string | null {
  const drawn = blankCard(canvas);
  if (!drawn) return null;
  const [card, context] = drawn;
  const mine = new Set([...input.onlyMine, ...input.both]);
  const theirs = new Set([...input.onlyTheirs, ...input.both]);
  drawMap(context, input.catalog.features, code => {
    const inMine = mine.has(code);
    const inTheirs = theirs.has(code);
    return inMine && inTheirs ? '#b48af0' : inMine ? '#2fc3ad' : inTheirs ? '#ffd166' : null;
  });
  context.font = font(22, false, 500); context.fillStyle = '#8fb3ad'; context.fillText('영토 전쟁 · 모든 지도 기준', 640, 70);
  context.font = font(84, true); context.fillStyle = '#2fc3ad'; context.fillText(`${mine.size}`, 634, 105);
  context.fillStyle = '#5f8f87'; context.font = font(40, true); context.fillText('vs', 800, 130);
  context.fillStyle = '#ffd166'; context.font = font(84, true); context.fillText(`${theirs.size}`, 880, 105);
  context.font = font(24); context.fillStyle = '#cfe6e1'; context.fillText('KOBI', 640, 200); context.fillText(input.otherName, 880, 200);
  ([['나만 간 곳', input.onlyMine.length, '#2fc3ad'], ['둘 다 간 곳', input.both.length, '#b48af0'], [`${input.otherName}만 간 곳`, input.onlyTheirs.length, '#ffd166']] as const)
    .forEach(([label, count, color], i) => {
      const rowY = 280 + i * 58;
      context.fillStyle = color; context.fillRect(640, rowY + 8, 14, 14);
      context.font = font(22); context.fillStyle = '#8fb3ad'; context.fillText(label, 668, rowY);
      context.font = font(34, true); context.fillStyle = '#fff'; context.fillText(`${count}곳`, 900, rowY - 6);
    });
  footer(context);
  return card.toDataURL('image/png');
}
