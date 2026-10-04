import type { FeedResponse } from '../../../api/types/social';
import { useCatalog } from '../../../shared/queries/catalog';
import { useSetNameLookup } from '../../../shared/queries/collection';
import { useProgress } from '../../../shared/queries/progress';
import { useSeasonNameLookup } from '../../../shared/queries/seasons';
import { avatarColor, displayHandle, feedParts, initialOf } from '../model/social';

/** 친구 소식(내가 팔로우한 사람 · 최근) — 정확한 날짜 없이 상대 시각만 */
export function FeedCard({ feed, loggedIn }: { feed: FeedResponse | null; loggedIn: boolean }) {
  const catalog = useCatalog();
  const setName = useSetNameLookup();
  const { data: progress } = useProgress();
  const badgeName = (badgeId: string) => progress?.badges.find(badge => badge.id === badgeId)?.name;
  const seasonName = useSeasonNameLookup();
  const items = feed?.items ?? [];
  return (
    <div className="card">
      <h2>친구 소식 <span>내가 팔로우한 사람 · 최근</span></h2>
      <ul className="feed" id="feed">
        {items.length ? items.map((item, i) => {
          const parts = feedParts(item, catalog, { setName, badgeName, seasonName });
          return (
            <li key={i} data-kind={item.kind} data-handle={item.handle || ''} data-region={item.regionCode || ''}>
              <span className="av" style={{ background: avatarColor(item.handle) }}>{initialOf(item.handle)}</span>
              <div>
                <b>{displayHandle(item.handle)}</b>님이 {parts.prefix ?? null}{parts.strong !== null ? <b>{parts.strong}</b> : null}{parts.text}
                <time>{item.when}</time>
              </div>
            </li>
          );
        }) : (
          <li className="empty">{loggedIn ? '아직 소식이 없어요 — 친구를 팔로우하면 칠한 곳·완성한 테마·레벨 업이 여기 보여요(비공개 친구 제외).' : '로그인하고 친구를 팔로우하면 친구 소식이 보여요.'}</li>
        )}
      </ul>
    </div>
  );
}
