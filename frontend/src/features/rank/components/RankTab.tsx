import { useEffect } from 'react';
import { useUiStore } from '../../../store/uiStore';
import { useSession } from '../../../shared/queries/session';
import { useFeed, useFriendRanking, useFriends } from '../queries';
import { CompareCard } from './CompareCard';
import { FeedCard } from './FeedCard';
import { FriendRankingCard } from './FriendRankingCard';
import { FriendsCard } from './FriendsCard';
import { MapRankingCard } from './MapRankingCard';

/** 랭킹 탭(5단계): 친구 랭킹·친구·친구 소식·지도 안 랭킹·영토 비교 — 전부 서버 값 */
export function RankTab() {
  const tab = useUiStore(state => state.tab);
  const active = tab === 'rank';
  const { data: session } = useSession();
  const loggedIn = !!session?.loggedIn;
  const ranking = useFriendRanking(active);
  const friends = useFriends(active);
  const feed = useFeed(active);

  const loaded = ranking.isSuccess && friends.isSuccess && feed.isSuccess && !ranking.isFetching && !friends.isFetching && !feed.isFetching;
  const revision = ranking.dataUpdatedAt + friends.dataUpdatedAt + feed.dataUpdatedAt;
  useEffect(() => {
    // 소셜 값을 다 읽었다는 표식(E2E·화면 확인용, 프로토타입 data-social)
    if (loaded) document.documentElement.dataset.social = String(revision);
  }, [loaded, revision]);

  return (
    <section id="tab-rank" hidden={!active} className="grid2">
      <div className="side">
        <FriendRankingCard ranking={ranking.data ?? null} />
        <FriendsCard friends={friends.data ?? null} loggedIn={loggedIn} />
        <FeedCard feed={feed.data ?? null} loggedIn={loggedIn} />
        <MapRankingCard active={active} />
      </div>
      <CompareCard />
    </section>
  );
}
