// PWA 아이콘 만들기(한 번 돌려 결과 PNG 를 src/pwa/icons/ 에 둔다 — 빌드는 PNG 만 쓴다).
//   node scripts/pwa-icons.mjs
// 앱의 픽셀 톤(16×16 격자, 테마 색 그대로)으로 깃발을 꽂은 영토 한 조각. 외부 라이브러리 없이 PNG 를 직접 쓴다(zlib).
import { deflateSync } from 'node:zlib';
import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const OUT = resolve(dirname(fileURLToPath(import.meta.url)), '../src/pwa/icons');

// 테마 색(global.css 의 --accent·--accent-soft·--accent-deep·--gold·--fg)
const PALETTE = {
  '.': null,
  B: [0x0f, 0x8f, 0x7e, 255], // 바탕(--accent)
  L: [0xd6, 0xf1, 0xec, 255], // 내 영토(--accent-soft)
  D: [0x0b, 0x6b, 0x5f, 255], // 경계(--accent-deep)
  G: [0xd9, 0x9a, 0x1b, 255], // 깃발(--gold)
  Y: [0xfb, 0xef, 0xd4, 255], // 깃발 빛(--gold-soft)
  P: [0x1b, 0x24, 0x30, 255], // 깃대(--fg)
  W: [0xff, 0xff, 0xff, 255],
};

// 16×16 — 바탕은 칸 밖에서 채운다('.' = 바탕)
const ART = [
  '................',
  '................',
  '......PGGGG.....',
  '......PGYGGG....',
  '......PGGGG.....',
  '......P.........',
  '......P.........',
  '...LLLPLLLL.....',
  '..LLLLPLLLLLL...',
  '.LLDLLLLLLDLLL..',
  '.LLLLLLDLLLLLL..',
  '..LLLLLLLLLLL...',
  '...LLLDLLLLL....',
  '....LLLLLLL.....',
  '................',
  '................',
];

// 알림 배지(단색 실루엣 — 안드로이드 상태 표시줄은 알파만 쓴다)
const BADGE = [
  '................',
  '................',
  '.....WWWWWW.....',
  '.....WWWWWWW....',
  '.....WWWWWW.....',
  '.....W..........',
  '.....W..........',
  '.....W..........',
  '..WWWWWWWWW.....',
  '.WWWWWWWWWWWW...',
  '.WWWWWWWWWWWWW..',
  '..WWWWWWWWWWW...',
  '...WWWWWWWWW....',
  '....WWWWWWW.....',
  '................',
  '................',
];

const CRC_TABLE = new Uint32Array(256).map((_, index) => {
  let value = index;
  for (let bit = 0; bit < 8; bit++) value = value & 1 ? 0xedb88320 ^ (value >>> 1) : value >>> 1;
  return value >>> 0;
});

function crc32(bytes) {
  let crc = 0xffffffff;
  for (const byte of bytes) crc = CRC_TABLE[(crc ^ byte) & 0xff] ^ (crc >>> 8);
  return (crc ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const head = Buffer.alloc(8);
  head.writeUInt32BE(data.length, 0);
  head.write(type, 4, 'ascii');
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(Buffer.concat([head.subarray(4), data])), 0);
  return Buffer.concat([head, data, crc]);
}

function png(size, pixelAt) {
  const raw = Buffer.alloc((size * 4 + 1) * size);
  for (let row = 0; row < size; row++) {
    raw[row * (size * 4 + 1)] = 0;
    for (let col = 0; col < size; col++) {
      const rgba = pixelAt(col, row) ?? [0, 0, 0, 0];
      raw.set(rgba, row * (size * 4 + 1) + 1 + col * 4);
    }
  }
  const header = Buffer.alloc(13);
  header.writeUInt32BE(size, 0);
  header.writeUInt32BE(size, 4);
  header[8] = 8; // 8비트
  header[9] = 6; // RGBA
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', header),
    chunk('IDAT', deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

/**
 * art 를 size 정사각형에 그린다. inset = 그림 둘레 여백 비율(마스커블은 안전 영역 80% 안에 들어가게 더 크게),
 * rounded = 바탕 모서리를 둥글게(투명) — 마스커블·애플 아이콘은 꽉 찬 사각형(기기가 모양을 자른다).
 */
function icon(size, { art = ART, inset = 0.06, rounded = true, background = PALETTE.B } = {}) {
  const grid = art.length;
  const drawn = Math.floor((size * (1 - 2 * inset)) / grid) * grid;
  const cell = drawn / grid;
  const offset = Math.floor((size - drawn) / 2);
  const radius = rounded ? size * 0.18 : 0;
  const insideRounded = (col, row) => {
    if (!radius) return true;
    const cx = Math.min(Math.max(col + 0.5, radius), size - radius);
    const cy = Math.min(Math.max(row + 0.5, radius), size - radius);
    return (col + 0.5 - cx) ** 2 + (row + 0.5 - cy) ** 2 <= radius ** 2;
  };
  return png(size, (col, row) => {
    if (!insideRounded(col, row)) return null;
    const gx = Math.floor((col - offset) / cell);
    const gy = Math.floor((row - offset) / cell);
    const key = gx >= 0 && gy >= 0 && gx < grid && gy < grid ? art[gy][gx] : '.';
    return PALETTE[key] ?? background;
  });
}

mkdirSync(OUT, { recursive: true });
const files = {
  'icon-32.png': icon(32, { inset: 0 }),
  'icon-192.png': icon(192),
  'icon-512.png': icon(512),
  'maskable-512.png': icon(512, { inset: 0.12, rounded: false }),
  'apple-touch-icon.png': icon(180, { rounded: false }),
  'badge-96.png': icon(96, { art: BADGE, inset: 0, rounded: false, background: null }),
};
for (const [name, bytes] of Object.entries(files)) {
  writeFileSync(resolve(OUT, name), bytes);
  console.log(name, bytes.length, 'bytes');
}
