import { useUiStore } from '../../../store/uiStore';
import { AccountCard } from './AccountCard';
import { BadgesCard } from './BadgesCard';
import { RecapCard } from './RecapCard';
import { ShareCard } from './ShareCard';
import { TitlesCard } from './TitlesCard';

/** 프로필 탭: 계정 · 자랑하기(서버 카드·공개 범위) · 칭호 · 뱃지 · 연간 리캡 */
export function ProfileTab() {
  const tab = useUiStore(state => state.tab);
  return (
    <section id="tab-profile" hidden={tab !== 'profile'} className="grid2">
      <div className="side">
        <AccountCard />
        <ShareCard active={tab === 'profile'} />
        <TitlesCard />
        <BadgesCard />
      </div>
      <RecapCard />
    </section>
  );
}
