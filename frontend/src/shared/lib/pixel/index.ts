/** 앱 전체가 쓰는 픽셀 렌더러 하나(브라우저 캔버스 + 스프라이트 시트). */
import { browserCanvas } from './painter';
import { PixelRenderer } from './renderer';
import { BrowserSprites } from './sprites';

export const sprites = new BrowserSprites();
export const pixelRenderer = new PixelRenderer(browserCanvas, sprites);

// 시트가 로드되면 그 전에(시트 없이) 그린 그림을 버린다 — 구독 컴포넌트가 다시 그린다
sprites.subscribe(() => pixelRenderer.clear());

export type { Equipment, Gender } from './renderer';
export type { ClientSlot, PixelItem } from './looks';
