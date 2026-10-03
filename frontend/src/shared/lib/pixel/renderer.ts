/**
 * 캐릭터·아이템·장면 캔버스(프로토타입 charCanvas·itemCanvas·sceneCanvas·charMarkup 이전본).
 * 결과는 착용 조합 키로 메모이즈한다 — 같은 조합이면 다시 그리지 않는다(재렌더마다 그리면 무겁고 지도 위 캐릭터가 깜빡인다).
 */
import { backgroundOps, CHAR_SCALE, CHAR_X, CHAR_Y, cozyScene, GROUND_Y, SCENE_HEIGHT, SCENE_WIDTH, seededRandom, themeOf } from './backgrounds';
import { drawBag, drawHand, drawHat, drawKeyring, drawPet, drawProp, OUTLINE } from './equipment';
import { backgroundThemeKey, HAND_TYPES, HAT_TYPES, lookOf, PET_TYPES, PROP_TYPES, type Look, type PixelItem } from './looks';
import { Painter, type CanvasFactory } from './painter';

export type Gender = 'm' | 'f';

/** 착용 상태(장면 슬롯 + 장식 3칸) */
export interface Equipment {
  gender: Gender;
  hat?: PixelItem;
  hand?: PixelItem;
  badge?: PixelItem;
  back?: PixelItem;
  pet?: PixelItem;
  bg?: PixelItem;
  props: PixelItem[];
}

/** 기본 캐릭터 스프라이트(사용자 제작 시트) — 로드 전이면 null */
export interface SpriteSource {
  image(gender: Gender): CanvasImageSource | null;
}

/** 시트 픽셀 블록 ≈ 4px */
const PX = 4;
type Anchor = readonly [number, number];
interface SpriteFrame {
  height: number;
  hat: Anchor;
  face: Anchor;
  hand: Anchor;
  bag: Anchor;
  ring: Anchor;
  belt: Anchor;
  pet: Anchor;
}
const SPRITES: Readonly<Record<Gender, SpriteFrame>> = {
  m: { height: 174, hat: [46, 52], face: [46, 60], hand: [86, 122], bag: [76, 86], ring: [90, 108], belt: [72, 122], pet: [150, 174] },
  f: { height: 170, hat: [57, 50], face: [57, 60], hand: [96, 118], bag: [84, 82], ring: [98, 102], belt: [80, 118], pet: [158, 170] },
};
/** 캐릭터 캔버스 58×56 유닛(232×224px) */
export const CHAR_UNITS_W = 58;
export const CHAR_UNITS_H = 56;
const CHAR_PX_W = CHAR_UNITS_W * PX;
const CHAR_PX_H = CHAR_UNITS_H * PX;
const SLOT_ORDER = ['hat', 'hand', 'badge', 'back', 'pet', 'bg'] as const;

/** 착용 조합 키(성별 + 슬롯별 아이템 + 장식) */
export function equipmentKey(equipment: Equipment): string {
  return JSON.stringify([equipment.gender, SLOT_ORDER.map(slot => equipment[slot]?.code ?? ''), equipment.props.map(item => item.code)]);
}

const themeColors = (key: string) => themeOf(key);
const lookFor = (item: PixelItem): Look => lookOf(item, themeColors);

/** 손에 드는 큰 소품의 그림판 크기·손 위치 [판, hx, hy] */
const BIG_HAND: Readonly<Record<string, readonly [number, number, number]>> = {
  kite: [40, 10, 32], board: [40, 16, 20], umbrella: [40, 14, 34], stick: [36, 18, 18], rod: [40, 12, 30], balloon: [40, 14, 34], flag: [32, 12, 26],
};

export class PixelRenderer {
  private readonly createCanvas: CanvasFactory;
  private readonly sprites: SpriteSource;
  private readonly canvases = new Map<string, HTMLCanvasElement>();
  private readonly urls = new Map<string, string>();

  constructor(createCanvas: CanvasFactory, sprites: SpriteSource) {
    this.createCanvas = createCanvas;
    this.sprites = sprites;
  }

  /** 스프라이트가 새로 로드되면 그 전에 그린 그림(스프라이트 없이 그린 것)을 버린다. */
  clear(): void {
    this.canvases.clear();
    this.urls.clear();
  }

