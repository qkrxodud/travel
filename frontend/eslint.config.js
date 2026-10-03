import js from '@eslint/js';
import globals from 'globals';
import reactHooks from 'eslint-plugin-react-hooks';
import tseslint from 'typescript-eslint';

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
    // 기능 폴더끼리는 서로의 components/·model/·queries 를 import 하지 않는다 — 함께 쓰는 것은 shared/ 로 올린다.
    // 기능 루트 파일(features/x/queries.ts)은 ../y/..., 그 아래 파일(components/·model/)은 ../../y/... 로 들어온다(둘 다 막는다).
    files: ['src/features/**/*.{ts,tsx}'],
    rules: {
      'no-restricted-imports': ['error', {
        patterns: [{
          regex: '^(\\.\\./|\\.\\./\\.\\./)(?!\\.\\.|shared/)[^/]+/(components|model|queries)(/|$)|(^|/)features/[^/]+/(components|model|queries)(/|$)',
          message: '다른 기능 폴더의 components/·model/·queries 는 import 하지 않는다 — 함께 쓰는 것은 shared/ 로 올린다',
        }],
      }],
    },
  },
  {
    files: ['src/api/**/*.ts', 'src/**/*.test.{ts,tsx}'],
    rules: { 'no-restricted-globals': 'off', 'no-restricted-properties': 'off' },
  },
);
