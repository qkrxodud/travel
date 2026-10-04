/**
 * PWA 화면 기억(12단계) — 이 브라우저에만 두는 값(서버 상태 아님): 방문 기록(설치 배너 조건), "알림 받을래요?"에 어떻게 답했는지,
 * 설치 배너를 언제 닫았는지. 판단(언제 다시 묻는지·몇 번째 방문인지)은 features/pwa/model 이 하고 여기는 저장만 한다.
 */
import { create } from 'zustand';

/** localStorage 키 */
export const VISIT_LOG_KEY = 'territory-visit-log';
export const PUSH_PROMPT_KEY = 'territory-push-prompt';
export const INSTALL_DISMISSED_KEY = 'territory-install-dismissed';

export interface VisitLog {
  /** 지금까지 방문 수(30분 넘게 쉬었다 다시 열면 새 방문) */
  count: number;
  /** 마지막으로 본 시각(ISO) */
  lastSeenAt: string | null;
}

export type PromptAnswer = 'granted' | 'denied' | 'dismissed';

export interface PromptMemory {
  answer: PromptAnswer | null;
  /** "나중에"를 누른 횟수 */
  dismissals: number;
  /** 마지막으로 "나중에"·닫기를 누른 시각(ISO) */
  dismissedAt: string | null;
}

export const EMPTY_VISITS: VisitLog = { count: 0, lastSeenAt: null };
export const EMPTY_PROMPT: PromptMemory = { answer: null, dismissals: 0, dismissedAt: null };

function read<T extends object>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key);
    if (!raw) return fallback;
    const parsed: unknown = JSON.parse(raw);
    return parsed && typeof parsed === 'object' ? { ...fallback, ...(parsed as Partial<T>) } : fallback;
  } catch {
    return fallback;
  }
}

function write(key: string, value: unknown): void {
  try {
    if (value === null) localStorage.removeItem(key);
    else localStorage.setItem(key, JSON.stringify(value));
  } catch {
    // 저장소를 못 쓰면 이 화면 동안만
  }
}

function readDismissed(): string | null {
  try {
    return localStorage.getItem(INSTALL_DISMISSED_KEY);
  } catch {
    return null;
  }
}

interface PwaState {
  visits: VisitLog;
  prompt: PromptMemory;
  installDismissedAt: string | null;
  setVisits: (visits: VisitLog) => void;
  setPrompt: (prompt: PromptMemory) => void;
  setInstallDismissedAt: (at: string | null) => void;
}

export const usePwaStore = create<PwaState>()(set => ({
  visits: read(VISIT_LOG_KEY, EMPTY_VISITS),
  prompt: read(PUSH_PROMPT_KEY, EMPTY_PROMPT),
  installDismissedAt: readDismissed(),
  setVisits: visits => {
    write(VISIT_LOG_KEY, visits);
    set({ visits });
  },
  setPrompt: prompt => {
    write(PUSH_PROMPT_KEY, prompt);
    set({ prompt });
  },
  setInstallDismissedAt: at => {
    try {
      if (at === null) localStorage.removeItem(INSTALL_DISMISSED_KEY);
      else localStorage.setItem(INSTALL_DISMISSED_KEY, at);
    } catch {
      // 저장소를 못 쓰면 이 화면 동안만
    }
    set({ installDismissedAt: at });
  },
}));
