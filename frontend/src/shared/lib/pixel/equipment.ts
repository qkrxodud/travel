/**
 * 장비 그림(코지 여행자 룩) — 프로토타입 drawHand·drawHat·drawBag·drawKeyring·drawPet·drawProp 이전본.
 * 좌표·색·순서는 프로토타입과 같다(pixelParity 테스트가 원본과 픽셀 단위로 비교한다).
 */
import { darken, lighten } from './color';
import type { FillStyle, Painter } from './painter';

/** 외곽선 색(갈색 잉크) */
export const OUTLINE = '#3b2a22';
const FLAT: FillStyle = { flat: true, outline: false };

/** 손에 드는 것: (hx, hy) 손 중심 */
export function drawHand(pen: Painter, type: string, primary: string, secondary: string, hx: number, hy: number): void {
  const outlined: FillStyle = { ol: OUTLINE };
  const bold: FillStyle = { ol: OUTLINE, wide: true };
  const round = Math.round;
  if (type === 'map') {
    pen.fill(pen.poly([[hx - 5, hy - 6], [hx + 5, hy - 7], [hx + 5, hy + 1], [hx - 5, hy + 2]]), '#f3e2b3', bold);
    pen.fill(pen.rect(round(hx - 3), round(hy - 4), 2, 2), primary, FLAT);
    pen.fill(pen.line(hx - 2, hy - 2, hx + 3, hy - 1, 1), secondary, FLAT);
    pen.dot(hx + 2, hy - 5, '#e63946');
  } else if (type === 'camera') {
    pen.fill(pen.rect(round(hx - 6), round(hy - 4), 12, 8), '#3b3542', bold);
    pen.fill(pen.rect(round(hx - 6), round(hy - 2), 12, 3), primary, FLAT);
    pen.fill(pen.ellipse(hx, hy, 3, 3), '#2b3542', outlined);
    pen.fill(pen.ellipse(hx, hy, 1.6, 1.6), '#90caf9', FLAT);
    pen.dot(hx - 1, hy - 1, '#ffffff');
    pen.fill(pen.rect(round(hx + 2), round(hy - 6), 3, 2), '#3b3542', outlined);
    pen.fill(pen.rect(round(hx - 5), round(hy - 6), 2, 2), '#f4c542', FLAT);
  } else if (type === 'stick') {
    pen.fill(pen.line(hx + 1, hy - 14, hx - 1, hy + 14, 2.2), primary, outlined);
    pen.fill(pen.ellipse(hx + 1, hy - 14, 2, 2), secondary, outlined);
  } else if (type === 'umbrella') {
    pen.fill(pen.line(hx, hy, hx + 3, hy - 14, 1.6), '#5c3b2e', outlined);
    pen.fill(pen.poly([[hx - 9, hy - 12], [hx - 4, hy - 18], [hx + 3, hy - 20], [hx + 10, hy - 18], [hx + 15, hy - 12]]), primary, bold);
    [hx - 5, hx + 3, hx + 11].forEach(ribX => pen.fill(pen.line(ribX, hy - 13, hx + 3, hy - 19, 1), secondary, FLAT));
  } else if (type === 'lantern') {
    pen.fill(pen.line(hx, hy, hx + 2, hy - 5, 1.2), '#5c3b2e', outlined);
    pen.fill(pen.ellipse(hx + 2, hy - 6, 2, 1.2), '#5c3b2e', outlined);
    pen.fill(pen.rect(round(hx - 4), round(hy), 9, 2), '#3b3542', outlined);
    pen.fill(pen.poly([[hx - 4, hy + 2], [hx + 5, hy + 2], [hx + 4, hy + 11], [hx - 3, hy + 11]]), primary, bold);
    pen.fill(pen.rect(round(hx - 2), round(hy + 4), 4, 5), '#fff3b0', FLAT);
    pen.fill(pen.rect(round(hx - 1), round(hy + 5), 2, 3), '#ffd166', FLAT);
    pen.fill(pen.rect(round(hx - 4), round(hy + 11), 9, 2), '#3b3542', outlined);
  } else if (type === 'cup') {
    pen.fill(pen.poly([[hx - 4, hy - 7], [hx + 4, hy - 7], [hx + 3, hy + 3], [hx - 3, hy + 3]]), primary, bold);
    pen.fill(pen.rect(round(hx - 4), round(hy - 8), 8, 2), secondary, outlined);
    pen.fill(pen.line(hx + 1, hy - 9, hx + 2, hy - 13, 1.2), secondary, FLAT);
  } else if (type === 'rod') {
    pen.fill(pen.line(hx, hy, hx + 10, hy - 18, 1.6), '#8d5524', outlined);
    pen.fill(pen.line(hx + 10, hy - 18, hx + 12, hy - 4, 0.8), '#cfd8e3', FLAT);
    pen.fill(pen.ellipse(hx + 12, hy - 2, 3.5, 2), primary, outlined);
    pen.fill(pen.poly([[hx + 9, hy - 2], [hx + 6, hy - 4], [hx + 6, hy]]), primary, outlined);
    pen.dot(hx + 14, hy - 3, secondary);
  } else if (type === 'kite') {
    pen.fill(pen.line(hx, hy, hx + 12, hy - 22, 0.8), '#cfd8e3', FLAT);
    pen.fill(pen.poly([[hx + 12, hy - 30], [hx + 18, hy - 22], [hx + 12, hy - 14], [hx + 6, hy - 22]]), primary, bold);
    pen.fill(pen.line(hx + 6, hy - 22, hx + 18, hy - 22, 1), secondary, FLAT);
    pen.fill(pen.line(hx + 12, hy - 30, hx + 12, hy - 14, 1), secondary, FLAT);
  } else if (type === 'bouquet') {
    pen.fill(pen.poly([[hx - 3, hy + 1], [hx + 3, hy + 1], [hx + 1, hy - 7], [hx - 1, hy - 7]]), '#2e7d32', outlined);
    ([[hx - 4, hy - 9], [hx, hy - 11], [hx + 4, hy - 9], [hx - 2, hy - 6], [hx + 2, hy - 6]] as const)
      .forEach(([flowerX, flowerY], i) => pen.fill(pen.ellipse(flowerX, flowerY, 2.2, 2.2), i % 2 ? secondary : primary, outlined));
    pen.fill(pen.rect(round(hx - 3), round(hy - 1), 6, 2), '#e9c46a', outlined);
  } else if (type === 'book') {
    pen.fill(pen.rect(round(hx - 5), round(hy - 6), 10, 8), primary, bold);
    pen.fill(pen.rect(round(hx - 3), round(hy - 4), 6, 1), secondary, FLAT);
    pen.fill(pen.rect(round(hx - 3), round(hy - 2), 4, 1), secondary, FLAT);
  } else if (type === 'ukulele') {
    pen.fill(pen.line(hx + 2, hy - 2, hx + 8, hy - 14, 2), '#5c3b2e', outlined);
    pen.fill(pen.ellipse(hx, hy + 2, 5, 6), primary, bold);
    pen.fill(pen.ellipse(hx, hy + 2, 2, 2), '#2b3542', outlined);
    pen.fill(pen.rect(round(hx + 7), round(hy - 16), 3, 3), secondary, outlined);
  } else if (type === 'telescope') {
    pen.fill(pen.line(hx - 4, hy + 2, hx + 6, hy - 8, 3.5), primary, outlined);
    pen.fill(pen.ellipse(hx + 6, hy - 8, 2.5, 2.5), secondary, outlined);
  } else if (type === 'flag') {
    pen.fill(pen.line(hx, hy + 6, hx, hy - 16, 1.4), '#8d5524', outlined);
    pen.fill(pen.poly([[hx + 1, hy - 16], [hx + 12, hy - 13], [hx + 1, hy - 9]]), primary, bold);
    pen.fill(pen.ellipse(hx + 5, hy - 13, 1.5, 1.5), secondary, FLAT);
  } else if (type === 'fan') {
    pen.fill(pen.line(hx, hy, hx, hy - 5, 1.2), '#8d5524', outlined);
    const points: [number, number][] = [];
    for (let i = 0; i <= 6; i++) {
      const angle = -Math.PI * 0.85 + i * (Math.PI * 0.7) / 6;
      points.push([hx + Math.cos(angle) * 9, hy - 5 + Math.sin(angle) * 9]);
    }
    points.push([hx, hy - 5]);
    pen.fill(pen.poly(points), primary, bold);
    pen.fill(pen.ellipse(hx, hy - 10, 2, 2), secondary, FLAT);
  } else if (type === 'balloon') {
    pen.fill(pen.line(hx, hy, hx + 4, hy - 14, 0.8), '#9aa7b5', FLAT);
    pen.fill(pen.ellipse(hx + 4, hy - 19, 4.5, 5.5), primary, bold);
    pen.fill(pen.ellipse(hx + 2.5, hy - 21, 1.2, 1.5), '#ffffff', FLAT);
    pen.dot(hx + 4, hy - 13, secondary);
  } else if (type === 'board') {
    pen.fill(pen.poly([[hx + 4, hy - 22], [hx + 8, hy - 10], [hx + 7, hy + 10], [hx + 4, hy + 14], [hx + 1, hy + 10], [hx, hy - 10]]), primary, bold);
    pen.fill(pen.line(hx + 4, hy - 18, hx + 4, hy + 10, 1), secondary, FLAT);
  } else if (type === 'basket') {
    pen.fill(pen.poly([[hx - 6, hy + 2], [hx + 6, hy + 2], [hx + 5, hy + 9], [hx - 5, hy + 9]]), '#c9a24c', bold);
    pen.fill(pen.line(hx - 4, hy + 2, hx, hy - 3, 1.2), '#8d5524', outlined);
    pen.fill(pen.line(hx + 4, hy + 2, hx, hy - 3, 1.2), '#8d5524', outlined);
    [hx - 3, hx, hx + 3].forEach(fruitX => pen.fill(pen.ellipse(fruitX, hy + 1, 1.6, 1.6), primary, outlined));
  }
}