  /** 기본 캐릭터 + 착용 장비(232×224) */
  character(equipment: Equipment): HTMLCanvasElement {
    const sprite = this.sprites.image(equipment.gender);
    return this.memo('c' + (sprite ? 1 : 0) + equipmentKey(equipment), () => this.paintCharacter(equipment, sprite));
  }

  /** 아이템 아이콘 */
  item(item: PixelItem, scale = 3): HTMLCanvasElement {
    return this.memo('i' + scale + item.code + item.slot, () => this.paintItem(item, scale));
  }

  /** 장면(배경 + 장식 + 캐릭터) */
  scene(equipment: Equipment, scale = 3): HTMLCanvasElement {
    const sprite = this.sprites.image(equipment.gender);
    return this.memo('s' + scale + (sprite ? 1 : 0) + equipmentKey(equipment), () => this.paintScene(equipment, scale));
  }

  itemUrl(item: PixelItem): string {
    return this.memoUrl('i' + item.code + item.slot, () => this.item(item, 3));
  }

  sceneUrl(equipment: Equipment): string {
    const sprite = this.sprites.image(equipment.gender);
    return this.memoUrl('s' + (sprite ? 1 : 0) + equipmentKey(equipment), () => this.scene(equipment, 3));
  }

  /** 지도 위 캐릭터 SVG 조각(생성 마크업 — 사용자 입력 없음). big=false 면 이름표 KOBI 를 단다. */
  characterMarkup(equipment: Equipment, big = false): string {
    const sprite = this.sprites.image(equipment.gender);
    const href = this.memoUrl('c' + (sprite ? 1 : 0) + equipmentKey(equipment), () => this.character(equipment));
    const width = CHAR_UNITS_W;
    const height = CHAR_UNITS_H;
    return `<g class="char " transform="translate(${-width / 2},${-height / 2})">
    <ellipse cx="${width / 2 - 2}" cy="${height - 1}" rx="12" ry="2" fill="rgba(0,0,0,.28)"/>
    <image href="${href}" x="0" y="0" width="${width}" height="${height}" style="image-rendering:pixelated"/>
    ${big ? '' : `<text x="${width / 2}" y="${height + 6}" font-size="4.5" text-anchor="middle" fill="var(--fg)" font-weight="700" letter-spacing=".06em">KOBI</text>`}
  </g>`;
  }

  private memo(key: string, paint: () => HTMLCanvasElement): HTMLCanvasElement {
    const hit = this.canvases.get(key);
    if (hit) return hit;
    const canvas = paint();
    this.canvases.set(key, canvas);
    return canvas;
  }

  private memoUrl(key: string, canvas: () => HTMLCanvasElement): string {
    const hit = this.urls.get(key);
    if (hit) return hit;
    const url = canvas().toDataURL();
    this.urls.set(key, url);
    return url;
  }

