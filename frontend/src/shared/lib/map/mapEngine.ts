/**
 * 지도 엔진(D3) — 지도 SVG 의 자식은 전부 이 엔진이 소유한다(React 는 <svg> 컨테이너만 넘긴다).
 * 마운트 때 한 번 만들고, 데이터가 바뀌면 update 메서드만 부른다. 애니메이션(캐릭터 이동·줌)은 목적지가 바뀔 때만 시작한다 —
 * 같은 목적지로 이동 중이면 재렌더가 와도 다시 걸지 않는다(2026-10-03 수정 건: 재조회마다 이동을 처음부터 다시 걸어 끊겼다).
 */
import {
  easeCubicInOut, geoMercator, geoPath, select, zoom as d3zoom, zoomIdentity,
  type D3ZoomEvent, type GeoPath, type GeoPermissibleObjects, type Selection, type ZoomBehavior,
} from 'd3';
import type { RegionFeature } from '../region/catalog';

export const MAP_WIDTH = 720;
export const MAP_HEIGHT = 774;
const MAX_ZOOM = 9;
const MOVE_MS = 900;
const HOP_HEIGHT = 40;

/** 지역마다 칠하기 상태 */
export interface RegionPaint {
  /** 내 영토(.on) */
  mine: ReadonlySet<string>;
  selected: string | null;
  highlight: string | null;
  /** 공유 지도: 지역 → 선점자 색(인라인 fill) */
  claimColor: ReadonlyMap<string, string>;
  /** 공유 지도: 지역 → 선점자 explorerId(data-claim) */
  claimer: ReadonlyMap<string, string>;
}

export interface MapHandlers {
  onRegionClick: (code: string) => void;
  /** 툴팁 문구 */
  describe: (code: string) => string;
}

type FeatureCollection = { type: 'FeatureCollection'; features: RegionFeature[] };

/** 같은 투영(지도 탭·영토 비교 지도·카드)에서 쓰는 경로 생성기 */
export function createMapPath(features: RegionFeature[]): GeoPath<unknown, GeoPermissibleObjects> {
  const collection: FeatureCollection = { type: 'FeatureCollection', features };
  const projection = geoMercator().fitExtent([[14, 14], [MAP_WIDTH - 14, MAP_HEIGHT - 14]], collection as unknown as GeoPermissibleObjects);
  return geoPath(projection);
}

export class MapEngine {
  private readonly svg: Selection<SVGSVGElement, unknown, null, undefined>;
  private readonly tip: HTMLElement;
  private readonly card: HTMLElement;
  private readonly byCode: Map<string, RegionFeature>;
  private readonly path: GeoPath<unknown, GeoPermissibleObjects>;
  private readonly regions: Selection<SVGPathElement, RegionFeature, SVGGElement, unknown>;
  private readonly pingLayer: Selection<SVGGElement, unknown, null, undefined>;
  private readonly charPos: Selection<SVGGElement, unknown, null, undefined>;
  private readonly charScale: Selection<SVGGElement, unknown, null, undefined>;
  private readonly zoomBehavior: ZoomBehavior<SVGSVGElement, unknown>;
  private handlers: MapHandlers;

  private zoomK = 1;
  private paint: RegionPaint | null = null;
  /** 캐릭터가 서 있는 곳 / 가고 있는 곳 */
  private charAt: string | null = null;
  private charTarget: string | null = null;
  private charLook: string | null = null;
  /** 지금 화면에 그려진 캐릭터 위치(이동 중이면 보간된 위치) — 이동 도중 새 목적지가 오면 여기서 이어서 출발한다 */
  private charXY: [number, number] | null = null;
  private moving = false;
  /** 이동 중에 바뀐 캐릭터 그림 — 도착한 뒤 갈아 끼운다 */
  private pendingLook: string | null = null;

