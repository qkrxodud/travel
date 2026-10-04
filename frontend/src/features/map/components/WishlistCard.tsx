import { toClientCode } from '../../../api/client';
import { wishRegionName } from '../../../shared/lib/progress/news';
import { useWishlist } from '../../../shared/queries/wishlist';
import { useUiStore } from '../../../store/uiStore';
import { wishSections } from '../model/wishlist';

/** 사이드 "가고 싶은 곳"(#wish-card) — 남은 곳·다녀온 곳(비공개). 누르면 지도에서 보여 준다 */
export function WishlistCard() {
  const { data: wishlist } = useWishlist();
  const showOnMap = useUiStore(state => state.showOnMap);
  if (!wishlist) return null;
  const sections = wishSections(wishlist);
  return (
    <div className="card wishes" id="wish-card">
      <h2>가고 싶은 곳 <span id="wish-count">{sections.summary}</span></h2>
      {sections.pending.length ? (
        <ul className="wish-list" id="wish-pending">
          {sections.pending.map(item => {
            const code = toClientCode(item.regionCode);
            return (
              <li key={item.regionCode} data-wish={code}>
                <button onClick={() => showOnMap(code)} title="지도에서 보기">📍 {wishRegionName(item)}</button>
              </li>
            );
          })}
        </ul>
      ) : (
        <p className="empty">지역을 골라 "가고 싶어요"를 누르면 여기 모여요. 다녀오면(칠하면) +{wishlist.xpPerWish} XP</p>
      )}
      {sections.visited.length ? (
        <>
          <h3>다녀온 곳 <span id="wish-done-count">{wishlist.fulfilledCount}</span></h3>
          <ul className="wish-list done" id="wish-visited">
            {sections.visited.map(item => {
              const code = toClientCode(item.regionCode);
              return (
                <li key={item.regionCode} data-wish={code}>
                  <button onClick={() => showOnMap(code)} title="지도에서 보기">✓ {wishRegionName(item)}</button>
                </li>
              );
            })}
          </ul>
        </>
      ) : null}
    </div>
  );
}