  private paintCharacter(equipment: Equipment, sprite: CanvasImageSource | null): HTMLCanvasElement {
    const frame = SPRITES[equipment.gender];
    const canvas = this.createCanvas(CHAR_PX_W, CHAR_PX_H);
    const context = canvas.getContext('2d');
    if (!context) return canvas;
    context.imageSmoothingEnabled = false;
    const originX = 30;
    const originY = CHAR_PX_H - frame.height - 6;
    const pixelAt = ([anchorX, anchorY]: Anchor): [number, number] => [originX + anchorX, originY + anchorY];
    const unitAt = ([anchorX, anchorY]: Anchor): [number, number] => [(originX + anchorX) / PX, (originY + anchorY) / PX];
    const look = (slot: (typeof SLOT_ORDER)[number]) => {
      const worn = equipment[slot];
      return worn ? lookFor(worn) : null;
    };
    // 뒤: 배낭
    const bagLook = look('back');
    if (bagLook) {
      const back = new Painter(CHAR_UNITS_W, CHAR_UNITS_H);
      const [bagX, bagY] = pixelAt(frame.bag);
      drawBag(back, bagLook.primary, bagLook.secondary, bagX / PX, bagY / PX);
      back.silhouette(OUTLINE);
      context.drawImage(back.toCanvas(PX, this.createCanvas), 0, 0);
    }
    // 스프라이트
    if (sprite) context.drawImage(sprite, originX, originY);
    // 앞 오버레이(유닛 = PX)
    const front = new Painter(CHAR_UNITS_W, CHAR_UNITS_H);
    if (equipment.badge) {
      const badgeLook = lookFor(equipment.badge);
      const [ringX, ringY] = unitAt(bagLook ? frame.ring : frame.belt);
      drawKeyring(front, badgeLook.primary, badgeLook.secondary, ringX, ringY);
    }
    const hatLook = look('hat');
    if (hatLook) {
      const [hatX, hatY] = unitAt(frame.hat);
      if (hatLook.type === 'mask') {
        const [faceX, faceY] = unitAt(frame.face);
        drawHat(front, 'mask', hatLook.primary, hatLook.secondary, faceX, faceY - 6);
      } else {
        drawHat(front, hatLook.type, hatLook.primary, hatLook.secondary, hatX, hatY - 1);
      }
    }
    const handLook = look('hand');
    if (handLook) {
      const [handX, handY] = unitAt(frame.hand);
      drawHand(front, handLook.type, handLook.primary, handLook.secondary, handX, handY);
    }
    const petLook = look('pet');
    if (petLook) {
      const [petX, petY] = unitAt(frame.pet);
      drawPet(front, petLook.type, petLook.primary, petLook.secondary, petX, petY);
    }
    front.silhouette(OUTLINE);
    context.drawImage(front.toCanvas(PX, this.createCanvas), 0, 0);
    return canvas;
  }

  private paintItem(item: PixelItem, scale: number): HTMLCanvasElement {
    const look = lookFor(item);
    let pen: Painter;
    if (HAND_TYPES.has(look.type)) {
      const big = BIG_HAND[look.type];
      if (big) {
        pen = new Painter(big[0], big[0]);
        drawHand(pen, look.type, look.primary, look.secondary, big[1], big[2]);
      } else {
        pen = new Painter(22, 22);
        drawHand(pen, look.type, look.primary, look.secondary, 11, 14);
      }
    } else if (HAT_TYPES.has(look.type)) {
      pen = new Painter(34, 26);
      drawHat(pen, look.type, look.primary, look.secondary, look.type === 'bow' || look.type === 'star' ? 9 : 17, 16);
    } else if (look.type === 'keyring') {
      pen = new Painter(14, 14);
      drawKeyring(pen, look.primary, look.secondary, 7, 2);
    } else if (look.type === 'bag') {
      pen = new Painter(20, 24);
      drawBag(pen, look.primary, look.secondary, 10, 13);
    } else if (PET_TYPES.has(look.type)) {
      pen = new Painter(30, 30);
      drawPet(pen, look.type, look.primary, look.secondary, 14, 26);
    } else if (PROP_TYPES.has(look.type)) {
      pen = new Painter(70, 70);
      drawProp(pen, look.type, look.primary, look.secondary, 35, 66);
    } else {
      // 배경·풍경: 하늘·땅·해 미니 타일
      pen = new Painter(30, 30);
      pen.fill(pen.rect(3, 3, 24, 24), look.primary, { flat: true, ol: OUTLINE });
      pen.fill(pen.rect(4, 18, 22, 8), look.secondary, { flat: true, outline: false });
      pen.fill(pen.ellipse(20, 10, 3, 3), look.accent ?? null, { flat: true, outline: false });
    }
    pen.silhouette(OUTLINE);
    const blockSize = Math.max(2, Math.round(scale * 34 / Math.max(pen.width, pen.height)));
    return pen.toCanvas(blockSize, this.createCanvas);
  }

