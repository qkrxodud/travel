import { useState } from 'react';
import { errorToastCode, track } from '../../../api/analytics';
import type { PreferencesRequest, PushKind } from '../../../api/types/notification';
import { pushBrowser } from '../../../shared/lib/pwa/pushBrowser';
import { toast, toastError } from '../../../store/toastStore';
import { useUiStore } from '../../../store/uiStore';
import { OUTCOME_TEXT } from '../model/consent';
import { DEVICE_STATUS_TEXT, deviceStatus, devicesText, kindRows, rulesText, toggled } from '../model/settings';
import { useDeviceSubscription, useDisablePush, useEnablePush, usePreferences, useSavePreferences } from '../queries';

const CANNOT_ENABLE = OUTCOME_TEXT.failed.title;

/** 프로필 탭 알림 설정: 이 기기 켜기·끄기(= 구독 해지) · 종류별 켜고 끄기 · 서버 규칙(조용한 시간·하루 최대) */
export function NotificationSettingsCard() {
  const active = useUiStore(state => state.tab === 'profile');
  const support = pushBrowser.support();
  const device = useDeviceSubscription(active && support === 'supported');
  const preferences = usePreferences(active);
  const save = useSavePreferences();
  const enable = useEnablePush();
  const disable = useDisablePush();
  const status = deviceStatus(support, pushBrowser.permission(), !!device.data);
  const busy = enable.isPending || disable.isPending;
  const current = preferences.data;
  // 저장하는 동안에는 누른 값을 보인다(서버 응답이 오면 그 값으로 — 실패하면 다시 읽어 되돌린다)
  const [saving, setSaving] = useState<PreferencesRequest | null>(null);

  const turnOn = async () => {
    try {
      const outcome = await enable.mutateAsync('settings');
      if (outcome === 'subscribed') toast('🔔', OUTCOME_TEXT.subscribed.title, '이 기기로 알림을 보내요');
      else toast('!', OUTCOME_TEXT.denied.title, OUTCOME_TEXT.denied.detail);
    } catch (error) {
      toast('!', CANNOT_ENABLE, OUTCOME_TEXT.failed.detail);
      track('error_toast', { code: errorToastCode(error) });
    }
  };
  const turnOff = async () => {
    try {
      await disable.mutateAsync();
      toast('✓', '이 기기 알림을 껐어요', '다시 켜면 언제든 받을 수 있어요');
    } catch (error) {
      toastError(error);
    }
  };
  const change = async (kind: PushKind, on: boolean) => {
    if (!current) return;
    const next = toggled(current, kind, on);
    setSaving(next);
    try {
      await save.mutateAsync(next);
    } catch (error) {
      toastError(error, CANNOT_ENABLE);
    } finally {
      setSaving(null);
    }
  };

  return (
    <div className="card push-settings" id="push-settings" data-status={status}>
      <h2>알림 <span>여행과 여행 사이, 하루 한 번까지만</span></h2>
      <p className="note" id="push-status">{DEVICE_STATUS_TEXT[status]}</p>
      {status === 'off' ? (
        <div className="row"><button className="btn primary sm" id="push-enable" type="button" disabled={busy} onClick={() => void turnOn()}>이 기기에서 알림 켜기</button></div>
      ) : null}
      {current ? (
        <>
          <ul className="push-kinds" id="push-kinds">
            {kindRows(saving ?? current).map(row => (
              <li key={row.kind}>
                <label>
                  <input type="checkbox" data-kind={row.kind} checked={row.on} disabled={busy || saving !== null} onChange={event => void change(row.kind, event.target.checked)} />
                  <span><b>{row.label}</b><small>{row.when}</small></span>
                </label>
              </li>
            ))}
          </ul>
          <p className="note" id="push-rules" data-devices={current.devices}>{`${rulesText(current)} ${devicesText(current.devices)}`}</p>
        </>
      ) : null}
      {status === 'on' ? (
        <div className="row"><button className="btn sm" id="push-off" type="button" disabled={busy} onClick={() => void turnOff()}>이 기기 알림 모두 끄기</button></div>
      ) : null}
    </div>
  );
}