/** 모자: (cx) 머리 중심, (top) 챙이 닿는 선. 둥근 형태, 2단 명암, 좌상단 하이라이트 */
export function drawHat(pen: Painter, type: string, primary: string, secondary: string, cx: number, top: number): void {
  const bold: FillStyle = { ol: OUTLINE, wide: true };
  const outlined: FillStyle = { ol: OUTLINE };
  const shine = lighten(primary, 0.35);
  const shade = darken(primary, 0.18);
  const round = Math.round;
  const sparkle = (atX: number, atY: number) => {
    pen.dot(atX, atY, shine);
    pen.dot(atX + 1, atY, shine);
  };
  if (type === 'straw') {
    pen.fill(pen.ellipse(cx, top + 1, 14, 3), primary, bold);
    pen.fill(pen.ellipse(cx, top - 3, 8, 5), primary, bold);
    pen.fill(pen.rect(round(cx - 8), top - 2, 16, 2), '#e63946', FLAT);
    pen.fill(pen.ellipse(cx, top + 1, 14, 3), null, {});
    pen.fill(pen.ellipse(cx, top + 1, 14, 3), primary, outlined);
    pen.fill(pen.rect(round(cx - 13), top + 1, 26, 1), shade, FLAT);
    sparkle(cx - 5, top - 6);
  } else if (type === 'cap') {
    pen.fill(pen.ellipse(cx, top - 2, 9, 6), primary, bold);
    pen.fill(pen.poly([[cx - 3, top + 1], [cx + 13, top + 1], [cx + 13, top + 3], [cx - 3, top + 3]]), shade, outlined);
    pen.fill(pen.rect(round(cx - 1), top - 8, 2, 2), shade, FLAT);
    pen.fill(pen.rect(round(cx - 2), top - 4, 1, 4), darken(primary, 0.25), FLAT);
    sparkle(cx - 5, top - 5);
  } else if (type === 'beret') {
    pen.fill(pen.ellipse(cx + 1, top - 3, 10, 5), primary, bold);
    pen.fill(pen.ellipse(cx - 1, top, 7, 2), shade, outlined);
    pen.fill(pen.rect(round(cx + 1), top - 9, 1, 2), darken(primary, 0.3), FLAT);
    sparkle(cx - 5, top - 5);
  } else if (type === 'bucket') {
    pen.fill(pen.poly([[cx - 7, top - 7], [cx + 7, top - 7], [cx + 9, top], [cx - 9, top]]), primary, bold);
    pen.fill(pen.poly([[cx - 11, top], [cx + 11, top], [cx + 10, top + 3], [cx - 10, top + 3]]), shade, outlined);
    pen.fill(pen.rect(round(cx - 8), top - 2, 16, 1), darken(primary, 0.3), FLAT);
    sparkle(cx - 5, top - 5);
  } else if (type === 'fedora') {
    pen.fill(pen.ellipse(cx, top, 13, 2.5), shade, bold);
    pen.fill(pen.poly([[cx - 7, top], [cx - 6, top - 8], [cx + 6, top - 8], [cx + 7, top]]), primary, bold);
    pen.fill(pen.rect(round(cx - 7), top - 3, 14, 2), darken(primary, 0.35), FLAT);
    sparkle(cx - 4, top - 6);
  } else if (type === 'sunhat') {
    pen.fill(pen.ellipse(cx, top + 1, 16, 3.5), primary, bold);
    pen.fill(pen.ellipse(cx, top - 3, 8, 5), primary, bold);
    pen.fill(pen.rect(round(cx - 8), top - 2, 16, 2), secondary, FLAT);
    sparkle(cx - 5, top - 6);
  } else if (type === 'beanie') {
    pen.fill(pen.ellipse(cx, top - 3, 9, 6), primary, bold);
    pen.fill(pen.rect(round(cx - 10), top - 1, 20, 3), shade, outlined);
    for (let stitchX = cx - 8; stitchX < cx + 9; stitchX += 2) pen.dot(stitchX, top, darken(primary, 0.3));
    pen.fill(pen.ellipse(cx, top - 9, 2, 2), secondary, outlined);
    sparkle(cx - 5, top - 6);
  } else if (type === 'bandana') {
    pen.fill(pen.rect(round(cx - 9), top - 2, 18, 3), primary, bold);
    pen.fill(pen.poly([[cx + 8, top - 1], [cx + 14, top - 4], [cx + 13, top + 1]]), primary, outlined);
    for (let dotX = cx - 7; dotX < cx + 8; dotX += 3) pen.dot(dotX, top - 1, secondary);
  } else if (type === 'flower') {
    pen.fill(pen.rect(round(cx - 9), top - 1, 18, 1.5), '#2e7d32', FLAT);
    [-6, -2, 2, 6].forEach((offset, i) => {
      pen.fill(pen.ellipse(cx + offset, top - 1, 2.2, 2.2), i % 2 ? '#ffffff' : primary, outlined);
      pen.dot(cx + offset, top - 1, '#f4c542');
    });
  } else if (type === 'conical') {
    pen.fill(pen.poly([[cx, top - 9], [cx + 13, top + 1], [cx - 13, top + 1]]), primary, bold);
    pen.fill(pen.rect(round(cx - 13), top, 26, 1.5), secondary, FLAT);
    pen.fill(pen.line(cx, top - 8, cx - 6, top, 1), darken(primary, 0.25), FLAT);
  } else if (type === 'crown') {
    pen.fill(pen.poly([[cx - 7, top], [cx - 7, top - 7], [cx - 3.5, top - 3], [cx, top - 9], [cx + 3.5, top - 3], [cx + 7, top - 7], [cx + 7, top]]), primary, bold);
    ([[cx - 4, top - 2], [cx, top - 2.5], [cx + 4, top - 2]] as const).forEach(([gemX, gemY]) => pen.fill(pen.ellipse(gemX, gemY, 1.2, 1.2), secondary, FLAT));
  } else if (type === 'mask') {
    pen.fill(pen.ellipse(cx, top + 6, 7.5, 8), primary, bold);
    ([[cx - 3, top + 4], [cx + 3, top + 4]] as const).forEach(([eyeX, eyeY]) => pen.fill(pen.ellipse(eyeX, eyeY, 1.8, 1.2), secondary, { flat: true }));
    pen.fill(pen.ellipse(cx, top + 9, 3, 1.2), secondary, { flat: true });
  } else if (type === 'circlet') {
    pen.fill(pen.rect(round(cx - 9), top - 2, 18, 2), primary, outlined);
    pen.fill(pen.ellipse(cx, top - 1, 2, 2), secondary, outlined);
  } else if (type === 'bow') {
    // 머리 옆 장식
    const bowX = cx + 8;
    const bowY = top - 6;
    pen.fill(pen.ellipse(bowX - 3, bowY, 3, 2.2), primary, outlined);
    pen.fill(pen.ellipse(bowX + 3, bowY, 3, 2.2), primary, outlined);
    pen.fill(pen.ellipse(bowX, bowY, 1.5, 1.5), secondary, outlined);
    pen.fill(pen.poly([[bowX - 1, bowY + 1], [bowX - 3, bowY + 5], [bowX, bowY + 2]]), primary, outlined);
    pen.fill(pen.poly([[bowX + 1, bowY + 1], [bowX + 3, bowY + 5], [bowX, bowY + 2]]), primary, outlined);
    sparkle(bowX - 4, bowY - 1);
  } else if (type === 'ears') {
    ([[cx - 7, top - 6], [cx + 7, top - 6]] as const).forEach(([earX, earY]) => {
      pen.fill(pen.poly([[earX - 4, earY + 3], [earX, earY - 6], [earX + 4, earY + 3]]), primary, bold);
      pen.fill(pen.poly([[earX - 2, earY + 2], [earX, earY - 2], [earX + 2, earY + 2]]), secondary, FLAT);
    });
  } else if (type === 'headphones') {
    pen.fill(pen.ellipse(cx, top - 2, 11, 9), null, {});
    pen.fill(pen.ellipse(cx, top - 2, 11, 9), primary, outlined);
    pen.fill(pen.ellipse(cx, top - 1, 9, 8), null, {});
    ([[cx - 10, top + 2], [cx + 10, top + 2]] as const).forEach(([cupX, cupY]) => {
      pen.fill(pen.ellipse(cupX, cupY, 3, 3.5), primary, bold);
      pen.fill(pen.ellipse(cupX, cupY, 1.5, 2), secondary, FLAT);
    });
  } else if (type === 'hornband') {
    pen.fill(pen.rect(round(cx - 9), top - 2, 18, 2), primary, outlined);
    ([[cx - 5, top - 3], [cx + 5, top - 3]] as const).forEach(([hornX, hornY]) => pen.fill(pen.poly([[hornX - 2, hornY], [hornX, hornY - 5], [hornX + 2, hornY]]), secondary, outlined));
  } else if (type === 'star') {
    const starX = cx + 8;
    const starY = top - 6;
    pen.fill(pen.poly([[starX, starY - 4], [starX + 1.2, starY - 1.2], [starX + 4, starY - 1], [starX + 2, starY + 1], [starX + 2.5, starY + 4], [starX, starY + 2.2], [starX - 2.5, starY + 4], [starX - 2, starY + 1], [starX - 4, starY - 1], [starX - 1.2, starY - 1.2]]), primary, outlined);
    sparkle(starX - 1, starY - 1);
  }
}