  constructor(svgElement: SVGSVGElement, tip: HTMLElement, card: HTMLElement, features: RegionFeature[], handlers: MapHandlers) {
    this.svg = select(svgElement);
    this.tip = tip;
    this.card = card;
    this.handlers = handlers;
    this.byCode = new Map(features.map(feature => [feature.properties.code, feature]));
    this.path = createMapPath(features);
    const layer = this.svg.append('g');
    this.regions = layer.selectAll<SVGPathElement, RegionFeature>('path').data(features).join('path')
      .attr('class', feature => 'region' + (feature.properties.tier === 'legend' ? ' legend' : ''))
      .attr('d', feature => this.path(feature as unknown as GeoPermissibleObjects))
      .attr('data-code', feature => feature.properties.code)
      .on('click', (_event: MouseEvent, feature) => this.handlers.onRegionClick(feature.properties.code))
      .on('mousemove', (event: MouseEvent, feature) => this.showTip(event, feature.properties.code))
      .on('mouseleave', () => this.hideTip());
    this.pingLayer = layer.append('g') as unknown as Selection<SVGGElement, unknown, null, undefined>;
    this.charPos = layer.append('g').attr('class', 'charpos').style('pointer-events', 'none') as unknown as Selection<SVGGElement, unknown, null, undefined>;
    this.charScale = this.charPos.append('g');
    this.zoomBehavior = d3zoom<SVGSVGElement, unknown>().scaleExtent([1, MAX_ZOOM]).on('zoom', (event: D3ZoomEvent<SVGSVGElement, unknown>) => {
      layer.attr('transform', event.transform.toString());
      this.zoomK = event.transform.k;
      this.charScale.attr('transform', `scale(${1.1 / Math.sqrt(this.zoomK)})`);
    });
    this.svg.call(this.zoomBehavior).on('dblclick.zoom', null);
  }

  /** 클릭·툴팁 콜백 교체(React 재렌더마다 새 클로저) */
  setHandlers(handlers: MapHandlers): void {
    this.handlers = handlers;
  }

  /** 지역 칠하기 상태 반영 — 바뀐 값이 없으면 DOM 을 건드리지 않는다. */
  setPaint(paint: RegionPaint): void {
    const before = this.paint;
    if (before && sameSet(before.mine, paint.mine) && before.selected === paint.selected && before.highlight === paint.highlight
      && sameMap(before.claimColor, paint.claimColor) && sameMap(before.claimer, paint.claimer)) return;
    this.paint = paint;
    this.regions
      .classed('on', feature => paint.mine.has(feature.properties.code))
      .classed('focus', feature => feature.properties.code === paint.selected)
      .classed('hl', feature => feature.properties.code === paint.highlight)
      .classed('claimed', feature => paint.claimColor.has(feature.properties.code))
      .style('fill', feature => paint.claimColor.get(feature.properties.code) ?? null)
      .attr('data-claim', feature => paint.claimer.get(feature.properties.code) ?? null);
  }

  /**
   * 캐릭터를 code(가장 최근 체크인 지역)에 둔다. 처음이면 바로, 목적지가 바뀌면 깡충 이동(0.9초).
   * 이미 그곳에 있거나 그곳으로 가는 중이면 아무것도 하지 않는다. look(캐릭터 그림)은 바뀔 때만 갈아 끼우고, 이동 중이면 도착 뒤로 미룬다.
   */
  placeCharacter(code: string | null, look: string, animate: boolean): void {
    if (look !== this.charLook) {
      if (this.moving) this.pendingLook = look;
      else this.applyLook(look);
    }
    if (!code || !this.byCode.has(code)) {
      this.charPos.interrupt().attr('display', 'none');
      this.charAt = null;
      this.charTarget = null;
      this.charXY = null;
      this.moving = false;
      this.flushLook();
      return;
    }
    this.charScale.attr('transform', `scale(${1.1 / Math.sqrt(this.zoomK)})`);
    this.charPos.attr('display', null);
    if (code === this.charTarget) return;
    const [toX, toY] = this.centroid(code);
    // 출발점 = 지금 그려진 위치. 이동 도중 목적지가 바뀌면 출발 지역이 아니라 보간된 현재 위치에서 이어 간다(순간이동 방지).
    const start = this.charXY;
    const departing = this.moving || this.charAt !== code;
    this.charTarget = code;
    // 먼저 진행 중 전환을 멈춘다 — interrupt 콜백이 moving 을 끄므로 새 이동 표시는 그 뒤에 켠다
    this.charPos.interrupt();
    if (animate && start && departing) {
      const [fromX, fromY] = start;
      this.moving = true;
      this.charPos.transition().duration(MOVE_MS).ease(easeCubicInOut)
        .attrTween('transform', () => (progress: number) => {
          const atX = fromX + (toX - fromX) * progress;
          const atY = fromY + (toY - fromY) * progress - Math.sin(progress * Math.PI) * HOP_HEIGHT;
          this.charXY = [atX, atY];
          return `translate(${atX},${atY})`;
        })
        .on('end', () => {
          this.charAt = code;
          this.charXY = [toX, toY];
          this.moving = false;
          this.flushLook();
        })
        // 다른 곳으로 다시 출발할 때 — 새 전환이 지금 위치(charXY)에서 이어받는다
        .on('interrupt', () => {
          this.moving = false;
        });
    } else {
      this.charPos.attr('transform', `translate(${toX},${toY})`);
      this.charAt = code;
      this.charXY = [toX, toY];
      this.moving = false;
      this.flushLook();
    }
  }

