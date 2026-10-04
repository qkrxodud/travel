import { useSyncExternalStore } from 'react';
import { serviceWorkerClient } from '../../../shared/lib/pwa/serviceWorkerClient';

const subscribe = (listener: () => void) => serviceWorkerClient.onUpdate(listener);
const snapshot = () => serviceWorkerClient.updateReady;

/** 새 버전 서비스워커가 기다리면 "새 버전이 있어요 — 새로고침"(화면 위 — 아래는 토스트 자리) */
export function UpdateBanner() {
  const ready = useSyncExternalStore(subscribe, snapshot);
  if (!ready) return null;
  return (
    <div className="update-bar" id="update-banner" role="status">
      <span>새 버전이 있어요</span>
      <button className="btn primary sm" id="update-reload" type="button" onClick={() => serviceWorkerClient.applyUpdate()}>새로고침</button>
    </div>
  );
}
