/**
 * 도감 탭 — 같은 테마의 지역을 모아 세트를 완성한다. 모은 수·완성 여부·보상은 지금 보는 지도 기준 서버 값이다.
 * (9단계) 계절 한정 — 봄·가을 회차 기간 안에 칠한 곳만 세어 완성하면 보상을 받는다. 회차·남은 기간·진행·보상은 서버 값이다.
 * 이야기 순서: 불러오는 중 → 세트 진행 → 세트 완성 → 지역을 지도에서 보기 → 계절 한정(기간 중 → 완성 → 기간 밖·지난 기록).
 */
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { CollectionResponse, SeasonRoundResponse, SeasonsResponse, SetResponse } from '../../api/types/progression';
import { useUiStore } from '../../store/uiStore';
import { CATALOG } from '../../test/fixtures';
import { CollectionTab } from './components/CollectionTab';

const { served } = vi.hoisted(() => ({ served: { collection: undefined as CollectionResponse | undefined, seasons: undefined as SeasonsResponse | undefined } }));
vi.mock('../../shared/queries/catalog', () => ({ useCatalog: () => CATALOG }));
vi.mock('../../shared/queries/collection', () => ({ useCollection: () => ({ data: served.collection }) }));
vi.mock('../../shared/queries/seasons', () => ({ useSeasons: () => ({ data: served.seasons }) }));

const set = (overrides: Partial<SetResponse>): SetResponse => ({
  id: 'seoul', name: '서울 둘', desc: '서울 두 곳', title: '서울러', have: 1, total: 2, completed: false, completedAt: null, rewardXp: 100,
  regions: [{ code: 'KR-11010', name: '종로구', collected: true }, { code: 'KR-11020', name: '중구', collected: false }], ...overrides,
});
const collection = (...sets: SetResponse[]): CollectionResponse => ({ mapId: 'personal', completed: sets.filter(entry => entry.completed).length, total: sets.length, sets });

beforeEach(() => useUiStore.setState({ tab: 'sets', selected: null, highlight: null, mapCommand: null }));
afterEach(() => {
  cleanup();
  served.collection = undefined;
  served.seasons = undefined;
});

describe('도감 불러오기', () => {
  it('서버 도감이 오기 전에는 불러오는 중이라고 보여 준다', () => {
    render(<CollectionTab />);
    expect(screen.getByText('불러오는 중…')).toBeTruthy();
  });
});

describe('세트 진행', () => {
  it('완성한 세트 수와 전체 세트 수를 머리에 보여 준다', () => {
    served.collection = collection(set({}), set({ id: 'jiri', name: '지리산', completed: true, have: 2 }));
    render(<CollectionTab />);
    expect(screen.getByText('1 / 2 완성')).toBeTruthy();
  });

  it('세트마다 모은 수와 아직 받을 보상(칭호·XP)을 보여 준다', () => {
    served.collection = collection(set({}));
    render(<CollectionTab />);
    expect(screen.getByText('1 / 2')).toBeTruthy();
    expect(screen.getByText('보상: 칭호 「서울러」 + 100 XP')).toBeTruthy();
  });

  it('지역 칸에는 지역 이름이, 툴팁에는 시·도까지 붙은 이름이 보인다', () => {
    served.collection = collection(set({}));
    render(<CollectionTab />);
    expect(screen.getByRole('button', { name: '종로구' }).getAttribute('title')).toBe('서울 종로구');
  });
});

describe('세트 완성', () => {
  it('완성한 세트는 받은 칭호와 XP 를 완성으로 보여 준다', () => {
    served.collection = collection(set({ have: 2, completed: true }));
    render(<CollectionTab />);
    expect(screen.getByText('완성 · 칭호 「서울러」 +100 XP')).toBeTruthy();
  });
});

describe('지역을 지도에서 보기', () => {
  it('세트의 지역을 누르면 지도 탭으로 가서 그 지역을 고르고 확대한다', () => {
    served.collection = collection(set({}));
    render(<CollectionTab />);
    fireEvent.click(screen.getByRole('button', { name: '중구' }));
    expect(useUiStore.getState()).toMatchObject({ tab: 'map', selected: '11020', highlight: '11020', mapCommand: { kind: 'zoom', codes: ['11020'] } });
  });
});

