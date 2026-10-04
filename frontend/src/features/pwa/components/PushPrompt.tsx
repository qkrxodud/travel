import { useEffect, useRef, useState } from 'react';
import { errorToastCode, track } from '../../../api/analytics';
import { pushBrowser } from '../../../shared/lib/pwa/pushBrowser';
import { usePwaStore } from '../../../store/pwaStore';
import { consentStep, dismissed, OUTCOME_TEXT, type EnableOutcome } from '../model/consent';
import { useCheckedIn, useDeviceSubscription, useEnablePush } from '../queries';

type Phase = 'idle' | 'asking' | EnableOutcome;

/** 첫 체크인 뒤 "알림 받을래요?"(예 → 그때 브라우저 권한) — 지도 탭 오른쪽 칸 맨 위 */
export function PushPrompt() {
  const checkedIn = useCheckedIn();
  const memory = usePwaStore(state => state.prompt);
  const support = pushBrowser.support();
  const device = useDeviceSubscription(checkedIn && support === 'supported');
  const enable = useEnablePush();
  const [phase, setPhase] = useState<Phase>('idle');
  const shown = useRef(false);
  const subscribed = support === 'supported' ? (device.isFetched ? !!device.data : null) : false;
  const step = consentStep({ checkedIn, support, permission: pushBrowser.permission(), subscribed, memory, now: new Date() });

  useEffect(() => {
    if (step === 'ask' && !shown.current) {
      shown.current = true;
      track('push_prompt', { result: 'shown' });
    }
  }, [step]);

  const later = (result: 'dismissed' | null) => {
    const store = usePwaStore.getState();
    store.setPrompt(dismissed(store.prompt, new Date()));
    if (result) track('push_prompt', { result });
  };
  const accept = async () => {
    setPhase('asking');
    try {
      setPhase(await enable.mutateAsync('prompt'));
    } catch (error) {
      track('error_toast', { code: errorToastCode(error) });
      setPhase('failed');
    }
  };

  if (phase !== 'idle') {
    const text = phase === 'asking' ? { title: '브라우저 알림 창에서 허용을 눌러 주세요', detail: '허용하면 이 기기로 알림을 보내요.' } : OUTCOME_TEXT[phase];
    return (
      <div className="card push-ask" id="push-prompt" data-step={phase} role="status">
        <h2>{text.title}</h2>
        <p className="note">{text.detail}</p>
        {phase === 'asking' ? null : <div className="row"><button className="btn sm" id="push-prompt-close" type="button" onClick={() => setPhase('idle')}>닫기</button></div>}
      </div>
    );
  }
  if (step === 'hidden') return null;
  if (step === 'ios-install') {
    return (
      <div className="card push-ask" id="push-prompt" data-step="ios-install">
        <h2>알림은 홈 화면 앱에서 받을 수 있어요</h2>
        <p className="note">iPhone·iPad 는 사파리 공유 버튼 → "홈 화면에 추가"로 설치한 뒤 프로필 탭에서 알림을 켤 수 있어요.</p>
        <div className="row"><button className="btn sm" id="push-prompt-close" type="button" onClick={() => later(null)}>닫기</button></div>
      </div>
    );
  }
  return (
    <div className="card push-ask" id="push-prompt" data-step="ask">
      <h2>🔔 알림 받을래요?</h2>
      <p className="note">이번 주 미스터리 지역, 스트릭이 끊기기 전, 계절 테마가 열릴 때만 알려 드려요. 하루 한 번까지, 밤 10시~아침 8시에는 보내지 않아요.</p>
      <div className="row">
        <button className="btn primary sm" id="push-yes" type="button" onClick={() => void accept()}>예</button>
        <button className="btn sm" id="push-later" type="button" onClick={() => later('dismissed')}>나중에</button>
      </div>
    </div>
  );
}