  private paintScene(equipment: Equipment, scale: number): HTMLCanvasElement {
    const theme = themeOf(equipment.bg ? backgroundThemeKey(equipment.bg) : 'plain');
    const canvas = this.createCanvas(SCENE_WIDTH * scale, SCENE_HEIGHT * scale);
    const context = canvas.getContext('2d');
    if (!context) return canvas;
    context.imageSmoothingEnabled = false;
    if (theme.cozy) {
      const backdrop = new Painter(SCENE_WIDTH, SCENE_HEIGHT);
      context.fillStyle = theme.ground[0];
      context.fillRect(0, 0, canvas.width, canvas.height);
      cozyScene(backdrop, theme, seededRandom(5));
      context.drawImage(backdrop.toCanvas(scale, this.createCanvas), 0, 0);
    } else {
      const { bands, px } = backgroundOps(theme);
      const block = scale * 2;
      bands.forEach(([left, top, width, height, color]) => {
        context.fillStyle = color;
        context.fillRect(left * block, top * block, width * block, height * block);
      });
      px.forEach(([dotX, dotY, color]) => {
        context.fillStyle = color;
        context.fillRect(dotX * block, dotY * block, block, block);
      });
    }
    const props = new Painter(SCENE_WIDTH, SCENE_HEIGHT);
    const spots = [22, 150, 178];
    if (theme.cozy) {
      // 울타리(왼쪽 뒤)
      for (let postX = 2; postX < 60; postX += 12) props.fill(props.rect(postX, GROUND_Y - 14, 3, 16), '#8d5524', { ol: OUTLINE });
      props.fill(props.rect(2, GROUND_Y - 11, 58, 2), '#a86a32', { ol: OUTLINE });
      props.fill(props.rect(2, GROUND_Y - 5, 58, 2), '#a86a32', { ol: OUTLINE });
      // 작은 새
      props.fill(props.ellipse(12, GROUND_Y - 17, 2.5, 2), '#4fa3e0', { ol: OUTLINE });
      props.fill(props.ellipse(14.5, GROUND_Y - 19, 1.5, 1.5), '#4fa3e0', { ol: OUTLINE });
      props.dot(16, GROUND_Y - 19, '#f4c542');
    }
    equipment.props.slice(0, 3).forEach((prop, i) => {
      const look = lookFor(prop);
      drawProp(props, look.type, look.primary, look.secondary, spots[i], GROUND_Y + 2);
    });
    props.silhouette();
    if (theme.cozy) {
      const rnd = seededRandom(13);
      const meadow = new Painter(SCENE_WIDTH, SCENE_HEIGHT);
      // 풀숲 · 들꽃
      for (let i = 0; i < 26; i++) {
        const tuftX = Math.floor(rnd() * SCENE_WIDTH);
        const tuftY = GROUND_Y + 2 + Math.floor(rnd() * 18);
        meadow.fill(props.poly([[tuftX, tuftY + 3], [tuftX + 1.5, tuftY - 2], [tuftX + 3, tuftY + 3]]), i % 2 ? '#8bc34a' : '#6aa23a', { flat: true, outline: false });
      }
      for (let i = 0; i < 12; i++) {
        const flowerX = Math.floor(rnd() * SCENE_WIDTH);
        const flowerY = GROUND_Y + 3 + Math.floor(rnd() * 16);
        meadow.fill(props.rect(flowerX, flowerY, 1, 3), '#4caf50', { flat: true, outline: false });
        meadow.fill(props.ellipse(flowerX + 0.5, flowerY - 1.5, 2.2, 2.2), ['#ffffff', '#ffd166', '#f48fb1'][i % 3], { flat: true, outline: false });
        meadow.dot(flowerX, flowerY - 2, '#f4c542');
      }
      context.drawImage(meadow.toCanvas(scale, this.createCanvas), 0, 0);
    }
    const shadowX = (CHAR_X + 20 * CHAR_SCALE) * scale;
    context.drawImage(props.toCanvas(scale, this.createCanvas), 0, 0);
    context.fillStyle = 'rgba(0,0,0,.3)';
    context.beginPath();
    context.ellipse(shadowX, (GROUND_Y + 1) * scale, 20 * scale, 3 * scale, 0, 0, Math.PI * 2);
    context.fill();
    context.drawImage(this.character(equipment), CHAR_X * scale, CHAR_Y * scale, CHAR_UNITS_W * scale * CHAR_SCALE, CHAR_UNITS_H * scale * CHAR_SCALE);
    return canvas;
  }
}
