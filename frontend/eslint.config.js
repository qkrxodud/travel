import js from '@eslint/js';
import globals from 'globals';
import reactHooks from 'eslint-plugin-react-hooks';
import tseslint from 'typescript-eslint';
import { dirname, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const SRC = resolve(dirname(fileURLToPath(import.meta.url)), 'src');
/** 하위 계층 — features/ 를 몰라야 한다 */
const LOWER_LAYERS = ['shared', 'store', 'api'];

/** src 기준 경로 조각(['features', 'map', 'queries']). src 밖이면 null. */
function srcSegments(absolutePath) {
  const fromSrc = relative(SRC, absolutePath);
  return fromSrc.startsWith('..') || fromSrc === '' ? null : fromSrc.split(sep);
}

/** import 문자열이 가리키는 src 안 위치. 상대 경로(./ ../)와 루트 경로(/src/...)만 src 안을 가리킬 수 있다(별칭 없음). */
function targetSegments(importer, source) {
  if (source.startsWith('.')) return srcSegments(resolve(dirname(importer), source));
  if (source.startsWith('/src/')) return srcSegments(resolve(SRC, source.slice('/src/'.length)));
  return null;
}

const layerBoundaries = {
  meta: {
    type: 'problem',
    messages: {
      crossFeature: '다른 기능 폴더(features/{{target}})의 components/·model/·queries 는 import 하지 않는다 — 함께 쓰는 것은 shared/ 로 올린다',
      lowerToFeature: '{{layer}}/ 는 features/ 를 import 하지 않는다 — 여러 기능이 쓰는 것은 shared/ 에 둔다',
    },
    schema: [],
  },
  create(context) {
    const importer = context.filename;
    const from = srcSegments(importer);
    if (!from) return {};
    const check = (node, source) => {
      if (typeof source !== 'string') return;
      const target = targetSegments(importer, source);
      if (!target || target[0] !== 'features' || target.length < 2) return;
      if (from[0] === 'features' && from[1] !== target[1]) context.report({ node, messageId: 'crossFeature', data: { target: target[1] } });
      if (LOWER_LAYERS.includes(from[0])) context.report({ node, messageId: 'lowerToFeature', data: { layer: from[0] } });
    };
    const fromSource = node => node.source && check(node.source, node.source.value);
    return {
      ImportDeclaration: fromSource,
      ExportNamedDeclaration: fromSource,
      ExportAllDeclaration: fromSource,
      ImportExpression: node => node.source.type === 'Literal' && check(node.source, node.source.value),
    };
  },
};

export default tseslint.config(
  { ignores: ['dist', 'node_modules'] },
  {
    extends: [js.configs.recommended, ...tseslint.configs.strict],
    files: ['**/*.{ts,tsx}'],
    languageOptions: { ecmaVersion: 2022, globals: globals.browser },
    plugins: { 'react-hooks': reactHooks },
    rules: {
      ...reactHooks.configs.recommended.rules,
      '@typescript-eslint/no-explicit-any': 'error',
      '@typescript-eslint/ban-ts-comment': 'error',
      // 명명 규칙(백엔드와 같은 결): 한 글자 이름 금지 — 인덱스 i/j 만 예외
      'id-length': ['error', { min: 2, exceptions: ['i', 'j'], properties: 'never' }],
      // 서버 호출은 src/api/ 에만 — 전역 fetch 와 window.fetch·globalThis.fetch·self.fetch 모두
      'no-restricted-globals': ['error', { name: 'fetch', message: '서버 호출은 src/api/ 에서만 한다' }],
      'no-restricted-properties': ['error',
        ...['window', 'globalThis', 'self'].map(object => ({ object, property: 'fetch', message: '서버 호출은 src/api/ 에서만 한다' })),
      ],
    },
  },
  {
    // 계층 경계(import 경로를 실제 위치로 풀어 판단하므로 폴더 깊이·../ 개수와 무관하다):
    //  - 기능 폴더끼리는 서로의 components/·model/·queries 를 import 하지 않는다 — 함께 쓰는 것은 shared/ 로 올린다.
    //  - shared/·store/·api/ 는 features/ 를 import 하지 않는다(아래 계층이 위 계층을 모른다).
    files: ['src/**/*.{ts,tsx}'],
    plugins: { territory: { rules: { 'layer-boundaries': layerBoundaries } } },
    rules: { 'territory/layer-boundaries': 'error' },
  },
  {
    files: ['src/api/**/*.ts', 'src/**/*.test.{ts,tsx}'],
    rules: { 'no-restricted-globals': 'off', 'no-restricted-properties': 'off' },
  },
);