  /** 캐릭터가 지금 서 있는(또는 가는) 지역 */
  get characterRegion(): string | null {
    return this.charAt;
  }

  zoomTo(codes: readonly string[], pad = 0.85): void {
    const features = codes.map(code => this.byCode.get(code)).filter((feature): feature is RegionFeature => !!feature);
    if (!features.length) return;
    const bounds = this.path.bounds({ type: 'FeatureCollection', features } as unknown as GeoPermissibleObjects);
    const spanX = bounds[1][0] - bounds[0][0];
    const spanY = bounds[1][1] - bounds[0][1];
    const centerX = (bounds[0][0] + bounds[1][0]) / 2;
    const centerY = (bounds[0][1] + bounds[1][1]) / 2;
    const scale = Math.max(1, Math.min(MAX_ZOOM, pad / Math.max(spanX / MAP_WIDTH, spanY / MAP_HEIGHT)));
    this.svg.transition().duration(600)
      .call(this.zoomBehavior.transform, zoomIdentity.translate(MAP_WIDTH / 2 - scale * centerX, MAP_HEIGHT / 2 - scale * centerY).scale(scale));
  }

  resetView(): void {
    this.svg.transition().duration(500).call(this.zoomBehavior.transform, zoomIdentity);
  }

  /** 체크인한 곳에 퍼지는 원 */
  ping(code: string): void {
    if (!this.byCode.has(code)) return;
    const [pingX, pingY] = this.centroid(code);
    this.pingLayer.append('circle').attr('class', 'ping').attr('cx', pingX).attr('cy', pingY).attr('r', 2)
      .on('animationend', function removePing(this: SVGCircleElement) {
        this.remove();
      });
  }

  destroy(): void {
    this.charPos.interrupt();
    this.svg.interrupt();
    this.svg.on('.zoom', null);
    this.svg.selectAll('*').remove();
  }

  private centroid(code: string): [number, number] {
    const feature = this.byCode.get(code) as RegionFeature;
    return this.path.centroid(feature as unknown as GeoPermissibleObjects);
  }

  private applyLook(look: string): void {
    this.charScale.html(look);
    this.charLook = look;
    this.pendingLook = null;
  }

  private flushLook(): void {
    if (this.pendingLook !== null && this.pendingLook !== this.charLook) this.applyLook(this.pendingLook);
    this.pendingLook = null;
  }

  private showTip(event: MouseEvent, code: string): void {
    const box = this.card.getBoundingClientRect();
    this.tip.style.left = event.clientX - box.left + 'px';
    this.tip.style.top = event.clientY - box.top + 'px';
    this.tip.textContent = this.handlers.describe(code);
    this.tip.classList.add('show');
  }

  private hideTip(): void {
    this.tip.classList.remove('show');
  }
}

function sameSet(left: ReadonlySet<string>, right: ReadonlySet<string>): boolean {
  if (left === right) return true;
  if (left.size !== right.size) return false;
  for (const value of left) if (!right.has(value)) return false;
  return true;
}

function sameMap(left: ReadonlyMap<string, string>, right: ReadonlyMap<string, string>): boolean {
  if (left === right) return true;
  if (left.size !== right.size) return false;
  for (const [key, value] of left) if (right.get(key) !== value) return false;
  return true;
}
