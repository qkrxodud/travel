import { BagTab } from '../features/bag/components/BagTab';
import { CollectionTab } from '../features/collection/components/CollectionTab';
import { CheckinModal } from '../features/map/components/CheckinModal';
import { MapTab } from '../features/map/components/MapTab';
import { ProfileTab } from '../features/profile/components/ProfileTab';
import { QuestsTab } from '../features/quests/components/QuestsTab';
import { RankTab } from '../features/rank/components/RankTab';
import { MapModal } from '../features/sharedMap/components/MapModal';
import { SharedMapCard } from '../features/sharedMap/components/SharedMapCard';
import { CardModal } from '../shared/ui/CardModal';
import { ToastHost } from '../shared/ui/ToastHost';
import { Header } from './Header';
import { SvgDefs } from './SvgDefs';
import { TabBar } from './TabBar';
import { useAnnouncements } from './useAnnouncements';
import { useDocumentFlags } from './useDocumentFlags';
import { useIdentityReset, useLoginNotice, useProfileJoin, useTerritoryFallback } from './useSessionEffects';

/** 앱 셸: 헤더 · 요약 · 탭 바 · 탭 6개 · 토스트 · 모달 3개(카드·지도·체크인). 탭은 모두 그려 두고 hidden 으로 전환한다. */
export function App() {
  useIdentityReset();
  useTerritoryFallback();
  useLoginNotice();
  useProfileJoin();
  useAnnouncements();
  useDocumentFlags();
  return (
    <>
      <SvgDefs />
      <div className="wrap">
        <Header />
        <TabBar />
        <MapTab mapCard={<SharedMapCard />} />
        <BagTab />
        <CollectionTab />
        <QuestsTab />
        <RankTab />
        <ProfileTab />
      </div>
      <ToastHost />
      <CardModal />
      <MapModal />
      <CheckinModal />
    </>
  );
}
