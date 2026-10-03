import { useSyncExternalStore } from 'react';
import { pixelRenderer, sprites, type PixelItem } from '../lib/pixel';

/** 픽셀 아이템 아이콘(<img class="px">) — 렌더러가 아이템 코드로 메모이즈한다 */
export function ItemImage({ item, className = 'px' }: { item: PixelItem; className?: string }) {
  // 스프라이트가 로드되면 렌더러 캐시가 비워지므로 다시 그린다
  useSyncExternalStore(sprites.subscribe, sprites.version);
  return <img className={className} src={pixelRenderer.itemUrl(item)} alt="" />;
}