/** 배낭(등 뒤): (centerX, centerY) 중심 */
export function drawBag(pen: Painter, primary: string, secondary: string, centerX: number, centerY: number): void {
  const round = Math.round;
  pen.fill(pen.rect(round(centerX - 4), round(centerY - 5), 8, 11), primary, { ol: OUTLINE, wide: true }); // 몸통
  pen.fill(pen.rect(round(centerX - 4), round(centerY - 7), 8, 3), darken(primary, 0.12), { ol: OUTLINE }); // 윗덮개
  pen.fill(pen.rect(round(centerX - 2), round(centerY + 1), 5, 4), secondary, { ol: OUTLINE }); // 앞주머니
  pen.fill(pen.rect(round(centerX - 1), round(centerY - 7), 2, 2), '#f4c542', FLAT); // 버클
  pen.fill(pen.rect(round(centerX - 3), round(centerY - 4), 1, 5), lighten(primary, 0.35), FLAT); // 하이라이트
  pen.fill(pen.poly([[centerX - 2, centerY - 8], [centerX + 2, centerY - 8], [centerX + 1, centerY - 10], [centerX - 1, centerY - 10]]), darken(primary, 0.3), { ol: OUTLINE }); // 손잡이
}

/** 가방 키링 */
export function drawKeyring(pen: Painter, primary: string, secondary: string, ringX: number, ringY: number): void {
  pen.fill(pen.ellipse(ringX, ringY, 1.6, 1.6), '#f4c542', { ol: OUTLINE });
  pen.fill(pen.line(ringX, ringY + 1, ringX, ringY + 3, 0.8), '#f4c542', FLAT);
  pen.fill(pen.ellipse(ringX, ringY + 6, 3, 3), primary, { ol: OUTLINE, wide: true });
  pen.fill(pen.ellipse(ringX - 1, ringY + 5, 1, 1), secondary, FLAT);
}

