/** 앱에서 쓰는 카드 캔버스(브라우저 캔버스 + 픽셀 렌더러 아이템 아이콘) */
import { pixelRenderer } from '../pixel';
import { browserCanvas } from '../pixel/painter';
import type { CardCanvas } from './travelCards';

export const cardCanvas: CardCanvas = {
  create: browserCanvas,
  itemCanvas: item => pixelRenderer.item(item, 3),
};

export { recentCard, vsCard } from './travelCards';
