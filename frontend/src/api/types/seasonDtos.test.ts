/**
 * 계절 회차 근거·관리자 회차 응답(13s단계)의 화면 타입이 백엔드 응답 레코드와 필드 하나하나 같은지 — 백엔드 소스를 읽어 대조한다.
 * 필드 이름·순서, 그리고 문자열로 오는 종류 값(근거 종류·확정한 쪽·수집 계획·시도 결과·출처)이 서버 enum 과 같아야 한다.
 * 백엔드 소스가 없는 환경(프론트만 따로 받은 경우)에서는 건너뛴다.
 */
import { existsSync, readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const ROOT = resolve(__dirname, '../../../..');
const PROGRESSION_DTOS = join(ROOT, 'progression/src/main/java/com/kobi/territory/progression/api/web/ProgressionDtos.java');
const ADMIN_DTOS = join(ROOT, 'catalog/src/main/java/com/kobi/territory/catalog/api/web/AdminSeasonDtos.java');
const LINEUP_DOMAIN = join(ROOT, 'catalog/src/main/java/com/kobi/territory/catalog/domain/lineup');
const hasBackend = existsSync(PROGRESSION_DTOS) && existsSync(ADMIN_DTOS);

const withoutComments = (text: string) => text.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/[^\n]*/g, '');

/** Java record 의 구성 요소 이름(선언 순서) */
function recordFields(javaFile: string, record: string): string[] {
  const text = withoutComments(readFileSync(javaFile, 'utf8'));
  const match = new RegExp(`record\\s+${record}\\s*\\(([^)]*)\\)`).exec(text);
  if (!match?.[1]) throw new Error(`${record} 레코드를 찾지 못했다`);
  return match[1].split(',').map(part => part.trim().split(/\s+/).at(-1) ?? '').filter(Boolean);
}

/** TS interface 의 필드 이름(선언 순서) */
function interfaceFields(tsFile: string, name: string): string[] {
  const text = withoutComments(readFileSync(join(__dirname, tsFile), 'utf8'));
  const match = new RegExp(`export interface ${name} \\{([\\s\\S]*?)\\n\\}`).exec(text);
  if (!match?.[1]) throw new Error(`${name} 인터페이스를 찾지 못했다`);
  return [...match[1].matchAll(/^\s+(\w+)\??:/gm)].map(field => field[1] ?? '');
}

/** Java enum 상수(선언 순서) */
function enumConstants(javaFile: string, name: string): string[] {
  const text = withoutComments(readFileSync(javaFile, 'utf8'));
  const match = new RegExp(`enum\\s+${name}\\s*\\{([^;}]*)`).exec(text);
  if (!match?.[1]) throw new Error(`${name} enum 을 찾지 못했다`);
  return match[1].split(',').map(part => /^\s*([A-Z][A-Z0-9_]*)/.exec(part)?.[1] ?? '').filter(Boolean);
}

/** TS 문자열 리터럴 유니언 값 */
function unionValues(tsFile: string, name: string): string[] {
  const text = readFileSync(join(__dirname, tsFile), 'utf8');
  const match = new RegExp(`export type ${name} = ([^;]+);`).exec(text);
  if (!match?.[1]) throw new Error(`${name} 타입을 찾지 못했다`);
  return [...match[1].matchAll(/'([^']+)'/g)].map(value => value[1] ?? '');
}

describe.skipIf(!hasBackend)('계절 회차 응답 타입', () => {
  it('플레이어가 보는 회차·지역·근거·다음 회차는 서버 응답과 필드가 같다', () => {
    const pairs: [string, string][] = [
      ['SeasonRoundResponse', 'SeasonRoundResponse'], ['SeasonRegionResponse', 'SeasonRegionResponse'],
      ['SeasonEvidenceResponse', 'SeasonEvidenceResponse'], ['NextSeasonResponse', 'NextSeasonResponse'], ['SeasonsResponse', 'SeasonsResponse'],
    ];
    for (const [record, view] of pairs) expect(interfaceFields('progression.ts', view), view).toEqual(recordFields(PROGRESSION_DTOS, record));
  });

  it('관리자가 보는 회차 목록·TourAPI 사용량·후보·마지막 시도·지역·근거는 서버 응답과 필드가 같다', () => {
    const pairs: [string, string][] = [
      ['SeasonLineupsResponse', 'SeasonLineupsResponse'], ['TourApiStatusResponse', 'TourApiStatusResponse'], ['ScheduleResponse', 'LineupScheduleResponse'],
      ['RoundLineupResponse', 'RoundLineupResponse'], ['LineupResponse', 'LineupResponse'], ['CandidateResponse', 'LineupCandidateResponse'],
      ['AttemptResponse', 'LineupAttemptResponse'], ['LineupRegionResponse', 'LineupRegionResponse'], ['EvidenceResponse', 'LineupEvidenceResponse'],
    ];
    for (const [record, view] of pairs) expect(interfaceFields('catalog.ts', view), view).toEqual(recordFields(ADMIN_DTOS, record));
  });

  it('근거 종류·확정한 쪽·자동 수집 계획·수집 결과는 서버가 보내는 값과 빠짐도 남음도 없다', () => {
    const evidenceKinds = enumConstants(join(LINEUP_DOMAIN, 'LineupEvidence.java'), 'EvidenceKind');
    expect(unionValues('catalog.ts', 'LineupEvidenceKind')).toEqual(evidenceKinds);
    expect(unionValues('progression.ts', 'EvidenceKind')).toEqual(evidenceKinds);
    expect(unionValues('catalog.ts', 'LineupConfirmedBy')).toEqual(enumConstants(join(LINEUP_DOMAIN, 'ConfirmedBy.java'), 'ConfirmedBy'));
    expect(unionValues('catalog.ts', 'LineupNextPlan')).toEqual(enumConstants(join(LINEUP_DOMAIN, 'CollectionPlan.java'), 'CollectionPlan'));
    expect(unionValues('catalog.ts', 'LineupAttemptOutcome')).toEqual(enumConstants(join(LINEUP_DOMAIN, 'CollectionAttempt.java'), 'Outcome'));
  });

  it('지역 출처 값은 서버 출처 코드와 같다', () => {
    const text = readFileSync(join(LINEUP_DOMAIN, 'LineupProvenance.java'), 'utf8');
    const codes = [...text.matchAll(/^\s+[A-Z_]+\("([a-z-]+)"\)/gm)].map(code => code[1]);
    expect(unionValues('catalog.ts', 'LineupRegionProvenance')).toEqual(codes);
    expect(unionValues('progression.ts', 'RegionProvenance')).toEqual(codes);
  });
});
