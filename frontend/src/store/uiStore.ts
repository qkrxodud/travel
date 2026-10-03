/**
 * 화면 상태(zustand) — 서버 응답은 여기에 두지 않는다(TanStack Query 몫).
 * 현재 탭·선택 지역·하이라이트·칠하기/기록 모드·지금 보는 지도·열린 모달·가방 필터·비교 대상.
 */
import { create } from 'zustand';

export const TABS = ['map', 'bag', 'sets', 'quests', 'rank', 'profile'] as const;
export type Tab = (typeof TABS)[number];
export type MapMode = 'paint' | 'detail';
export type BagFilter = 'all' | 'hat' | 'hand' | 'badge' | 'back' | 'pet' | 'bg' | 'prop';

/** 지도 모달(공유 지도 만들기·만든 결과·합류·탈퇴) */
export type MapModal =
  | { kind: 'create' }
  | { kind: 'created'; name: string; inviteCode: string; rules: string[] }
  | { kind: 'join' }
  | { kind: 'leave'; mapId: string; name: string; regionCount: number };

/** 지도 엔진에 보내는 한 번짜리 명령(줌·전체 보기·캐릭터로) */
export type MapCommand =
  | { kind: 'zoom'; codes: string[]; pad?: number }
  | { kind: 'reset' }
  | { kind: 'to-character' }
  | { kind: 'ping'; code: string };

const MAP_KEY = 'territory-map-id';
/** 프로토타입 화면 저장 키(가방 필터만 남는다) */
const PREFS_KEY = 'territory-mvp-v3';

function readStorage(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function writeStorage(key: string, value: string | null): void {
  try {
    if (value === null) localStorage.removeItem(key);
    else localStorage.setItem(key, value);
  } catch {
    // 저장소를 못 쓰면 메모리 상태만
  }
}

function readBagFilter(): BagFilter {
  try {
    const saved = JSON.parse(readStorage(PREFS_KEY) ?? '{}') as { bagFilter?: unknown };
    const filters: readonly string[] = ['all', 'hat', 'hand', 'badge', 'back', 'pet', 'bg', 'prop'];
    return typeof saved.bagFilter === 'string' && filters.includes(saved.bagFilter) ? (saved.bagFilter as BagFilter) : 'all';
  } catch {
    return 'all';
  }
}

function initialTab(): Tab {
  const hash = typeof location === 'undefined' ? '' : location.hash.slice(1);
  return (TABS as readonly string[]).includes(hash) ? (hash as Tab) : 'map';
}

export interface UiState {
  tab: Tab;
  mode: MapMode;
  selected: string | null;
  highlight: string | null;
  /** 지금 보는 지도(null = 개인 지도) */
  mapId: string | null;
  bagFilter: BagFilter;
  /** 예시 데이터(서버 시드)가 칠해져 있는지 — 안내 문구·알림 억제 */
  sampleMode: boolean;
  /** 체크인 모달을 띄울 지역(미리보기 응답이 오면 열린다) */
  checkinCode: string | null;
  mapModal: MapModal | null;
  /** 카드 미리보기 모달 이미지 주소 */
  cardUrl: string | null;
  /** 영토 비교 대상 handle */
  compareHandle: string | null;
  mapCommand: (MapCommand & { id: number }) | null;

  setTab: (tab: Tab) => void;
  setMode: (mode: MapMode) => void;
  select: (code: string | null) => void;
  setHighlight: (code: string | null) => void;
  /** 다른 탭에서 "지도에서 보기" */
  showOnMap: (code: string) => void;
  setMapId: (mapId: string | null) => void;
  setBagFilter: (filter: BagFilter) => void;
  setSampleMode: (sample: boolean) => void;
  openCheckin: (code: string) => void;
  closeCheckin: () => void;
  openMapModal: (modal: MapModal) => void;
  closeMapModal: () => void;
  showCard: (url: string) => void;
  closeCard: () => void;
  compareWith: (handle: string | null) => void;
  sendMapCommand: (command: MapCommand) => void;
}

let commandSequence = 0;

export const useUiStore = create<UiState>()(set => ({
  tab: initialTab(),
  mode: 'paint',
  selected: null,
  highlight: null,
  mapId: readStorage(MAP_KEY),
  bagFilter: readBagFilter(),
  sampleMode: false,
  checkinCode: null,
  mapModal: null,
  cardUrl: null,
  compareHandle: null,
  mapCommand: null,

  setTab: tab => {
    try {
      history.replaceState(null, '', '#' + tab);
    } catch {
      // 히스토리를 못 쓰는 환경
    }
    set({ tab });
  },
  setMode: mode => set({ mode }),
  select: code => set({ selected: code, highlight: null }),
  setHighlight: code => set({ highlight: code }),
  showOnMap: code => {
    try {
      history.replaceState(null, '', '#map');
    } catch {
      // 히스토리를 못 쓰는 환경
    }
    commandSequence += 1;
    set({ tab: 'map', highlight: code, selected: code, mapCommand: { kind: 'zoom', codes: [code], pad: 0.25, id: commandSequence } });
  },
  setMapId: mapId => {
    writeStorage(MAP_KEY, mapId);
    set({ mapId, selected: null, highlight: null });
  },
  setBagFilter: filter => {
    writeStorage(PREFS_KEY, JSON.stringify({ bagFilter: filter }));
    set({ bagFilter: filter });
  },
  setSampleMode: sample => set({ sampleMode: sample }),
  openCheckin: code => set({ checkinCode: code }),
  closeCheckin: () => set({ checkinCode: null }),
  openMapModal: modal => set({ mapModal: modal }),
  closeMapModal: () => set({ mapModal: null }),
  showCard: url => set({ cardUrl: url }),
  closeCard: () => set({ cardUrl: null }),
  compareWith: handle => set({ compareHandle: handle }),
  sendMapCommand: command => {
    commandSequence += 1;
    set({ mapCommand: { ...command, id: commandSequence } });
  },
}));
