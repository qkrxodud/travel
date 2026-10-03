/**
 * 에러 코드 목록이 서버 실제 코드와 같은지(06 QA P3-6) — 백엔드 소스에서 코드를 모아 대조한다.
 * 도메인 오류 enum 상수(`NAME(ErrorKind.X, ...)`) + `new TerritoryException("CODE", ...)` / 상수 문자열 + GlobalExceptionHandler `respond(…, "CODE", …)`.
 * 백엔드 소스가 없는 환경(프론트만 따로 받은 경우)에서는 건너뛴다.
 */
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { SERVER_ERROR_CODES } from './common';

const ROOT = resolve(__dirname, '../../../..');
const MODULES = ['common', 'catalog', 'exploration', 'progression', 'wardrobe', 'sharing', 'social', 'account', 'app-api'];

function javaFiles(dir: string): string[] {
  if (!existsSync(dir)) return [];
  return readdirSync(dir).flatMap(name => {
    const path = join(dir, name);
    return statSync(path).isDirectory() ? javaFiles(path) : path.endsWith('.java') ? [path] : [];
  });
}

function serverCodes(): Set<string> {
  const codes = new Set<string>();
  const sources = MODULES.flatMap(module => javaFiles(join(ROOT, module, 'src/main/java')));
  for (const file of sources) {
    const text = readFileSync(file, 'utf8');
    for (const match of text.matchAll(/^\s+([A-Z][A-Z0-9_]+)\(ErrorKind\.[A-Z_]+/gm)) codes.add(match[1]);
    for (const match of text.matchAll(/new TerritoryException\(\s*"([A-Z][A-Z0-9_]+)"/g)) codes.add(match[1]);
    for (const match of text.matchAll(/respond\(\s*HttpStatus\.[A-Z_]+,\s*"([A-Z][A-Z0-9_]+)"/g)) codes.add(match[1]);
    // 상수로 넘기는 코드(ExplorerAuthentication·SecurityConfig): static final String X = "CODE" 중 TerritoryException·에러 응답에 쓰이는 것
    if (/TerritoryException|ErrorResponse|writeError/.test(text)) {
      for (const match of text.matchAll(/static final String [A-Z_]+ = "([A-Z][A-Z0-9_]+)";/g)) codes.add(match[1]);
    }
  }
  return codes;
}

const hasBackend = existsSync(join(ROOT, 'app-api/src/main/java'));

describe.skipIf(!hasBackend)('서버 에러 코드 목록', () => {
  it('SERVER_ERROR_CODES 가 백엔드가 내는 코드와 같다', () => {
    const server = [...serverCodes()].sort();
    expect(server.length).toBeGreaterThan(40);
    expect([...SERVER_ERROR_CODES].sort()).toEqual(server);
  });
});
