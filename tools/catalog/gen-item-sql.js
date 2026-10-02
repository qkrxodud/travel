// 아이템 정의 이관(3단계): tools/catalog/items.json(지역 특산물 250) + catalog sets.json(세트 배경 9) → item_definition INSERT.
// 실행: node tools/catalog/gen-item-sql.js > /tmp/items.sql  (출력을 V3_1__wardrobe.sql 끝에 붙였다 — Flyway 마이그레이션은 적용 후 고치지 않는다)
const fs = require('fs');
const ROOT = require('path').resolve(__dirname, '../..');
const items = JSON.parse(fs.readFileSync(__dirname + '/items.json', 'utf8'));
const sets = JSON.parse(fs.readFileSync(ROOT + '/catalog/src/main/resources/catalog/sets.json', 'utf8'));
// 이관 데이터의 정의 생성 시각(UTC 벽시계 — hibernate.jdbc.time_zone=UTC). 이슈 아이템 소급 판정(Q-R2-1)에만 쓰인다.
const CREATED_AT = '2026-10-01 00:00:00';
const quote = value => value === null || value === undefined ? 'NULL' : `'${String(value).replace(/'/g, "''")}'`;
const row = definition => `(${[definition.itemId, definition.name, definition.emoji, definition.slot, definition.tier, definition.theme, definition.look, definition.primary, definition.secondary, definition.rule, definition.ref, CREATED_AT].map(quote).join(', ')})`;
const rows = [
  ...items.map(item => ({ itemId: item.itemId, name: item.name, emoji: item.emoji, slot: item.slot, tier: item.tier, theme: item.theme,
    look: item.look && item.look.type, primary: item.look && item.look.primary, secondary: item.look && item.look.secondary,
    rule: 'REGION_VISIT', ref: item.regionCode })),
  ...sets.filter(set => set.background).map(set => ({ itemId: 'set:' + set.id, name: set.background.name, emoji: set.background.emoji,
    slot: 'BG', tier: 'LEGEND', theme: set.background.theme, look: null, primary: null, secondary: null, rule: 'THEME_COMPLETE', ref: set.id })),
];
console.log('INSERT INTO item_definition (item_id, name, emoji, slot, tier, theme, look, color_primary, color_secondary, grant_rule, grant_ref, created_at) VALUES');
console.log(rows.map(row).join(',\n') + ';');
console.error('rows', rows.length);