/** 동행(펫): (baseX, baseY) 발 기준점 */
export function drawPet(pen: Painter, type: string, primary: string, secondary: string, baseX: number, baseY: number): void {
  const bx = baseX;
  const by = baseY;
  if (type === 'dog') {
    pen.fill(pen.poly([[bx - 10, by - 14], [bx + 8, by - 14], [bx + 8, by - 2], [bx - 10, by - 2]]), primary, { wide: true });
    pen.fill(pen.ellipse(bx + 10, by - 16, 7, 6), primary, { wide: true });
    pen.fill(pen.ellipse(bx + 6, by - 21, 2.5, 4), primary, {});
    [bx - 7, bx - 1, bx + 4].forEach(legX => pen.fill(pen.rect(legX, by - 4, 3, 5), primary, {}));
    pen.fill(pen.line(bx - 10, by - 12, bx - 16, by - 20, 3), primary, {});
    pen.dot(bx + 12, by - 17, secondary);
    pen.dot(bx + 16, by - 15, secondary);
  } else if (type === 'rodent') {
    pen.fill(pen.ellipse(bx, by - 6, 8, 6), primary, { wide: true });
    pen.fill(pen.ellipse(bx + 7, by - 10, 5, 4.5), primary, {});
    pen.fill(pen.ellipse(bx + 5, by - 15, 2, 2.5), primary, {});
    pen.fill(pen.ellipse(bx + 9, by - 15, 2, 2.5), primary, {});
    pen.fill(pen.ellipse(bx - 9, by - 12, 4, 6), primary, {});
    pen.dot(bx + 9, by - 11, secondary);
    pen.fill(pen.ellipse(bx + 1, by - 5, 3, 2.5), secondary, FLAT);
  } else if (type === 'cow') {
    pen.fill(pen.poly([[bx - 12, by - 16], [bx + 10, by - 16], [bx + 10, by - 4], [bx - 12, by - 4]]), primary, { wide: true });
    pen.fill(pen.ellipse(bx + 13, by - 15, 7, 6), primary, { wide: true });
    pen.fill(pen.ellipse(bx + 15, by - 12, 4, 2.5), '#f0a0a0', { flat: true });
    [bx - 10, bx - 4, bx + 2, bx + 7].forEach(legX => pen.fill(pen.rect(legX, by - 5, 3, 6), primary, {}));
    pen.fill(pen.ellipse(bx - 5, by - 11, 4, 3), secondary, FLAT);
    pen.fill(pen.ellipse(bx + 5, by - 8, 3, 2), secondary, FLAT);
    pen.fill(pen.line(bx + 10, by - 20, bx + 14, by - 24, 2), '#f3e9dc', {});
    pen.fill(pen.line(bx + 16, by - 20, bx + 20, by - 24, 2), '#f3e9dc', {});
  } else if (type === 'sheep') {
    for (let i = 0; i < 6; i++) pen.fill(pen.ellipse(bx - 10 + i * 4, by - 12 + (i % 2) * 2, 5, 5), primary, { hi: 0.15 });
    pen.fill(pen.ellipse(bx, by - 10, 10, 6), primary, { outline: false, hi: 0.1, sh: 0.1 });
    pen.fill(pen.ellipse(bx + 10, by - 11, 5, 4), secondary, {});
    [bx - 6, bx + 2].forEach(legX => pen.fill(pen.rect(legX, by - 5, 3, 5), secondary, {}));
  } else if (type === 'bird') {
    pen.fill(pen.ellipse(bx, by - 10, 8, 6), primary, { wide: true });
    pen.fill(pen.ellipse(bx + 6, by - 19, 4, 4), primary, {});
    pen.fill(pen.poly([[bx + 9, by - 19], [bx + 15, by - 18], [bx + 9, by - 16]]), secondary, {});
    pen.fill(pen.rect(bx - 2, by - 5, 2, 5), secondary, {});
    pen.fill(pen.rect(bx + 3, by - 5, 2, 5), secondary, {});
    pen.fill(pen.ellipse(bx - 2, by - 10, 4, 3), darken(primary, 0.15), FLAT);
  } else if (type === 'dino') {
    pen.fill(pen.poly([[bx - 12, by - 12], [bx + 6, by - 18], [bx + 6, by - 4], [bx - 12, by - 4]]), primary, { wide: true });
    pen.fill(pen.ellipse(bx + 10, by - 20, 7, 5), primary, { wide: true });
    pen.fill(pen.line(bx - 12, by - 10, bx - 20, by - 4, 3), primary, {});
    [bx - 6, bx + 1].forEach(legX => pen.fill(pen.rect(legX, by - 5, 4, 6), primary, {}));
    [bx - 8, bx - 2, bx + 4].forEach(spikeX => pen.fill(pen.poly([[spikeX - 2, by - 13], [spikeX, by - 18], [spikeX + 2, by - 13]]), secondary, {}));
    pen.dot(bx + 12, by - 21, '#1b2430');
  } else if (type === 'seal') {
    pen.fill(pen.ellipse(bx, by - 6, 14, 6), primary, { wide: true });
    pen.fill(pen.ellipse(bx + 11, by - 10, 6, 5), primary, { wide: true });
    pen.fill(pen.ellipse(bx - 14, by - 9, 4, 3), primary, {});
    pen.dot(bx + 13, by - 11, secondary);
    pen.dot(bx + 15, by - 9, secondary);
    pen.fill(pen.ellipse(bx - 2, by - 4, 6, 2), secondary, FLAT);
  } else if (type === 'fishpet') {
    pen.fill(pen.ellipse(bx, by - 12, 11, 6), primary, { wide: true });
    pen.fill(pen.poly([[bx - 10, by - 12], [bx - 17, by - 18], [bx - 17, by - 6]]), primary, {});
    pen.dot(bx + 7, by - 13, '#1b2430');
    pen.fill(pen.ellipse(bx - 1, by - 12, 4, 2), secondary, FLAT);
  }
}

