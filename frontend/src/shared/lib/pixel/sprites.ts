/**
 * 기본 캐릭터 스프라이트(남·여 여행자 시트 PNG) 로더. 로드되면 렌더러 캐시를 비우고 구독자에게 알린다
 * (로드 전에는 스프라이트 없이 장비만 그린다 — 프로토타입과 같다).
 */
import femaleSheet from './sprites/f.png';
import maleSheet from './sprites/m.png';
import type { Gender, SpriteSource } from './renderer';

const SOURCES: Readonly<Record<Gender, string>> = { m: maleSheet, f: femaleSheet };

export class BrowserSprites implements SpriteSource {
  private readonly images = new Map<Gender, HTMLImageElement>();
  private readonly listeners = new Set<() => void>();
  private loadedCount = 0;

  /** 두 시트를 미리 받는다. */
  preload(): void {
    (Object.keys(SOURCES) as Gender[]).forEach(gender => this.load(gender));
  }

  image(gender: Gender): CanvasImageSource | null {
    const image = this.load(gender);
    return image.complete && image.naturalWidth ? image : null;
  }

  /** useSyncExternalStore 용: 로드된 시트 수가 바뀌면 알린다. */
  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  };

  version = (): number => this.loadedCount;

  private load(gender: Gender): HTMLImageElement {
    const cached = this.images.get(gender);
    if (cached) return cached;
    const image = new Image();
    image.onload = () => {
      this.loadedCount += 1;
      this.listeners.forEach(listener => listener());
    };
    image.src = SOURCES[gender];
    this.images.set(gender, image);
    return image;
  }
}
