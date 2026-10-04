import { BagTab } from '../features/bag/components/BagTab';
import { CollectionTab } from '../features/collection/components/CollectionTab';
import { CheckinModal } from '../features/map/components/CheckinModal';
import { MapTab } from '../features/map/components/MapTab';
import { ProfileTab } from '../features/profile/components/ProfileTab';
import { InstallBanner } from '../features/pwa/components/InstallBanner';
import { NotificationSettingsCard } from '../features/pwa/components/NotificationSettingsCard';
import { PushPrompt } from '../features/pwa/components/PushPrompt';
import { UpdateBanner } from '../features/pwa/components/UpdateBanner';
import { useSubscriptionResync, useVisitLog } from '../features/pwa/queries';
import { QuestsTab } from '../features/quests/components/QuestsTab';
import { RankTab } from '../features/rank/components/RankTab';
import { MapModal } from '../features/sharedMap/components/MapModal';
import { SharedMapCard } from '../features/sharedMap/components/SharedMapCard';
import { CardModal } from '../shared/ui/CardModal';
import { ToastHost } from '../shared/ui/ToastHost';
import { Header } from './Header';
import { SvgDefs } from './SvgDefs';
import { TabBar } from './TabBar';
import { useAnalytics } from './useAnalytics';
import { useAnnouncements } from './useAnnouncements';
import { useDocumentFlags } from './useDocumentFlags';
import { useIdentityReset, useLoginNotice, useProfileJoin, useTerritoryFallback } from './useSessionEffects';

/**
 * 앱 셸: 헤더 · 요약 · 탭 바 · 탭 6개 · 토스트 · 모달 3개(카드·지도·체크인) · 새 버전 안내. 탭은 모두 그려 두고 hidden 으로 전환한다.
 * PWA(12단계): 방문 기록·구독 다시 보내기, 지도 탭의 알림 질문·설치 권유, 프로필 탭의 알림 설정.
 */
export function App() {
  useAnalytics();
  useIdentityReset();
  useTerritoryFallback();
  useLoginNotice();
  useProfileJoin();
  useAnnouncements();
  useDocumentFlags();
  useVisitLog();
  useSubscriptionResync();
  return (
    <>
      <SvgDefs />
      <div className="wrap">
        <Header />
        <TabBar />
        <MapTab mapCard={<SharedMapCard />} notices={<><PushPrompt /><InstallBanner /></>} />
        <BagTab />
        <CollectionTab />
        <QuestsTab />
        <RankTab />
        <ProfileTab notificationCard={<NotificationSettingsCard />} />
      </div>
      <ToastHost />
      <UpdateBanner />
      <CardModal />
      <MapModal />
      <CheckinModal />
    </>
  );
}
