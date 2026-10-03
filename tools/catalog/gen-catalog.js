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
return { PROVS, FEATURES, LEGEND, rarity, XP, BONUS, RAR_LABEL, itemOf, lookOf: (typeof lookOf !== 'undefined' ? lookOf : null), SAMPLE, findCode, TIER,
  LEVEL_TITLES, SETS, SET_BG, BADGES, LONG };`)(RAW, localStorage);
// monthQuests 는 '기본 캐릭터' 구획 뒤에 있다 — 함수 본문만 잘라 빈 컨텍스트로 실행해 정의(id·이름·목표·XP)를 얻는다.
const mqSrc = html.slice(html.indexOf('function monthQuests(c){'), html.indexOf('function ctx(){'));
const MONTH_QUESTS = new Function('PROVS', 'rarity', 'SET_OF', 'ym', 'CUR_YM', mqSrc + '\nreturn monthQuests({ list: [] });')(
  api.PROVS, api.rarity, new Map(), () => '', 'none');

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
  // 8단계(게임 요소 1순위): 이번 주 미스터리 지역 보너스·시·도 정복 보상 — 프로토타입에 없는 값이라 여기서 정한다
  mysteryBonus: 50, provinceConquestBonus: 300,
};
const sample = api.SAMPLE.map(([p, n, mo, day, memo]) => {
  const c = api.findCode(p, n); if (!c) throw new Error('sample missing ' + p + n);
  return { regionCode: 'KR-' + c, monthOffset: mo, day, memo };
});

// ---- 2단계(진행) 정의 데이터: 레벨 곡선·칭호, 도감 세트 9, 뱃지 12, 퀘스트(월간 4 + 상시 3) ----
// 판정 함수(test/cur)는 JS 라 그대로 옮길 수 없으므로 id 별 조건을 아래 표로 선언하고, id 집합이 프로토타입과 같은지 검사한다.
const provOf = name => { const c = provCode[name]; if (!c) throw new Error('unknown prov ' + name); return c; };
const levels = {
  // 프로토타입: level = floor((1 + sqrt(1 + xp/5)) / 2)  ⇔  레벨 L 의 하한 XP = 4·divisor·L·(L-1)
  divisor: 5,
  titles: api.LEVEL_TITLES.map(([level, name]) => ({ level, name })),
};
const sets = api.SETS.map(s => ({ id: s.id, name: s.name, desc: s.desc, title: s.title,
  regionCodes: s.codes.map(c => 'KR-' + c),
  background: { emoji: api.SET_BG[s.id][0], name: api.SET_BG[s.id][1] + ' 배경', theme: api.SET_BG[s.id][2] } }));
sets.forEach(s => { if (s.regionCodes.length !== api.SETS.find(x => x.id === s.id).m.length) throw new Error('set code missing ' + s.id); });
const BADGE_RULES = {
  first:   { type: 'REGION_COUNT', min: 1 },
  ten:     { type: 'REGION_COUNT', min: 10 },
  fifty:   { type: 'REGION_COUNT', min: 50 },
  hundred: { type: 'REGION_COUNT', min: 100 },
  seoul:   { type: 'PROVINCES_COMPLETE', provinces: [provOf('서울')] },
  capital: { type: 'PROVINCES_COMPLETE', provinces: [provOf('서울'), provOf('경기'), provOf('인천')] },
  samnam:  { type: 'PROVINCE_GROUPS_TOUCHED', groups: [[provOf('충북'), provOf('충남')], [provOf('전북'), provOf('전남')], [provOf('경북'), provOf('경남')]] },
  legend:  { type: 'LEGEND_COUNT', min: 1 },
  allprov: { type: 'ALL_PROVINCES_TOUCHED' },
  set1:    { type: 'SETS_COMPLETED', min: 1 },
  streak3: { type: 'STREAK_MONTHS', min: 3 },
  half:    { type: 'CONQUEST_RATIO', ratio: 0.5 },
};
const badges = api.BADGES.map(b => {
  if (!BADGE_RULES[b.id]) throw new Error('badge rule missing ' + b.id);
  return { id: b.id, ico: b.ico, name: b.name, desc: b.desc, condition: BADGE_RULES[b.id] };
});
if (badges.length !== Object.keys(BADGE_RULES).length) throw new Error('badge rule extra');
// 8단계: 이번 주 미스터리 지역 누적 뱃지(프로토타입에 없는 뱃지 — 끝에 덧붙인다)
badges.push(
  { id: 'mystery1', ico: '?', name: '미스터리 탐험가', desc: '이번 주 미스터리 지역 1번 찾기', condition: { type: 'MYSTERY_FOUND', min: 1 } },
  { id: 'mystery5', ico: '??', name: '미스터리 추적자', desc: '이번 주 미스터리 지역 5번 찾기', condition: { type: 'MYSTERY_FOUND', min: 5 } },
  { id: 'mystery10', ico: '謎', name: '미스터리 마스터', desc: '이번 주 미스터리 지역 10번 찾기', condition: { type: 'MYSTERY_FOUND', min: 10 } },
);
const QUEST_RULES = {
  m3:    { metric: 'NEW_REGIONS' },
  mgun:  { metric: 'NON_COMMON_REGIONS' },
  mprov: { metric: 'FIRST_IN_PROVINCE' },
  mset:  { metric: 'SET_REGIONS' },
  leg5:  { metric: 'LEGEND_REGIONS' },
  gun30: { metric: 'NON_COMMON_REGIONS' },
  p3:    { metric: 'PROVINCES_WITH_MIN_REGIONS', param: 3 },
};
const quest = (q, scope, target, title) => {
  const rule = QUEST_RULES[q.id]; if (!rule) throw new Error('quest rule missing ' + q.id);
  return { id: q.id, scope, ico: q.ico, name: q.name, desc: q.desc, metric: rule.metric, param: rule.param || 0,
    target, xp: q.xp, title: title || null };
};
const quests = [...MONTH_QUESTS.map(q => quest(q, 'MONTHLY', q.max, null)), ...api.LONG.map(q => quest(q, 'ALWAYS', q.max, q.title))];
if (quests.length !== Object.keys(QUEST_RULES).length) throw new Error('quest rule extra');

const CAT = ROOT + '/catalog/src/main/resources/catalog', DEV = ROOT + '/app-api/src/main/resources/dev';
fs.mkdirSync(CAT, { recursive: true }); fs.mkdirSync(DEV, { recursive: true });
const w = (name, obj, pretty) => fs.writeFileSync(name, pretty ? JSON.stringify(obj, null, 1) + '\n' : JSON.stringify(obj));
w(CAT + '/provinces.json', provinces, true);
w(CAT + '/regions.json', regions, true);
// 아이템 정의는 3단계부터 DB(item_definition). items.json 은 이관 원본으로만 남는다 → gen-item-sql.js 가 V3_1 INSERT 를 만든다.
w(__dirname + '/items.json', items, true);
w(CAT + '/regions.geojson', geojson, false);
w(CAT + '/reward-rules.json', rewardRules, true);
w(DEV + '/sample-visits.json', sample, true);
w(CAT + '/levels.json', levels, true);
w(CAT + '/sets.json', sets, true);
w(CAT + '/badges.json', badges, true);
w(CAT + '/quests.json', quests, true);
console.log('provinces', provinces.length, 'regions', regions.length, 'items', items.length, 'legend', regions.filter(r => r.rarity==='LEGEND').length, 'rare', regions.filter(r=>r.rarity==='RARE').length, 'sample', sample.length, 'sets', sets.length, 'badges', badges.length, 'quests', quests.length);