describe('계절 한정', () => {
  const round = (overrides: Partial<SeasonRoundResponse> = {}): SeasonRoundResponse => ({
    roundId: 'autumn-2026', seasonId: 'autumn', name: '2026 단풍 명소', emoji: '🍁', year: 2026,
    startsAt: '2026-09-30T15:00:00Z', endsAt: '2026-11-30T15:00:00Z', remainingSeconds: 57 * 86400 + 3600, open: true,
    have: 1, total: 2, completed: false, completedAt: null, rewarded: false, xp: 150, titleId: 'season-autumn', titleName: '단풍 사냥꾼',
    backgroundItemId: 'season:autumn-2026',
    regions: [{ code: 'KR-31370', name: '가평군', provinceCode: 'KR-31', collected: true }, { code: 'KR-11010', name: '종로구', provinceCode: 'KR-11', collected: false }],
    ...overrides,
  });
  const seasons = (overrides: Partial<SeasonsResponse> = {}): SeasonsResponse =>
    ({ mapId: 'personal', now: '2026-10-04T03:00:00Z', current: [round()], next: null, history: [], ...overrides });

  describe('회차 기간 중', () => {
    it('지금 회차 이름과 남은 기간, 기간 안에 모은 수, 아직 받을 완성 보상을 보여 준다', () => {
      served.seasons = seasons();
      render(<CollectionTab />);
      expect(screen.getByText('🍁 2026 단풍 명소')).toBeTruthy();
      expect(document.querySelector('#season-left')?.textContent).toBe('57일 남음');
      expect(document.querySelector('#season-have')?.textContent).toBe('1 / 2');
      expect(document.querySelector('#season-reward')?.textContent).toBe('보상: 칭호 「단풍 사냥꾼」 + 150 XP · 2026 계절 배경');
    });

    it('회차 기간을 서울 날짜로 알려 주고, 이 기간에 칠한 곳만 센다고 안내한다', () => {
      served.seasons = seasons();
      render(<CollectionTab />);
      expect(screen.getByText(/^10월 1일 ~ 11월 30일 · 이 기간에 칠한 곳만 세어요/)).toBeTruthy();
    });

    it('기간 안에 칠해 센 지역은 채워 보여 준다', () => {
      served.seasons = seasons();
      render(<CollectionTab />);
      expect(document.querySelector('[data-season-region="31370"]')?.classList.contains('on')).toBe(true);
      expect(document.querySelector('[data-season-region="11010"]')?.classList.contains('on')).toBe(false);
    });

    it('지도에서 보기를 누르면 지도 탭으로 가서 회차 지역을 모두 강조하고 한눈에 보이게 확대한다', () => {
      served.seasons = seasons();
      render(<CollectionTab />);
      fireEvent.click(screen.getByRole('button', { name: '지도에서 보기' }));
      expect(useUiStore.getState()).toMatchObject({ tab: 'map', focusRegions: ['31370', '11010'], mapCommand: { kind: 'zoom', codes: ['31370', '11010'] } });
    });

    it('회차 지역 하나를 누르면 그 지역을 지도에서 고른다', () => {
      served.seasons = seasons();
      render(<CollectionTab />);
      fireEvent.click(screen.getByRole('button', { name: '가평군' }));
      expect(useUiStore.getState()).toMatchObject({ tab: 'map', selected: '31370' });
    });
  });

  describe('회차를 완성하면', () => {
    it('완성 시점 멤버였으면 받은 칭호·XP·계절 배경을 보여 준다', () => {
      served.seasons = seasons({ current: [round({ have: 2, completed: true, rewarded: true, completedAt: '2026-10-04T03:00:00Z' })] });
      render(<CollectionTab />);
      expect(document.querySelector('#season-reward')?.textContent).toBe('완성 · 칭호 「단풍 사냥꾼」 +150 XP · 2026 계절 배경을 받았어요');
    });

    it('완성한 뒤에 함께한 멤버에게는 이 회차 보상이 없다고 알려 준다', () => {
      served.seasons = seasons({ current: [round({ have: 2, completed: true, rewarded: false })] });
      render(<CollectionTab />);
      expect(document.querySelector('#season-reward')?.textContent).toBe('완성 · 완성한 뒤에 함께해 이 회차 보상은 없어요');
    });
  });

  describe('계절 기간이 아닐 때', () => {
    it('기간이 아니라고 알리고, 다음 회차가 언제 열리는지와 지난 회차 기록을 보여 준다', () => {
      served.seasons = seasons({
        current: [],
        next: { roundId: 'spring-2027', seasonId: 'spring', name: '2027 벚꽃 명소', emoji: '🌸', startsAt: '2027-03-19T15:00:00Z', endsAt: '2027-04-30T15:00:00Z' },
        history: [round({ open: false, remainingSeconds: 0, have: 2, completed: true, rewarded: true })],
      });
      render(<CollectionTab />);
      expect(document.querySelector('#season-left')?.textContent).toBe('기간 아님');
      expect(document.querySelector('#season-next')?.textContent).toBe('다음 회차 🌸 2027 벚꽃 명소 · 3월 20일부터');
      expect(document.querySelector('#season-history li')?.textContent).toBe('🍁 2026 단풍 명소 · 2/2 · 완성');
    });
  });
});
