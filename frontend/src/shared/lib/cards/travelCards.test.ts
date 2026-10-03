/**
 * 화면에서 그리는 자랑 카드 — 여행 카드(방금 칠한 곳)와 영토 비교 카드. 정복률·비교 결과는 서버 값이다.
 */
import { describe, expect, it } from 'vitest';
import { CATALOG } from '../../../test/fixtures';
import { recentCard, vsCard, type CardCanvas } from './travelCards';

/** 글자만 받아 적는 가짜 캔버스(jsdom 에는 2D 그리기가 없다) */
function recordingCanvas() {
  const texts: string[] = [];
  const context = new Proxy({} as Record<string | symbol, unknown>, {
    get: (target, key) => (key === 'fillText' ? (text: string) => void texts.push(text) : key in target ? target[key] : () => undefined),
    set: (target, key, value) => { target[key] = value; return true; },
  });
  const canvas: CardCanvas = {
    create: () => ({ getContext: () => context, toDataURL: () => 'data:image/png;card' }) as unknown as HTMLCanvasElement,
    itemCanvas: () => ({}) as CanvasImageSource,
  };
  return { canvas, texts };
}

const visits = new Map([
  ['11010', { date: '2026-09-01', memo: '', at: 1 }],
  ['37430', { date: '2026-10-02', memo: '독도 보고 옴', at: 2 }],
]);

describe('여행 카드', () => {
  const draw = (conquestPercent: number | null) => {
    const { canvas, texts } = recordingCanvas();
    const url = recentCard(canvas, { catalog: CATALOG, code: '37430', visits, sets: [{ name: '섬 둘', have: 2, total: 2 }], item: null, topPercent: '12', conquestPercent });
    return { url, texts };
  };

  it('몇 번째 영토인지와 방문 날짜, 지역·희귀도·받은 XP, 메모를 적는다', () => {
    const { url, texts } = draw(40);
    expect(url).toBe('data:image/png;card');
    expect(texts).toEqual(expect.arrayContaining(['2번째 영토 · 2026-10-02', '울릉군', '경북 · 전설 지역 · +50 XP', '“독도 보고 옴”']));
  });

  it('이 지역이 든 세트의 모은 수를 적고, 다 모았으면 완성이라고 적는다', () => {
    expect(draw(40).texts).toEqual(expect.arrayContaining(['섬 둘 세트', '2 / 2 완성']));
  });

  it('개인 지도에서는 전국 정복률로 서버 값을 쓴다', () => {
    expect(draw(40).texts).toContain('전국 40% · 상위 12%');
  });

  it('공유 지도처럼 서버 정복률이 없으면 내가 칠한 곳 수로 센다', () => {
    expect(draw(null).texts).toContain('전국 50% · 상위 12%');
  });

  it('모르는 지역이면 카드를 만들지 않는다', () => {
    const { canvas } = recordingCanvas();
    expect(recentCard(canvas, { catalog: CATALOG, code: '99999', visits, sets: [], item: null, topPercent: '—' })).toBeNull();
  });
});

describe('영토 비교 카드', () => {
  it('서버 비교 결과로 양쪽 지역 수와 나만·둘 다·친구만 간 곳 수를 적는다', () => {
    const { canvas, texts } = recordingCanvas();
    vsCard(canvas, { catalog: CATALOG, otherName: '@lee', onlyMine: ['37430'], both: ['11010'], onlyTheirs: ['31370', '11020'] });
    expect(texts).toEqual(expect.arrayContaining(['2', '3', '@lee', '나만 간 곳', '1곳', '둘 다 간 곳', '@lee만 간 곳', '2곳']));
  });
});
