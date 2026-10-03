/** 픽셀 페인터 색 계산(프로토타입 hex2rgb·mix·lighten·darken 그대로). */
export type Rgb = readonly [number, number, number];

const WHITE: Rgb = [255, 255, 255];
const INK: Rgb = [20, 16, 30];

export function hexToRgb(hex: string): Rgb {
  return [parseInt(hex.slice(1, 3), 16), parseInt(hex.slice(3, 5), 16), parseInt(hex.slice(5, 7), 16)];
}

export function rgbToHex(red: number, green: number, blue: number): string {
  return '#' + [red, green, blue].map(value => Math.max(0, Math.min(255, Math.round(value))).toString(16).padStart(2, '0')).join('');
}

/** hex 색을 target 쪽으로 ratio 만큼 섞는다. */
export function mix(hex: string, ratio: number, target: Rgb): string {
  const [red, green, blue] = hexToRgb(hex);
  const [toRed, toGreen, toBlue] = target;
  return rgbToHex(red + (toRed - red) * ratio, green + (toGreen - green) * ratio, blue + (toBlue - blue) * ratio);
}

export const lighten = (hex: string, ratio: number): string => mix(hex, ratio, WHITE);
export const darken = (hex: string, ratio: number): string => mix(hex, ratio, INK);
export const WHITE_RGB = WHITE;
