/**
 * 도감 탭 — 같은 테마의 지역을 모아 세트를 완성한다. 모은 수·완성 여부·보상은 지금 보는 지도 기준 서버 값이다.
 * 이야기 순서: 불러오는 중 → 세트 진행 → 세트 완성 → 지역을 지도에서 보기.
 */
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { CollectionResponse, SetResponse } from '../../api/types/progression';
import { useUiStore } from '../../store/uiStore';
import { CATALOG } from '../../test/fixtures';
import { CollectionTab } from './components/CollectionTab';

const { served } = vi.hoisted(() => ({ served: { collection: undefined as CollectionResponse | undefined } }));
vi.mock('../../shared/queries/catalog', () => ({ useCatalog: () => CATALOG }));
vi.mock('../../shared/queries/collection', () => ({ useCollection: () => ({ data: served.collection }) }));

const set = (overrides: Partial<SetResponse>): SetResponse => ({
  id: 'seoul', name: '서울 둘', desc: '서울 두 곳', title: '서울러', have: 1, total: 2, completed: false, completedAt: null, rewardXp: 100,
  regions: [{ code: 'KR-11010', name: '종로구', collected: true }, { code: 'KR-11020', name: '중구', collected: false }], ...overrides,
});
const collection = (...sets: SetResponse[]): CollectionResponse => ({ mapId: 'personal', completed: sets.filter(entry => entry.completed).length, total: sets.length, sets });

beforeEach(() => useUiStore.setState({ tab: 'sets', selected: null, highlight: null, mapCommand: null }));
afterEach(() => {
  cleanup();
  served.collection = undefined;
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
