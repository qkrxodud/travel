/**
 * 지도 엔진 — 지도 그림은 엔진이 소유하고 화면은 바뀐 값만 넘긴다. 캐릭터 이동은 목적지가 바뀔 때만 시작한다.
 * 이야기 순서: 캐릭터 이동 → 지도 칠하기 → 시·도 정복 테두리 → 이번 주 미스터리 마커 → 지도 만지기.
 */
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
  const mysteryClicks: string[] = [];
  const engine = new MapEngine(svg, tip, card, CATALOG.features, { onRegionClick: code => clicks.push(code), onMysteryClick: code => mysteryClicks.push(code), describe: code => code });
  const charpos = () => svg.querySelector('g.charpos') as TransitionNode;
  const transitions = () => Object.keys(charpos().__transition ?? {});
  return { engine, svg, tip, charpos, transitions, clicks, mysteryClicks };
}

const paint = (mine: string[], selected: string | null = null): RegionPaint =>
  ({ mine: new Set(mine), selected, highlight: null, claimColor: new Map(), claimer: new Map(), group: new Set<string>() });

afterEach(() => {
  document.body.innerHTML = '';
});

describe('지도 위 캐릭터 이동', () => {
  it('처음에는 제자리에 바로 서고, 목적지가 바뀌면 한 번 깡충 이동한다', () => {
    const { engine, charpos, transitions } = mount();
    engine.placeCharacter('11010', LOOK_A, true);
    expect(charpos().getAttribute('transform')).toMatch(/^translate\(/);
    expect(transitions()).toHaveLength(0);
    engine.placeCharacter('37430', LOOK_A, true);
    expect(transitions()).toHaveLength(1);
  });

  it('같은 곳으로 가는 중에 화면이 다시 그려져도 이동을 처음부터 다시 하지 않는다', () => {
    const { engine, transitions } = mount();
    engine.placeCharacter('11010', LOOK_A, true);
    engine.placeCharacter('37430', LOOK_A, true);
    const [moving] = transitions();
    for (let i = 0; i < 5; i++) engine.placeCharacter('37430', LOOK_A, true);
    expect(transitions()).toEqual([moving]);
  });

  it('가는 도중 옷차림이 바뀌면 이동을 끊지 않고 도착한 뒤에 갈아입는다', () => {
    const { engine, svg, transitions } = mount();
    engine.placeCharacter('11010', LOOK_A, true);
    engine.placeCharacter('37430', LOOK_A, true);
    const [moving] = transitions();
    engine.placeCharacter('37430', LOOK_B, true);
    expect(transitions()).toEqual([moving]);
    expect(svg.querySelector('g.charpos g')?.innerHTML).toBe(LOOK_A);
  });

  it('다른 곳으로 다시 출발하면 새 이동 하나가 이어받는다', () => {
    const { engine, transitions } = mount();
    engine.placeCharacter('11010', LOOK_A, true);
    engine.placeCharacter('37430', LOOK_A, true);
    const [first] = transitions();
    engine.placeCharacter('31370', LOOK_A, true);
    const after = transitions();
    expect(after).not.toContain(first);
    expect(after).toHaveLength(1);
  });

  it('가는 도중 새 목적지가 생기면 출발 지역이 아니라 지금 있는 곳에서 이어 간다', async () => {
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

  it('두 번째로 가는 도중에도 갈아입기는 도착 뒤로 미룬다', async () => {
    const { engine, svg } = mount();
    engine.placeCharacter('11010', LOOK_A, true);
    engine.placeCharacter('37430', LOOK_A, true);
    await new Promise(resolve => setTimeout(resolve, 100)); // 첫 이동이 실제로 달리는 중(interrupt 콜백이 불린다)
    engine.placeCharacter('31370', LOOK_A, true);
    engine.placeCharacter('31370', LOOK_B, true);
    expect(svg.querySelector('g.charpos g')?.innerHTML).toBe(LOOK_A);
  });

  it('칠한 곳이 없으면 캐릭터를 숨긴다', () => {
    const { engine, charpos } = mount();
    engine.placeCharacter(null, LOOK_A, true);
    expect(charpos().getAttribute('display')).toBe('none');
  });
});

describe('지도 칠하기', () => {
  const regionIn = (svg: SVGSVGElement) => (code: string) => svg.querySelector(`path.region[data-code="${code}"]`) as SVGPathElement;

  it('전설 지역은 처음부터 전설로 표시한다', () => {
    const { svg } = mount();
    expect(regionIn(svg)('37430').classList.contains('legend')).toBe(true);
    expect(regionIn(svg)('11010').classList.contains('legend')).toBe(false);
  });

  it('내 영토와 지금 고른 지역을 칠한다', () => {
    const { engine, svg } = mount();
    engine.setPaint(paint(['11010'], '31370'));
    expect(regionIn(svg)('11010').classList.contains('on')).toBe(true);
    expect(regionIn(svg)('31370').classList.contains('focus')).toBe(true);
    expect(regionIn(svg)('37430').classList.contains('on')).toBe(false);
  });

  it('바뀐 것이 없으면 다시 칠하지 않고, 바뀌면 다시 칠한다', () => {
    const { engine, svg } = mount();
    const region = regionIn(svg);
    engine.setPaint(paint(['11010'], '31370'));
    region('11010').style.fill = 'red'; // 다시 칠하면 덧칠한 색이 지워진다
    engine.setPaint(paint(['11010'], '31370'));
    expect(region('11010').style.fill).toBe('red');
    engine.setPaint(paint(['11010', '11020'], '31370'));
    expect(region('11010').style.fill).toBe('');
  });

  it('다른 탭에서 보여 달라고 한 지역을 강조한다', () => {
    const { engine, svg } = mount();
    engine.setPaint({ ...paint([]), highlight: '37430' });
    expect(regionIn(svg)('37430').classList.contains('hl')).toBe(true);
  });

  it('공유 지도에서는 먼저 칠한 멤버의 색으로 칠하고 누가 칠했는지 표시한다', () => {
    const { engine, svg } = mount();
    engine.setPaint({ ...paint([]), claimColor: new Map([['11010', '#e8743b']]), claimer: new Map([['11010', 'friend']]) });
    const jongno = regionIn(svg)('11010');
    expect(jongno.classList.contains('claimed')).toBe(true);
    expect(jongno.style.fill).toBe('rgb(232, 116, 59)');
    expect(jongno.getAttribute('data-claim')).toBe('friend');
  });
});

describe('시·도 정복 테두리', () => {
  const outlineOf = (svg: SVGSVGElement, province: string) => svg.querySelector(`g.conquest[data-province="${province}"]`) as SVGGElement | null;

  it('정복한 시·도는 그 시·도 지역들로 테두리를 두르고, 안쪽 경계선은 가리개로 가린다', () => {
    const { engine, svg } = mount();
    engine.setConqueredProvinces(new Set(['서울']));
    const outline = outlineOf(svg, '서울');
    expect(outline?.querySelectorAll('path')).toHaveLength(2);
    const maskId = /url\(#(.+)\)/.exec(outline?.getAttribute('mask') ?? '')?.[1];
    expect(svg.querySelector(`mask[id="${maskId}"]`)?.querySelectorAll('path')).toHaveLength(2);
    expect(outlineOf(svg, '경기')).toBeNull();
  });

  it('정복한 시·도가 늘면 그 시·도만 더하고, 같은 목록이면 다시 그리지 않는다', () => {
    const { engine, svg } = mount();
    engine.setConqueredProvinces(new Set(['서울']));
    const seoul = outlineOf(svg, '서울');
    engine.setConqueredProvinces(new Set(['서울']));
    engine.setConqueredProvinces(new Set(['서울', '경북']));
    expect(outlineOf(svg, '서울')).toBe(seoul);
    expect(outlineOf(svg, '경북')).not.toBeNull();
    expect(svg.querySelectorAll('mask')).toHaveLength(2);
  });

  it('막 정복한 시·도는 잠깐 반짝이고, 반짝이는 동안 다시 불려도 처음부터 다시 걸지 않는다', () => {
    const { engine, svg } = mount();
    engine.setConqueredProvinces(new Set(['서울']));
    engine.flashProvinces(['서울', '경기']);
    const seoul = outlineOf(svg, '서울') as SVGGElement;
    expect(seoul.classList.contains('flash')).toBe(true);
    seoul.classList.add('restarted-marker');
    engine.flashProvinces(['서울']);
    expect(seoul.classList.contains('restarted-marker')).toBe(true);
    seoul.dispatchEvent(new Event('animationend'));
    expect(seoul.classList.contains('flash')).toBe(false);
  });
});

describe('이번 주 미스터리 마커', () => {
  const markOf = (svg: SVGSVGElement) => svg.querySelector('g.mystery-mark') as SVGGElement | null;

  it('미스터리 지역 한가운데 물음표 마커를 세우고, 누르면 그 지역을 알린다', () => {
    const { engine, svg, clicks, mysteryClicks } = mount();
    engine.setMystery({ code: '31370', received: false });
    const mark = markOf(svg) as SVGGElement;
    expect(mark.getAttribute('data-mystery')).toBe('31370');
    expect(mark.getAttribute('transform')).toMatch(/^translate\(/);
    expect(mark.textContent).toBe('❓');
    mark.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    expect(mysteryClicks).toEqual(['31370']);
    expect(clicks).toEqual([]);
  });

  it('이번 주 보너스를 받으면 체크 표시로 바뀌고, 같은 값이면 다시 그리지 않는다', () => {
    const { engine, svg } = mount();
    engine.setMystery({ code: '31370', received: false });
    const mark = markOf(svg);
    engine.setMystery({ code: '31370', received: true });
    expect(markOf(svg)).toBe(mark);
    expect(mark?.classList.contains('received')).toBe(true);
    expect(mark?.textContent).toBe('✓');
  });

  it('미스터리 지역이 없거나 지도에 없는 지역이면 마커를 세우지 않는다', () => {
    const { engine, svg } = mount();
    engine.setMystery({ code: '99999', received: false });
    expect(markOf(svg)).toBeNull();
    engine.setMystery({ code: '31370', received: false });
    engine.setMystery(null);
    expect(markOf(svg)).toBeNull();
  });
});

describe('가고 싶은 곳 핀', () => {
  const pinsOf = (svg: SVGSVGElement) => [...svg.querySelectorAll('g.wish-pin')].map(pin => pin.getAttribute('data-wish'));

  it('꽂은 지역마다 한가운데 핀을 세우고, 핀은 눌러도 아래 지역이 눌린다', () => {
    const { engine, svg } = mount();
    engine.setWishPins(new Set(['31370', '11010']));
    expect(pinsOf(svg)).toEqual(['11010', '31370']);
    expect(svg.querySelector('g.wish-pin')?.getAttribute('transform')).toMatch(/^translate\(/);
    expect((svg.querySelector('g.wish-layer') as SVGGElement).style.pointerEvents).toBe('none');
  });

  it('다녀와서 목록에서 빠진 지역의 핀은 걷고, 같은 목록이면 다시 그리지 않는다', () => {
    const { engine, svg } = mount();
    engine.setWishPins(new Set(['31370', '11010']));
    const kept = svg.querySelector('g.wish-pin[data-wish="11010"]');
    engine.setWishPins(new Set(['11010']));
    expect(pinsOf(svg)).toEqual(['11010']);
    expect(svg.querySelector('g.wish-pin[data-wish="11010"]')).toBe(kept);
  });

  it('지도에 없는 지역은 핀을 세우지 않는다', () => {
    const { engine, svg } = mount();
    engine.setWishPins(new Set(['99999']));
    expect(pinsOf(svg)).toEqual([]);
  });
});

describe('지역 묶음 강조', () => {
  it('함께 보여 줄 지역 묶음을 모두 강조하고, 묶음이 풀리면 강조도 걷는다', () => {
    const { engine, svg } = mount();
    engine.setPaint({ ...paint([]), group: new Set(['11010', '31370']) });
    expect([...svg.querySelectorAll('path.region.grp')].map(path => path.getAttribute('data-code')).sort()).toEqual(['11010', '31370']);
    engine.setPaint(paint([]));
    expect(svg.querySelectorAll('path.region.grp')).toHaveLength(0);
  });
});

describe('지도 만지기', () => {
  it('지역을 누르면 어느 지역인지 알린다', () => {
    const { svg, clicks } = mount();
    svg.querySelector('path.region[data-code="31370"]')?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    expect(clicks).toEqual(['31370']);
  });

  it('지역 위에 마우스를 올리면 그 지역 설명을 띄우고, 벗어나면 감춘다', () => {
    const { svg, tip } = mount();
    const region = svg.querySelector('path.region[data-code="37430"]') as SVGPathElement;
    region.dispatchEvent(new MouseEvent('mousemove', { bubbles: true, clientX: 10, clientY: 20 }));
    expect(tip.textContent).toBe('37430');
    expect(tip.classList.contains('show')).toBe(true);
    region.dispatchEvent(new MouseEvent('mouseleave'));
    expect(tip.classList.contains('show')).toBe(false);
  });

  it('체크인한 곳에 퍼지는 원을 그리고, 모르는 지역이면 그리지 않는다', () => {
    const { engine, svg } = mount();
    engine.ping('11010');
    engine.ping('99999');
    expect(svg.querySelectorAll('circle.ping')).toHaveLength(1);
  });
});
