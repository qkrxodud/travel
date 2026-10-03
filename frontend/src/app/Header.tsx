import { useMyTerritory } from '../shared/queries/territory';
import { useProgress } from '../shared/queries/progress';
import { usePercentile } from '../shared/queries/social';
import { useCatalog } from '../shared/queries/catalog';
import { Bar } from '../shared/ui/Bar';
import { freezeBadge } from '../shared/lib/progress/freeze';

const LEVEL_ONE_TITLE = '초보 탐험가';

/** 헤더(브랜드·Lv·칭호)와 요약 줄(정복률·상위 %·스트릭·XP) — 전부 서버 값, 화면은 포맷만 */
export function Header() {
  const catalog = useCatalog();
  const { territory } = useMyTerritory();
  const { data: progress } = useProgress();
  const { data: percentile } = usePercentile();
  const freeze = freezeBadge(progress?.streakFreeze);
  const conquest = territory?.conquest ?? { visited: 0, total: catalog?.features.length ?? 0, percent: 0 };
  const xp = progress?.xp ?? 0;
  const level = progress?.level ?? 1;
  const current = progress?.currentLevelXp ?? 0;
  const next = progress?.nextLevelXp ?? 40;
  const xpPercent = Math.round(100 * (xp - current) / (next - current));
  const computed = !!percentile?.computed;
  return (
    <>
      <header className="top">
        <div className="brand">
          <h1>나의 영토 <small>MVP PROTOTYPE</small></h1>
          <p>다녀온 시·군·구를 칠하면 그곳 특산물로 캐릭터를 꾸밀 수 있어요. 가기 힘든 곳일수록 화려합니다.</p>
        </div>
        <div className="me">
          <div className="avatar">K</div>
          <div><div className="lv" id="lv">Lv.{level}</div><div className="ttl" id="ttl">{progress?.title?.name ?? LEVEL_ONE_TITLE}</div></div>
        </div>
      </header>

      <section className="stats" aria-label="요약">
        <div className="stat">
          <div className="k">전국 정복률</div>
          <div className="v"><span id="s-pct">{conquest.percent}</span>%<em id="s-cnt">{`${conquest.visited} / ${conquest.total}`}</em></div>
          <Bar id="s-bar" percent={conquest.percent} />
        </div>
        <div className="stat">
          <div className="k">전체 유저 중</div>
          <div className="v">상위 <span id="s-top">{computed ? percentile?.topPercent : '—'}</span>%<em id="s-rank">{computed && percentile ? `${(percentile.rank ?? 0).toLocaleString()}위 / ${(percentile.population ?? 0).toLocaleString()}명` : '하루 한 번 집계해요'}</em></div>
        </div>
        <div className="stat">
          <div className="k">탐험 스트릭</div>
          <div className="v">
            <span id="s-streak">{progress?.streak.months ?? 0}</span><em>개월 연속</em>
            <em id="s-freeze" className={`freeze ${freeze.empty ? 'dim' : ''}`} title={freeze.hint} aria-label={`스트릭 보호권 ${progress?.streakFreeze.held ?? 0}개`} data-held={progress?.streakFreeze.held ?? 0}><span className="ico" aria-hidden="true">{freeze.icon}</span>{freeze.count}</em>
          </div>
        </div>
        <div className="stat">
          <div className="k">탐험 XP</div>
          <div className="v"><span id="s-xp">{xp}</span><em id="s-next">{`다음 레벨까지 ${next - xp}`}</em></div>
          <Bar id="s-xpbar" percent={xpPercent} />
        </div>
      </section>
    </>
  );
}
