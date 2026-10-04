import { useEffect, useRef, type ReactNode } from 'react';
import { MAP_HEIGHT, MAP_WIDTH, MapEngine, type MapHandlers, type MysteryMark, type RegionPaint } from '../../../shared/lib/map/mapEngine';
import type { Catalog } from '../../../shared/lib/region/catalog';
import { useUiStore } from '../../../store/uiStore';

interface MapViewProps {
  catalog: Catalog | null;
  paint: RegionPaint;
  /** 캐릭터가 설 지역(가장 최근 체크인) */
  characterCode: string | null;
  /** 캐릭터 SVG 조각(착용이 바뀔 때만 달라진다) */
  characterLook: string;
  handlers: MapHandlers;
  /** 정복 기록이 있는 시·도(이름) — 테두리 강조 */
  conqueredProvinces: ReadonlySet<string>;
  /** 이번 주 미스터리 지역 ❓ 마커 */
  mystery: MysteryMark | null;
  /** 가고 싶은 곳 📍 핀(아직 다녀오지 않은 곳) */
  wishPins: ReadonlySet<string>;
  children?: ReactNode;
}

/**
 * 지도 카드. <svg> 의 자식은 D3 엔진이 소유한다 — React 는 컨테이너만 그리고, 마운트 때 엔진을 한 번 만든 뒤
 * 데이터가 바뀌면 엔진의 update 메서드만 부른다.
 */
export function MapView({ catalog, paint, characterCode, characterLook, handlers, conqueredProvinces, mystery, wishPins, children }: MapViewProps) {
  const svgRef = useRef<SVGSVGElement>(null);
  const tipRef = useRef<HTMLDivElement>(null);
  const cardRef = useRef<HTMLDivElement>(null);
  const engineRef = useRef<MapEngine | null>(null);
  const mapCommand = useUiStore(state => state.mapCommand);

  useEffect(() => {
    if (!catalog || !svgRef.current || !tipRef.current || !cardRef.current) return undefined;
    const engine = new MapEngine(svgRef.current, tipRef.current, cardRef.current, catalog.features, handlers);
    engineRef.current = engine;
    return () => {
      engine.destroy();
      engineRef.current = null;
    };
    // 엔진은 카탈로그가 정해지면 한 번만 만든다(콜백은 아래 setHandlers 로 갈아 끼운다)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [catalog]);

  useEffect(() => {
    engineRef.current?.setHandlers(handlers);
  }, [handlers]);

  useEffect(() => {
    engineRef.current?.setPaint(paint);
  }, [paint]);

  useEffect(() => {
    engineRef.current?.setConqueredProvinces(conqueredProvinces);
  }, [conqueredProvinces, catalog]);

  useEffect(() => {
    engineRef.current?.setMystery(mystery);
  }, [mystery, catalog]);

  useEffect(() => {
    engineRef.current?.setWishPins(wishPins);
  }, [wishPins, catalog]);

  useEffect(() => {
    // 같은 목적지면 엔진이 아무것도 하지 않는다(이동 중 재렌더가 애니메이션을 다시 걸지 않게)
    engineRef.current?.placeCharacter(characterCode, characterLook, true);
  }, [characterCode, characterLook]);

  useEffect(() => {
    const engine = engineRef.current;
    if (!engine || !mapCommand) return;
    if (mapCommand.kind === 'zoom') engine.zoomTo(mapCommand.codes, mapCommand.pad);
    else if (mapCommand.kind === 'reset') engine.resetView();
    else if (mapCommand.kind === 'ping') engine.ping(mapCommand.code);
    else if (mapCommand.kind === 'flash-provinces') engine.flashProvinces(mapCommand.provinces);
    else if (mapCommand.kind === 'to-character' && engine.characterRegion) engine.zoomTo([engine.characterRegion], 0.25);
  }, [mapCommand]);

  return (
    <div className="mapcard" ref={cardRef}>
      <svg id="map" ref={svgRef} viewBox={`0 0 ${MAP_WIDTH} ${MAP_HEIGHT}`} role="img" aria-label="대한민국 시군구 지도" />
      {children}
      <div className="tip" id="tip" ref={tipRef} />
    </div>
  );
}
