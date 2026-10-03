import { describe, expect, it, vi } from 'vitest';
import { Painter } from './painter';
import { equipmentKey, PixelRenderer, type Equipment, type SpriteSource } from './renderer';
import type { PixelItem } from './looks';

/** 그리기 호출만 세는 가짜 캔버스(jsdom 에는 2D 컨텍스트가 없다) */
function fakeCanvasFactory() {
  const created: { width: number; height: number; fills: number }[] = [];
  const factory = vi.fn((width: number, height: number) => {
    const record = { width, height, fills: 0 };
    created.push(record);
    const context = {
      imageSmoothingEnabled: true, fillStyle: '',
      fillRect: () => { record.fills += 1; }, drawImage: () => undefined, beginPath: () => undefined, ellipse: () => undefined, fill: () => undefined,
    };
    return { width, height, getContext: () => context, toDataURL: vi.fn(() => `data:${width}x${height}#${created.length}`) } as unknown as HTMLCanvasElement;
  });
  return { factory, created };
}

const sprites = (ready: boolean): SpriteSource => ({ image: () => (ready ? ({} as CanvasImageSource) : null) });
const lantern: PixelItem = { code: 'region:KR-11010', emoji: '🏮', slot: 'hand', look: { type: 'lantern', primary: '#e63946', secondary: '#f4c542' }, theme: null };
const beanie: PixelItem = { code: 'region:KR-36330', emoji: '🌸', slot: 'hat', look: { type: 'beanie', primary: '#f48fb1', secondary: '#ffffff' }, theme: null };
const ridge: PixelItem = { code: 'set:jiri', emoji: '⛰️', slot: 'bg', look: null, theme: 'ridge' };

describe('픽셀 렌더러 메모이즈', () => {
  it('같은 착용 조합이면 다시 그리지 않고 같은 캔버스를 준다', () => {
    const { factory } = fakeCanvasFactory();
    const renderer = new PixelRenderer(factory, sprites(true));
    const equipment: Equipment = { gender: 'm', hand: lantern, props: [] };
    const first = renderer.character(equipment);
    const calls = factory.mock.calls.length;
    // 내용이 같은 새 객체(재조회 응답)도 같은 키
    expect(renderer.character({ gender: 'm', hand: { ...lantern }, props: [] })).toBe(first);
    expect(factory.mock.calls.length).toBe(calls);
    // 조합이 바뀌면 새로 그린다
    expect(renderer.character({ gender: 'm', hand: lantern, hat: beanie, props: [] })).not.toBe(first);
    expect(renderer.character({ gender: 'f', hand: lantern, props: [] })).not.toBe(first);
  });

  it('지도 위 캐릭터 마크업·data URL 도 조합 키로 한 번만 만든다', () => {
    const { factory } = fakeCanvasFactory();
    const renderer = new PixelRenderer(factory, sprites(true));
    const equipment: Equipment = { gender: 'm', hand: lantern, props: [] };
    const markup = renderer.characterMarkup(equipment);
    expect(renderer.characterMarkup({ ...equipment })).toBe(markup);
    const characterCanvas = renderer.character(equipment);
    expect(characterCanvas.toDataURL).toHaveBeenCalledTimes(1);
    expect(markup).toContain('KOBI');
    expect(renderer.characterMarkup(equipment, true)).not.toContain('KOBI');
  });

  it('스프라이트가 로드되기 전에 그린 그림은 로드 뒤 키가 달라 다시 그린다', () => {
    const { factory } = fakeCanvasFactory();
    let ready = false;
    const renderer = new PixelRenderer(factory, { image: () => (ready ? ({} as CanvasImageSource) : null) });
    const equipment: Equipment = { gender: 'm', props: [] };
    const before = renderer.character(equipment);
    ready = true;
    expect(renderer.character(equipment)).not.toBe(before);
  });

  it('아이템 아이콘·장면도 메모이즈(clear 하면 다시)', () => {
    const { factory } = fakeCanvasFactory();
    const renderer = new PixelRenderer(factory, sprites(true));
    expect(renderer.itemUrl(lantern)).toBe(renderer.itemUrl({ ...lantern }));
    const scene = renderer.scene({ gender: 'm', bg: ridge, props: [] });
    expect(renderer.scene({ gender: 'm', bg: { ...ridge }, props: [] })).toBe(scene);
    renderer.clear();
    expect(renderer.scene({ gender: 'm', bg: ridge, props: [] })).not.toBe(scene);
  });

  it('착용 조합 키', () => {
    expect(equipmentKey({ gender: 'f', hat: beanie, props: [lantern] })).toBe(JSON.stringify(['f', ['region:KR-36330', '', '', '', '', ''], ['region:KR-11010']]));
  });
});

describe('페인터', () => {
  it('도형마다 외곽선·하이라이트·음영을 입힌다', () => {
    const pen = new Painter(10, 10);
    pen.fill(pen.rect(2, 2, 5, 5), '#808080');
    const colors = new Set(pen.px.filter(Boolean));
    expect(colors.size).toBeGreaterThan(1);
    expect(pen.px[0]).toBeNull();
    pen.fill(pen.rect(2, 2, 5, 5), null);
    expect(pen.px.every(color => color === null)).toBe(true);
  });
});
