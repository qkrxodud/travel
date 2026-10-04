import { useEffect } from 'react';
import { analytics, track } from '../api/analytics';
import { entryPoint, pushOpenKind, withoutEntryMark } from '../shared/lib/analytics/entry';
import { useUiStore } from '../store/uiStore';

/** 이 페이지에서 첫 화면 이벤트를 이미 보냈는지(앱이 다시 마운트돼도 한 번만) */
let opened = false;

/**
 * 앱 셸 수준의 화면 이벤트: 첫 화면 노출(app_open, 들어온 갈래만 — 알림을 눌러 열었으면 push_open 도) → 처음 탭과 이후 탭 전환(tab_view).
 * 화면이 숨겨지거나 닫힐 때 남은 이벤트를 보내도록 페이지 이벤트를 듣는다.
 */
export function useAnalytics(): void {
  useEffect(() => {
    if (!opened) {
      opened = true;
      track('app_open', { entry: entryPoint(location.href, document.referrer) });
      const pushKind = pushOpenKind(location.href);
      if (pushKind) track('push_open', { kind: pushKind });
      const cleaned = withoutEntryMark(location.href);
      if (cleaned) history.replaceState(history.state, '', cleaned);
    }
    track('tab_view', { tab: useUiStore.getState().tab });
    const stopTabs = useUiStore.subscribe((state, previous) => {
      if (state.tab !== previous.tab) track('tab_view', { tab: state.tab });
    });
    const stopPage = analytics.listen(document, window);
    return () => {
      stopTabs();
      stopPage();
    };
  }, []);
}
