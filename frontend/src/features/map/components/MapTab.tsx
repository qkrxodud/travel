import { useEffect, useMemo, useSyncExternalStore, type ReactNode } from 'react';
import { pixelRenderer, sprites } from '../../../shared/lib/pixel';
import { useUiStore } from '../../../store/uiStore';
import { useEquipment } from '../../../shared/queries/wardrobe';
import { useCatalog } from '../../../shared/queries/catalog';
import { useCollection, useSetNameLookup } from '../../../shared/queries/collection';
import { claimMaps, latestVisitCode, regionTip } from '../model/territory';
import { useMyTerritory } from '../../../shared/queries/territory';
import { useMapActions } from '../queries';
import { ExplorationLog } from './ExplorationLog';
import { MapTools } from './MapTools';
import { MapView } from './MapView';
import { ProvinceProgress } from './ProvinceProgress';
import { RegionDetail } from './RegionDetail';
import { SampleNote } from './SampleNote';

const EMPTY = new Set<string>();

/** 지도 탭: 왼쪽 지도, 오른쪽 기록 칸. 지도 카드(공유 지도)는 sharedMap 기능이 그려 slot 으로 넣는다. */
export function MapTab({ mapCard }: { mapCard: ReactNode }) {
  const tab = useUiStore(state => state.tab);
  const selected = useUiStore(state => state.selected);
  const highlight = useUiStore(state => state.highlight);
  const catalog = useCatalog();
  const { mapId, territory, detail, visits } = useMyTerritory();
  const { data: collection } = useCollection();
  const setName = useSetNameLookup();
  const equipment = useEquipment(catalog, setName);
  useSyncExternalStore(sprites.subscribe, sprites.version);
  const { clickRegion } = useMapActions(catalog, visits, mapId);

  const mine = useMemo(() => (visits ? new Set(visits.keys()) : EMPTY), [visits]);
  const claims = useMemo(() => claimMaps(territory, detail), [territory, detail]);
  const paint = useMemo(() => ({ mine, selected, highlight, claimColor: claims.color, claimer: claims.claimer }), [mine, selected, highlight, claims]);
  const sets = collection?.sets;
  const handlers = useMemo(() => ({
    onRegionClick: clickRegion,
    describe: (code: string) => {
      const feature = catalog?.byCode.get(code);
      return feature ? regionTip(feature, mine.has(code), sets ?? []) : code;
    },
  }), [clickRegion, catalog, mine, sets]);
  // 캐릭터 그림은 렌더러가 착용 조합 키로 메모이즈한다(같은 조합이면 같은 문자열 → 엔진이 갈아 끼우지 않는다).
  // 스프라이트가 로드되면(useSyncExternalStore) 다시 그린다.
  const characterLook = pixelRenderer.characterMarkup(equipment);
  const characterCode = visits ? latestVisitCode(visits) : null;

  useEffect(() => {
    if (!catalog || !territory || !visits) return;
    // 지도에 영토가 칠해졌다 — E2E·화면 확인용 표식(프로토타입과 같은 data-territory·data-map-id)
    document.documentElement.dataset.territory = 'ready';
    document.documentElement.dataset.mapId = territory.mapId;
  }, [catalog, territory, visits]);

  return (
    <section id="tab-map" className="main" hidden={tab !== 'map'}>
      <MapView catalog={catalog} paint={paint} characterCode={characterCode} characterLook={characterLook} handlers={handlers}>
        <MapTools />
        <div className="maplegend"><span>미탐험</span><span className="a">내 영토</span><span className="l">전설 풍경 지역</span></div>
      </MapView>
      <aside className="side">
        {mapCard}
        <RegionDetail />
        <ProvinceProgress />
        <ExplorationLog />
        <SampleNote />
      </aside>
    </section>
  );
}