/** 장식(장면 바닥에 세우는 것): (baseX, baseY) 바닥 중심 */
export function drawProp(pen: Painter, type: string, primary: string, secondary: string, baseX: number, baseY: number): void {
  const bx = baseX;
  const by = baseY;
  if (type === 'parasol') {
    pen.fill(pen.rect(bx - 1, by - 40, 3, 40), '#8d5524', {});
    pen.fill(pen.poly([[bx - 24, by - 36], [bx - 12, by - 50], [bx, by - 54], [bx + 12, by - 50], [bx + 24, by - 36]]), primary, { wide: true });
    for (let rib = -2; rib <= 2; rib++) pen.fill(pen.line(bx + rib * 9, by - 37, bx + rib * 2, by - 52, 1.5), secondary, FLAT);
  } else if (type === 'hanok') {
    pen.fill(pen.rect(bx - 20, by - 22, 40, 22), '#f3e9dc', { wide: true });
    for (let pillar = -1; pillar <= 1; pillar++) pen.fill(pen.rect(bx + pillar * 14 - 2, by - 22, 4, 22), '#5c3b2e', {});
    pen.fill(pen.rect(bx - 5, by - 14, 10, 14), '#8d5524', {});
    pen.fill(pen.poly([[bx - 30, by - 22], [bx - 20, by - 36], [bx + 20, by - 36], [bx + 30, by - 22]]), primary, { wide: true, sh: 0.4 });
    pen.fill(pen.rect(bx - 30, by - 24, 60, 3), secondary, { flat: true });
  } else if (type === 'statue') {
    pen.fill(pen.poly([[bx - 9, by - 22], [bx + 9, by - 22], [bx + 11, by], [bx - 11, by]]), primary, { wide: true });
    pen.fill(pen.ellipse(bx, by - 28, 9, 10), primary, { wide: true });
    pen.fill(pen.ellipse(bx, by - 36, 9, 4), secondary, {});
    ([[bx - 4, by - 30], [bx + 4, by - 30]] as const).forEach(([eyeX, eyeY]) => pen.fill(pen.ellipse(eyeX, eyeY, 2.5, 2), secondary, { flat: true }));
    pen.fill(pen.ellipse(bx, by - 24, 3, 2), secondary, { flat: true });
    pen.fill(pen.ellipse(bx - 6, by - 12, 4, 6), secondary, { outline: false, flat: true });
    pen.fill(pen.ellipse(bx + 6, by - 10, 4, 6), secondary, { outline: false, flat: true });
  } else if (type === 'tree') {
    pen.fill(pen.rect(bx - 3, by - 22, 6, 22), secondary, { wide: true });
    pen.fill(pen.ellipse(bx, by - 34, 18, 14), primary, { wide: true });
    pen.fill(pen.ellipse(bx - 8, by - 28, 10, 8), primary, { outline: false, hi: 0.15, sh: 0 });
    pen.fill(pen.ellipse(bx + 6, by - 40, 9, 7), lighten(primary, 0.12), { outline: false, hi: 0.2, sh: 0 });
  } else if (type === 'bamboo') {
    [-7, 0, 7].forEach((offset, i) => {
      const stalk = 44 - i * 6;
      pen.fill(pen.rect(bx + offset - 2, by - stalk, 4, stalk), primary, {});
      for (let knotY = by - stalk + 8; knotY < by; knotY += 10) pen.fill(pen.rect(bx + offset - 2, knotY, 4, 2), secondary, { flat: true });
      pen.fill(pen.poly([[bx + offset, by - stalk + 10], [bx + offset + 10, by - stalk + 4], [bx + offset + 2, by - stalk + 12]]), secondary, {});
    });
  } else if (type === 'railbike') {
    pen.fill(pen.rect(bx - 18, by - 14, 36, 8), primary, { wide: true });
    pen.fill(pen.rect(bx - 14, by - 22, 10, 8), primary, {});
    pen.fill(pen.rect(bx + 4, by - 22, 10, 8), primary, {});
    [bx - 11, bx + 8].forEach(wheelX => pen.fill(pen.ellipse(wheelX, by - 4, 5, 5), secondary, { wide: true }));
    pen.fill(pen.rect(bx - 24, by - 2, 48, 2), '#9aa7b5', { flat: true });
  } else if (type === 'candle') {
    pen.fill(pen.rect(bx - 5, by - 18, 10, 18), primary, { wide: true });
    pen.fill(pen.ellipse(bx, by - 21, 2, 4), secondary, { hi: 0.4 });
    pen.fill(pen.ellipse(bx, by - 23, 1, 2), '#ffffff', FLAT);
  } else if (type === 'vase') {
    pen.fill(pen.poly([[bx - 6, by - 30], [bx + 6, by - 30], [bx + 12, by - 18], [bx + 9, by], [bx - 9, by], [bx - 12, by - 18]]), primary, { wide: true });
    pen.fill(pen.rect(bx - 12, by - 16, 24, 3), secondary, { flat: true });
    pen.fill(pen.ellipse(bx, by - 8, 4, 3), secondary, { flat: true });
  }
}
