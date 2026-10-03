/**
 * 픽셀 페인터(프로토타입 Painter 이전본). 도형(타원·다각형·굵은 선)을 격자 마스크로 찍고,
 * 도형마다 하이라이트(좌상단 림)·음영(우하단 림)·외곽선을 자동으로 입힌다. 캔버스와 무관한 순수 데이터(px 배열)라 테스트할 수 있다.
 */
import { darken, lighten } from './color';

export type Mask = Uint8Array;
export type Point = readonly [number, number];

export interface FillStyle {
  /** 명암 없이 한 색 */
  flat?: boolean;
  /** false 면 외곽선 없음 */
  outline?: boolean;
  /** 외곽선 색(기본: 주색을 62% 어둡게) */
  ol?: string;
  /** 음영을 2px 폭으로 */
  wide?: boolean;
  /** 하이라이트 비율(기본 .25) */
  hi?: number;
  /** 음영 비율(기본 .3) */
  sh?: number;
}

/** 캔버스를 만드는 함수 — 브라우저는 document.createElement, 테스트는 가짜. */
export type CanvasFactory = (width: number, height: number) => HTMLCanvasElement;

export const browserCanvas: CanvasFactory = (width, height) => {
  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  return canvas;
};

export class Painter {
  readonly width: number;
  readonly height: number;
  readonly px: (string | null)[];

  constructor(width: number, height: number) {
    this.width = width;
    this.height = height;
    this.px = new Array<string | null>(width * height).fill(null);
  }

  mask(): Mask {
    return new Uint8Array(this.width * this.height);
  }

  ellipse(centerX: number, centerY: number, radiusX: number, radiusY: number): Mask {
    const mask = this.mask();
    for (let row = Math.max(0, Math.floor(centerY - radiusY)); row <= Math.min(this.height - 1, Math.ceil(centerY + radiusY)); row++) {
      for (let col = Math.max(0, Math.floor(centerX - radiusX)); col <= Math.min(this.width - 1, Math.ceil(centerX + radiusX)); col++) {
        const offsetX = (col + 0.5 - centerX) / radiusX;
        const offsetY = (row + 0.5 - centerY) / radiusY;
        if (offsetX * offsetX + offsetY * offsetY <= 1) mask[row * this.width + col] = 1;
      }
    }
    return mask;
  }

  rect(left: number, top: number, rectWidth: number, rectHeight: number): Mask {
    const mask = this.mask();
    for (let row = Math.max(0, top); row < Math.min(this.height, top + rectHeight); row++) {
      for (let col = Math.max(0, left); col < Math.min(this.width, left + rectWidth); col++) mask[row * this.width + col] = 1;
    }
    return mask;
  }

  poly(points: readonly Point[]): Mask {
    const mask = this.mask();
    const ys = points.map(point => point[1]);
    const firstRow = Math.max(0, Math.floor(Math.min(...ys)));
    const lastRow = Math.min(this.height - 1, Math.ceil(Math.max(...ys)));
    for (let row = firstRow; row <= lastRow; row++) {
      const scanY = row + 0.5;
      const crossings: number[] = [];
      for (let i = 0; i < points.length; i++) {
        const [fromX, fromY] = points[i];
        const [toX, toY] = points[(i + 1) % points.length];
        if ((fromY <= scanY && toY > scanY) || (toY <= scanY && fromY > scanY)) crossings.push(fromX + (scanY - fromY) * (toX - fromX) / (toY - fromY));
      }
      crossings.sort((left, right) => left - right);
      for (let i = 0; i + 1 < crossings.length; i += 2) {
        for (let col = Math.max(0, Math.round(crossings[i])); col < Math.min(this.width, Math.round(crossings[i + 1])); col++) mask[row * this.width + col] = 1;
      }
    }
    return mask;
  }

  line(fromX: number, fromY: number, toX: number, toY: number, thickness: number): Mask {
    const deltaX = toX - fromX;
    const deltaY = toY - fromY;
    const length = Math.hypot(deltaX, deltaY) || 1;
    const normalX = -deltaY / length * thickness / 2;
    const normalY = deltaX / length * thickness / 2;
    const mask = this.poly([[fromX + normalX, fromY + normalY], [toX + normalX, toY + normalY], [toX - normalX, toY - normalY], [fromX - normalX, fromY - normalY]]);
    const startCap = this.ellipse(fromX, fromY, thickness / 2, thickness / 2);
    const endCap = this.ellipse(toX, toY, thickness / 2, thickness / 2);
    for (let i = 0; i < mask.length; i++) if (startCap[i] || endCap[i]) mask[i] = 1;
    return mask;
  }

  /** 마스크를 칠한다. color 가 null 이면 지운다. */
  fill(mask: Mask, color: string | null, style: FillStyle = {}): void {
    if (!color) {
      for (let i = 0; i < mask.length; i++) if (mask[i]) this.px[i] = null;
      return;
    }
    const gridWidth = this.width;
    const gridHeight = this.height;
    const inside = (col: number, row: number) => col >= 0 && row >= 0 && col < gridWidth && row < gridHeight && mask[row * gridWidth + col];
    const highlight = lighten(color, style.hi ?? 0.25);
    const shade = darken(color, style.sh ?? 0.3);
    const softShade = darken(color, (style.sh ?? 0.3) * 0.5);
    const outlineColor = style.ol || darken(color, 0.62);
    for (let row = 0; row < gridHeight; row++) {
      for (let col = 0; col < gridWidth; col++) {
        const index = row * gridWidth + col;
        if (!mask[index]) continue;
        let shaded = color;
        if (!style.flat) {
          if (!inside(col + 1, row + 1)) shaded = shade;
          else if (style.wide && !inside(col + 2, row + 2)) shaded = softShade;
          else if (!inside(col - 1, row - 1)) shaded = highlight;
        }
        if (style.outline !== false && (!inside(col - 1, row) || !inside(col + 1, row) || !inside(col, row - 1) || !inside(col, row + 1))) shaded = outlineColor;
        this.px[index] = shaded;
      }
    }
  }

  dot(atX: number, atY: number, color: string): void {
    const col = Math.round(atX);
    const row = Math.round(atY);
    if (col >= 0 && row >= 0 && col < this.width && row < this.height) this.px[row * this.width + col] = color;
  }

  /** 칠한 영역의 가장자리를 한 색 외곽선으로. */
  silhouette(color = '#15121c'): void {
    const gridWidth = this.width;
    const gridHeight = this.height;
    const before = this.px.slice();
    const empty = (col: number, row: number) => col < 0 || row < 0 || col >= gridWidth || row >= gridHeight || !before[row * gridWidth + col];
    for (let row = 0; row < gridHeight; row++) {
      for (let col = 0; col < gridWidth; col++) {
        const index = row * gridWidth + col;
        if (!before[index]) continue;
        if (empty(col - 1, row) || empty(col + 1, row) || empty(col, row - 1) || empty(col, row + 1)) this.px[index] = color;
      }
    }
  }

  /** 픽셀 하나를 scale×scale 블록으로 그린 캔버스. */
  toCanvas(scale: number, createCanvas: CanvasFactory): HTMLCanvasElement {
    const canvas = createCanvas(this.width * scale, this.height * scale);
    const context = canvas.getContext('2d');
    if (!context) return canvas;
    for (let row = 0; row < this.height; row++) {
      for (let col = 0; col < this.width; col++) {
        const color = this.px[row * this.width + col];
        if (color) {
          context.fillStyle = color;
          context.fillRect(col * scale, row * scale, scale, scale);
        }
      }
    }
    return canvas;
  }
}
