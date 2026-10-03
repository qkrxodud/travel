import { afterEach, describe, expect, it } from 'vitest';
import { CATALOG } from '../../../test/fixtures';
import { MapEngine, type RegionPaint } from './mapEngine';

const LOOK_A = '<g class="char a"></g>';
const LOOK_B = '<g class="char b"></g>';

type TransitionNode = Element & { __transition?: Record<string, unknown> };

function mount() {
  const card = document.createElement('div');
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  const tip = document.createElement('div');
  card.append(svg, tip);
  document.body.append(card);
  const clicks: string[] = [];
  const engine = new MapEngine(svg, tip, card, CATALOG.features, { onRegionClick: code => clicks.push(code), describe: code => code });
  const charpos = () => svg.querySelector('g.charpos') as TransitionNode;
  const transitions = () => Object.keys(charpos().__transition ?? {});
  return { engine, svg, charpos, transitions, clicks };
}

const paint = (mine: string[], selected: string | null = null): RegionPaint =>
  ({ mine: new Set(mine), selected, highlight: null, claimColor: new Map(), claimer: new Map() });

afterEach(() => {
  document.body.innerHTML = '';
});

describe('지도 엔진 — 캐릭터 이동', () => {
  it('처음에는 바로 서고, 목적지가 바뀌면 이동을 한 번 시작한다', () => {
    const { engine, charpos, transitions } = mount();
    engine.placeCharacter('11010', LOOK_A, true);
    expect(charpos().getAttribute('transform')).toMatch(/^translate\(/);
    expect(transitions()).toHaveLength(0);
    engine.placeCharacter('37430', LOOK_A, true);
    expect(transitions()).toHaveLength(1);
  });

  it('같은 목적지로 이동 중이면 재렌더가 와도 다시 걸지 않는다(재시작 금지)', () => {
    const { engine, transitions } = mount();
    engine.placeCharacter('11010', LOOK_A, true);
    engine.placeCharacter('37430', LOOK_A, true);
    const [moving] = transitions();
    for (let i = 0; i < 5; i++) engine.placeCharacter('37430', LOOK_A, true);
    expect(transitions()).toEqual([moving]);
  });

  it('이동 중 캐릭터 그림이 바뀌어도 이동을 끊지 않고 도착 뒤로 미룬다', () => {
    const { engine, svg, transitions } = mount();
    engine.placeCharacter('11010', LOOK_A, true);
    engine.placeCharacter('37430', LOOK_A, true);
    const [moving] = transitions();
    engine.placeCharacter('37430', LOOK_B, true);
    expect(transitions()).toEqual([moving]);
    expect(svg.querySelector('g.charpos g')?.innerHTML).toBe(LOOK_A);
  });

  it('다른 곳으로 다시 출발하면 새 이동이 이어받는다', () => {
    const { engine, transitions } = mount();
    engine.placeCharacter('11010', LOOK_A, true);
    engine.placeCharacter('37430', LOOK_A, true);
    const [first] = transitions();
    engine.placeCharacter('31370', LOOK_A, true);
    const after = transitions();
    expect(after).not.toContain(first);
    expect(after).toHaveLength(1);
  });

  it('이동 도중 새 목적지로 다시 출발하면 출발 지역이 아니라 지금 위치에서 이어 간다(순간이동 없음)', async () => {
    const { engine, charpos } = mount();
    const at = () => {
      const match = /translate\(([-\d.e]+),\s*([-\d.e]+)\)/.exec(charpos().getAttribute('transform') ?? '');
      return match ? [Number(match[1]), Number(match[2])] as const : null;
    };
    engine.placeCharacter('11010', LOOK_A, true);
    const origin = at();
    engine.placeCharacter('37430', LOOK_A, true);
    await new Promise(resolve => setTimeout(resolve, 400));
    const midway = at();
    engine.placeCharacter('31370', LOOK_A, true);
    await new Promise(resolve => setTimeout(resolve, 60));
    const resumed = at();
    expect(origin && midway && resumed).toBeTruthy();
    if (!origin || !midway || !resumed) return;
    const distance = (left: readonly number[], right: readonly number[]) => Math.hypot(left[0] - right[0], left[1] - right[1]);
    expect(distance(origin, midway), '첫 이동이 진행됐다').toBeGreaterThan(5);
    // 이어서 출발했으면 새 위치는 중간 지점 근처, 출발 지역에서 다시 시작했으면 출발 지역 근처
    expect(distance(midway, resumed)).toBeLessThan(distance(origin, resumed));
  });

  it('두 번째 이동 중에도 캐릭터 그림 교체는 도착 뒤로 미룬다', async () => {
    const { engine, svg } = mount();
    engine.placeCharacter('11010', LOOK_A, true);
    engine.placeCharacter('37430', LOOK_A, true);
    await new Promise(resolve => setTimeout(resolve, 100)); // 첫 이동이 실제로 달리는 중(interrupt 콜백이 불린다)
    engine.placeCharacter('31370', LOOK_A, true);
    engine.placeCharacter('31370', LOOK_B, true);
    expect(svg.querySelector('g.charpos g')?.innerHTML).toBe(LOOK_A);
  });

  it('영토가 없으면 숨긴다', () => {
    const { engine, charpos } = mount();
    engine.placeCharacter(null, LOOK_A, true);
    expect(charpos().getAttribute('display')).toBe('none');
  });
});

describe('지도 엔진 — 지역 칠하기', () => {
  it('내 영토·선택·전설·data-code 를 단다(바뀐 게 없으면 DOM 을 건드리지 않는다)', () => {
    const { engine, svg, clicks } = mount();
    const region = (code: string) => svg.querySelector(`path.region[data-code="${code}"]`) as SVGPathElement;
    expect(region('37430').classList.contains('legend')).toBe(true);
    engine.setPaint(paint(['11010'], '31370'));
    expect(region('11010').classList.contains('on')).toBe(true);
    expect(region('31370').classList.contains('focus')).toBe(true);
    region('11010').style.fill = 'red'; // 칠하기를 다시 돌리면 인라인 fill 이 지워진다
    engine.setPaint(paint(['11010'], '31370'));
    expect(region('11010').style.fill).toBe('red');
    engine.setPaint(paint(['11010', '11020'], '31370'));
    expect(region('11010').style.fill).toBe('');
    region('31370').dispatchEvent(new MouseEvent('click', { bubbles: true }));
    expect(clicks).toEqual(['31370']);
  });
});
