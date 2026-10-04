import { useSyncExternalStore } from 'react';
import { readDevice } from '../../../shared/lib/pwa/device';
import { installPrompt } from '../../../shared/lib/pwa/installPrompt';
import { usePwaStore } from '../../../store/pwaStore';
import { installOffer } from '../model/install';
import { useCheckedIn } from '../queries';

/** 홈 화면 설치 권유(두 번 이상 방문 + 첫 체크인 뒤, 닫으면 14일 숨김) — 지도 탭 오른쪽 칸 */
export function InstallBanner() {
  const checkedIn = useCheckedIn();
  const visits = usePwaStore(state => state.visits.count);
  const dismissedAt = usePwaStore(state => state.installDismissedAt);
  useSyncExternalStore(installPrompt.subscribe, installPrompt.snapshot);
  const device = readDevice(true);
  const offer = installOffer({
    visits, checkedIn, standalone: device.standalone, installed: installPrompt.installed, ios: device.ios,
    canPrompt: installPrompt.available, dismissedAt, now: new Date(),
  });
  if (offer === 'none') return null;

  const dismiss = () => usePwaStore.getState().setInstallDismissedAt(new Date().toISOString());
  const install = async () => {
    const outcome = await installPrompt.prompt();
    if (outcome === 'dismissed') dismiss();
  };
  return (
    <div className="card install-ask" id="install-banner" data-offer={offer}>
      <h2>📲 홈 화면에 두고 바로 열기</h2>
      <p className="note">
        {offer === 'prompt' ? '설치하면 앱처럼 한 번에 열리고, 여행 중에도 지도를 빨리 꺼낼 수 있어요.'
          : '사파리 아래쪽 공유 버튼 → "홈 화면에 추가"를 누르면 앱처럼 열리고 알림도 받을 수 있어요.'}
      </p>
      <div className="row">
        {offer === 'prompt' ? <button className="btn primary sm" id="install-accept" type="button" onClick={() => void install()}>설치하기</button> : null}
        <button className="btn sm" id="install-dismiss" type="button" onClick={dismiss}>닫기</button>
      </div>
    </div>
  );
}
