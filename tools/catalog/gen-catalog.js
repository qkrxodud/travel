// 프로토타입(doc/나의 영토.html)의 JS 상수를 그대로 실행해 카탈로그 JSON을 만든다.
// 실행: node tools/catalog/gen-catalog.js  (저장소 루트에서)
// 상호·브랜드 아이템명 일반명사화(리스크 #9)는 renames.json 에서 관리한다.
const fs = require('fs');
const ROOT = require('path').resolve(__dirname, '../..');
const html = fs.readFileSync(ROOT + '/doc/나의 영토.html', 'utf8');
const RENAMES = JSON.parse(fs.readFileSync(__dirname + '/renames.json', 'utf8'));
const rawStart = html.indexOf('const RAW = ') + 'const RAW = '.length;
const RAW = JSON.parse(html.slice(rawStart, html.indexOf('\n', rawStart)).trim().replace(/;$/, ''));
let seg = html.slice(html.indexOf('const PROVS = '), html.indexOf('/* ================= 기본 캐릭터'));
for (const [from, to] of RENAMES) seg = seg.split(`'${from}'`).join(`'${to}'`);
const localStorage = { getItem: () => null, setItem: () => {} };
const api = new Function('RAW', 'localStorage', seg + `
return { PROVS, FEATURES, LEGEND, rarity, XP, BONUS, RAR_LABEL, itemOf, lookOf: (typeof lookOf !== 'undefined' ? lookOf : null), SAMPLE, findCode, TIER };`)(RAW, localStorage);

const PROV_META = {
  '서울':['서울특별시'], '부산':['부산광역시'], '대구':['대구광역시'], '인천':['인천광역시'], '광주':['광주광역시'],
  '대전':['대전광역시'], '울산':['울산광역시'], '세종':['세종특별자치시'], '경기':['경기도'], '강원':['강원특별자치도'],
  '충북':['충청북도'], '충남':['충청남도'], '전북':['전북특별자치도'], '전남':['전라남도'], '경북':['경상북도'],
  '경남':['경상남도'], '제주':['제주특별자치도'],
};
const provCode = {};
RAW.forEach(r => { provCode[r.p] = 'KR-' + r.c.slice(0, 2); });
const RAR = { common:'COMMON', rare:'RARE', legend:'LEGEND' };
const SLOT = { hand:'HAND', badge:'BADGE', hat:'HAT', back:'BAG', pet:'PET', bg:'BG', prop:'PROP' };

const provinces = api.PROVS.map((p, i) => ({
  code: provCode[p], name: p, fullName: PROV_META[p][0], displayOrder: i + 1,
  regionCount: RAW.filter(r => r.p === p).length,
}));
const regions = api.FEATURES.map(f => ({
  code: 'KR-' + f.properties.code, name: f.properties.name, provinceCode: provCode[f.properties.prov],
  rarity: RAR[api.rarity(f)], countryCode: 'KR', version: 1, replacedBy: null, retiredAt: null,
}));
const items = api.FEATURES.map(f => {
  const it = api.itemOf(f);
  const out = { itemId: 'region:KR-' + f.properties.code, regionCode: 'KR-' + f.properties.code,
    name: it.n, emoji: it.e, slot: SLOT[it.slot], tier: RAR[it.tier], theme: it.theme || null, look: null };
  if (it.slot !== 'bg' && api.lookOf) { const l = api.lookOf(it); out.look = { type: l.type, primary: l.a, secondary: l.b }; }
  if (!out.slot) throw new Error('unknown slot ' + it.slot);
  return out;
});
const geojson = { type: 'FeatureCollection', features: api.FEATURES.map(f => ({
  type: 'Feature',
  properties: { code: 'KR-' + f.properties.code, name: f.properties.name, provinceCode: provCode[f.properties.prov],
    province: f.properties.prov, rarity: RAR[api.rarity(f)] },
  geometry: f.geometry })) };
const rewardRules = {
  xpByRarity: { COMMON: api.XP.common, RARE: api.XP.rare, LEGEND: api.XP.legend },
  provinceFirstBonus: api.BONUS.prov, setCompleteBonus: api.BONUS.set, claimBonus: 10,
};
const sample = api.SAMPLE.map(([p, n, mo, day, memo]) => {
  const c = api.findCode(p, n); if (!c) throw new Error('sample missing ' + p + n);
  return { regionCode: 'KR-' + c, monthOffset: mo, day, memo };
});
const CAT = ROOT + '/catalog/src/main/resources/catalog', DEV = ROOT + '/app-api/src/main/resources/dev';
fs.mkdirSync(CAT, { recursive: true }); fs.mkdirSync(DEV, { recursive: true });
const w = (name, obj, pretty) => fs.writeFileSync(name, pretty ? JSON.stringify(obj, null, 1) + '\n' : JSON.stringify(obj));
w(CAT + '/provinces.json', provinces, true);
w(CAT + '/regions.json', regions, true);
w(CAT + '/items.json', items, true);
w(CAT + '/regions.geojson', geojson, false);
w(CAT + '/reward-rules.json', rewardRules, true);
w(DEV + '/sample-visits.json', sample, true);
console.log('provinces', provinces.length, 'regions', regions.length, 'items', items.length, 'legend', regions.filter(r => r.rarity==='LEGEND').length, 'rare', regions.filter(r=>r.rarity==='RARE').length, 'sample', sample.length);
