import { toClientCode } from '../../../api/client';
import { regionLabel } from '../../../shared/lib/region/catalog';
import { Bar } from '../../../shared/ui/Bar';
import { useUiStore } from '../../../store/uiStore';
import { useCatalog } from '../../../shared/queries/catalog';
import { useCollection } from '../../../shared/queries/collection';
import { SeasonSection } from './SeasonSection';

/** 도감 탭: 테마 세트(지도 기준 서버 값) — 지역을 누르면 지도에서 보여 준다 */
export function CollectionTab() {
  const tab = useUiStore(state => state.tab);
  const showOnMap = useUiStore(state => state.showOnMap);
  const catalog = useCatalog();
  const { data: collection } = useCollection();
  return (
    <section id="tab-sets" hidden={tab !== 'sets'}>
      <div className="card" style={{ marginBottom: 14 }}>
        <h2>테마 도감 <span id="sets-sum">{collection ? `${collection.completed} / ${collection.total} 완성` : ''}</span></h2>
        <p className="sub" style={{ margin: 0 }}>같은 테마의 지역을 모두 모으면 세트가 완성되고 칭호와 보너스 XP를 받습니다. 지역을 누르면 지도에서 보여줘요.</p>
      </div>
      <SeasonSection />
      <div className="sets" id="sets">
        {!collection ? <p className="empty">불러오는 중…</p> : collection.sets.map(set => (
          <div key={set.id} className={`set ${set.completed ? 'done' : ''}`} data-set={set.id}>
            <h3>{set.name}<span className="have">{set.have} / {set.total}</span></h3>
            <p className="d">{set.desc}</p>
            <Bar percent={Math.round(100 * set.have / set.total)} gold={set.completed} />
            <div className="slots">
              {set.regions.map(region => {
                const code = toClientCode(region.code);
                const feature = catalog?.byCode.get(code);
                return (
                  <button key={region.code} className={`slot ${region.collected ? 'on' : ''}`} data-show={code} title={feature ? regionLabel(feature) : region.name} onClick={() => showOnMap(code)}>
                    {region.name}
                  </button>
                );
              })}
            </div>
            <div className="reward">
              {set.completed ? <b>완성 · 칭호 「{set.title}」 +{set.rewardXp} XP</b> : `보상: 칭호 「${set.title}」 + ${set.rewardXp} XP`}
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
