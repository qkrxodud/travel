/**
 * 이 기기·브라우저가 PWA·웹 푸시를 어디까지 지원하는지(12단계). 판단은 순수 함수, 브라우저 값 읽기는 readDevice 한 곳.
 *
 * - 웹 푸시는 서비스워커 + PushManager + Notification 이 모두 있고 보안 출처(HTTPS·localhost)여야 한다.
 * - iOS·iPadOS 는 홈 화면에 추가한 앱(standalone)에서만 웹 푸시가 된다 — 사파리 탭에서는 "홈 화면에 추가" 안내만.
 * - 서비스워커는 빌드 산출물에서만 등록한다(개발 서버 npm run dev 에는 sw.js 가 없다) → 개발 중에는 알림 미지원으로 본다.
 */
export type PushSupport = 'supported' | 'ios-needs-install' | 'unsupported';

export interface DeviceTraits {
  secure: boolean;
  serviceWorker: boolean;
  pushManager: boolean;
  notification: boolean;
  ios: boolean;
  standalone: boolean;
  /** 이 화면이 서비스워커를 등록했는지(빌드 산출물만 등록한다) */
  workerEnabled: boolean;
}

export function pushSupport(traits: DeviceTraits): PushSupport {
  if (traits.ios && !traits.standalone) return 'ios-needs-install';
  if (!traits.workerEnabled || !traits.secure || !traits.serviceWorker || !traits.pushManager || !traits.notification) return 'unsupported';
  return 'supported';
}

/** iPhone·iPad(데스크톱 모드 iPad 는 MacIntel + 터치) */
export function isIosDevice(userAgent: string, platform: string, maxTouchPoints: number): boolean {
  return /iPad|iPhone|iPod/.test(userAgent) || (platform === 'MacIntel' && maxTouchPoints > 1);
}

export function readDevice(workerEnabled: boolean): DeviceTraits {
  const nav = typeof navigator === 'undefined' ? null : navigator;
  const win = typeof window === 'undefined' ? null : window;
  const standaloneFlag = !!(nav && (nav as Navigator & { standalone?: boolean }).standalone);
  const displayStandalone = !!win?.matchMedia?.('(display-mode: standalone)').matches;
  return {
    secure: !!win?.isSecureContext,
    serviceWorker: !!nav && 'serviceWorker' in nav,
    pushManager: !!win && 'PushManager' in win,
    notification: !!win && 'Notification' in win,
    ios: !!nav && isIosDevice(nav.userAgent, nav.platform, nav.maxTouchPoints ?? 0),
    standalone: standaloneFlag || displayStandalone,
    workerEnabled,
  };
}
