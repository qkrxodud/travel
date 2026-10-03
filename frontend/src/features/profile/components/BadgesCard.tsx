import { useProgress } from '../../../shared/queries/progress';

/** 뱃지(서버 판정) */
export function BadgesCard() {
  const { data: progress } = useProgress();
  const badges = progress?.badges ?? [];
  return (
    <div className="card">
      <h2>뱃지 <span id="b-cnt">{`${progress?.badgeCount ?? 0} / ${badges.length}`}</span></h2>
      <div className="badges" id="badges">
        {badges.map(badge => (
          <div key={badge.id} className={`badge ${badge.earned ? 'got' : ''}`} data-badge={badge.id}>
            <div className="ico">{badge.ico}</div>
            <div><div className="t">{badge.name}</div><div className="d">{badge.desc}</div></div>
          </div>
        ))}
      </div>
    </div>
  );
}
