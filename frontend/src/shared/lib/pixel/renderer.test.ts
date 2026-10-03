/**
 * 픽셀 캐릭터 — 옷차림(착용 조합)마다 한 번만 그려 다시 쓰고, 아이템은 칸에 맞는 모양으로 그린다. 모양·색은 서버 카탈로그 값이다.
 * 이야기 순서: 캐릭터 그림 다시 쓰기 → 픽셀 그리기 → 아이템 모양.
 */
import { describe, expect, it, vi } from 'vitest';
import { Painter } from './painter';
import { equipmentKey, PixelRenderer, type Equipment, type SpriteSource } from './renderer';
import { backgroundThemeKey, lookOf, type PixelItem } from './looks';

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

describe('캐릭터 그림 다시 쓰기', () => {
  it('같은 옷차림이면 다시 그리지 않고 같은 그림을 준다 — 내용이 같은 새 응답이 와도', () => {
    const { factory } = fakeCanvasFactory();
    const renderer = new PixelRenderer(factory, sprites(true));
    const first = renderer.character({ gender: 'm', hand: lantern, props: [] });
    const calls = factory.mock.calls.length;
    expect(renderer.character({ gender: 'm', hand: { ...lantern }, props: [] })).toBe(first);
    expect(factory.mock.calls.length).toBe(calls);
  });

  it('옷차림이나 성별이 바뀌면 새로 그린다', () => {
    const renderer = new PixelRenderer(fakeCanvasFactory().factory, sprites(true));
    const first = renderer.character({ gender: 'm', hand: lantern, props: [] });
    expect(renderer.character({ gender: 'm', hand: lantern, hat: beanie, props: [] })).not.toBe(first);
    expect(renderer.character({ gender: 'f', hand: lantern, props: [] })).not.toBe(first);
  });

  it('지도 위 캐릭터 그림도 옷차림마다 한 번만 만든다', () => {
    const { factory } = fakeCanvasFactory();
    const renderer = new PixelRenderer(factory, sprites(true));
    const equipment: Equipment = { gender: 'm', hand: lantern, props: [] };
    const markup = renderer.characterMarkup(equipment);
    expect(renderer.characterMarkup({ ...equipment })).toBe(markup);
    expect(renderer.character(equipment).toDataURL).toHaveBeenCalledTimes(1);
  });

  it('지도 위 캐릭터에는 이름표를 달고, 이름표 없는 그림도 따로 만든다', () => {
    const renderer = new PixelRenderer(fakeCanvasFactory().factory, sprites(true));
    const equipment: Equipment = { gender: 'm', hand: lantern, props: [] };
    expect(renderer.characterMarkup(equipment)).toContain('KOBI');
    expect(renderer.characterMarkup(equipment, true)).not.toContain('KOBI');
  });

  it('캐릭터 몸 그림이 늦게 도착하면 도착 전에 그린 그림을 버리고 다시 그린다', () => {
    const { factory } = fakeCanvasFactory();
    let ready = false;
    const renderer = new PixelRenderer(factory, { image: () => (ready ? ({} as CanvasImageSource) : null) });
    const equipment: Equipment = { gender: 'm', props: [] };
    const before = renderer.character(equipment);
    ready = true;
    expect(renderer.character(equipment)).not.toBe(before);
  });

  it('아이템 아이콘과 꾸민 장면도 다시 쓰고, 비우면 새로 그린다', () => {
    const { factory } = fakeCanvasFactory();
    const renderer = new PixelRenderer(factory, sprites(true));
    expect(renderer.itemUrl(lantern)).toBe(renderer.itemUrl({ ...lantern }));
    const scene = renderer.scene({ gender: 'm', bg: ridge, props: [] });
    expect(renderer.scene({ gender: 'm', bg: { ...ridge }, props: [] })).toBe(scene);
    renderer.clear();
    expect(renderer.scene({ gender: 'm', bg: ridge, props: [] })).not.toBe(scene);
  });

  it('옷차림은 성별·칸마다 입은 아이템·장식 순서로 구별한다', () => {
    expect(equipmentKey({ gender: 'f', hat: beanie, props: [lantern] })).toBe(JSON.stringify(['f', ['region:KR-36330', '', '', '', '', ''], ['region:KR-11010']]));
  });
});

describe('픽셀 그리기', () => {
  it('도형마다 외곽선·밝은 면·그림자를 넣고, 지우면 빈칸이 된다', () => {
    const pen = new Painter(10, 10);
    pen.fill(pen.rect(2, 2, 5, 5), '#808080');
    const colors = new Set(pen.px.filter(Boolean));
    expect(colors.size).toBeGreaterThan(1);
    expect(pen.px[0]).toBeNull();
    pen.fill(pen.rect(2, 2, 5, 5), null);
    expect(pen.px.every(color => color === null)).toBe(true);
  });
});

describe('아이템 모양', () => {
  const themes = (key: string) => (key === 'ridge' ? { sky: ['#87ceeb', '#cfe8f5'], ground: ['#4caf50'] } : { sky: ['#aaaaaa', '#bbbbbb'], ground: ['#cccccc'] });
  const item = (slot: PixelItem['slot'], type: string | null, emoji = '🎁', theme: string | null = null): PixelItem =>
    ({ code: 'x', emoji, slot, theme, look: type ? { type, primary: '#112233', secondary: '#445566' } : null });

  it('칸에 맞는 모양은 서버가 준 모양과 색 그대로 그린다', () => {
    expect(lookOf(item('hat', 'crown'), themes)).toEqual({ type: 'crown', primary: '#112233', secondary: '#445566' });
  });

  it('모자 칸에 모자가 아닌 모양이 오면 같은 색 비니로, 손에 들 수 없는 모양은 지도로 그린다', () => {
    expect(lookOf(item('hat', 'dog'), themes).type).toBe('beanie');
    expect(lookOf(item('hand', 'crown'), themes)).toMatchObject({ type: 'map', primary: '#112233' });
    expect(lookOf(item('badge', 'crown'), themes).type).toBe('keyring');
  });

  it('배낭은 받은 색을 밝은 면과 어두운 면으로 나눠 가방 모양으로 그린다', () => {
    const back = lookOf(item('back', 'crown'), themes);
    expect(back.type).toBe('bag');
    expect(back.primary).not.toBe('#112233');
    expect(back.secondary).not.toBe(back.primary);
  });

  it('모르는 동행·장식 모양이나 모양 정보가 없는 아이템은 칸마다 정해진 기본 모양으로 그린다', () => {
    expect(lookOf(item('pet', 'crown'), themes).type).toBe('dog');
    expect(lookOf(item('prop', 'crown'), themes).type).toBe('vase');
    expect(lookOf(item('hat', null), themes).type).toBe('beanie');
  });

  it('배경은 테마의 하늘·땅 색으로 그리고, 테마가 없으면 이모지로, 그것도 없으면 민무늬로 고른다', () => {
    expect(lookOf(item('bg', null, '🎁', 'ridge'), themes)).toEqual({ type: 'bg', primary: '#87ceeb', secondary: '#4caf50', accent: '#cfe8f5' });
    expect(backgroundThemeKey({ theme: null, emoji: '🌅' })).toBe('sunset');
    expect(backgroundThemeKey({ theme: null, emoji: '🎁' })).toBe('plain');
  });

  it('오라는 이모지마다 정해진 색으로 빛나고, 모르는 이모지면 보라색이다', () => {
    expect(lookOf(item('aura', null, '🌊'), themes).primary).toBe('#29b6f6');
    expect(lookOf(item('aura', null, '🎁'), themes).primary).toBe('#b48af0');
  });
});
