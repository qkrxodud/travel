import type { ReactNode } from 'react';
import { useUiStore } from '../../../store/uiStore';
import { AccountCard } from './AccountCard';
import { BadgesCard } from './BadgesCard';
import { RecapCard } from './RecapCard';
import { ShareCard } from './ShareCard';
import { TitlesCard } from './TitlesCard';

/** 프로필 탭: 계정 · 자랑하기(서버 카드·공개 범위) · 알림 설정(pwa 기능이 그려 slot 으로) · 칭호 · 뱃지 · 연간 리캡 */
export function ProfileTab({ notificationCard }: { notificationCard?: ReactNode }) {
  const tab = useUiStore(state => state.tab);
  return (
    <section id="tab-profile" hidden={tab !== 'profile'} className="grid2">
      <div className="side">
        <AccountCard />
        <ShareCard active={tab === 'profile'} />
        {notificationCard}
        <TitlesCard />
        <BadgesCard />
      </div>
      <RecapCard />
    </section>
  );
}
