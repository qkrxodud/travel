---
name: implement-frontend
description: 나의 영토(territory) 웹 프론트엔드(frontend/, Vite + React + TypeScript)의 구현 규칙. index.html 프로토타입의 React 이전, 지도(D3)·가방·도감·퀘스트·랭킹·프로필·공유 지도 화면, 컴포넌트·훅·API 클라이언트·TanStack Query·zustand·픽셀 캐릭터 렌더링, 프론트 빌드를 app-api 정적 리소스로 통합하는 작업, 화면 버그 수정("화면이 끊겨", "탭이 안 떠", "버튼 동작") 등 프론트 코드를 만들거나 고치는 모든 작업에 반드시 이 스킬을 사용할 것. 백엔드 Java 코드는 implement-context, 검증은 verify-architecture.
---

# Implement Frontend — 웹 프론트 구현 규칙

사용자 확정 규칙(2026-10-03). 백엔드에서 확정한 코드 품질 원칙(얇은 계층, 일급 컬렉션 정신, 명명 규칙)을 프론트에도 같은 결로 적용한다. 각 규칙의 이유를 함께 적었다 — 애매한 경우는 이유에 비추어 판단한다.

## 스택 (고정)

Vite + React 18 + TypeScript(strict) · TanStack Query(서버 상태) · zustand(화면 상태) · D3 v7(npm, CDN 아님) · Vitest + React Testing Library(단위) · Playwright(E2E, 기존 `e2e/`) · ESLint(typescript-eslint, react-hooks). 새 런타임 의존성 추가는 보고에 이유를 적는다 — 번들과 공급망을 작게 유지하기 위해서다.

## 폴더 구조

```
frontend/
├─ index.html · vite.config.ts · tsconfig.json · package.json
└─ src/
   ├─ main.tsx                 # 진입점: QueryClientProvider, 전역 CSS
   ├─ app/                     # App 셸(헤더·탭 바·토스트), queryClient, 탭 전환
   ├─ api/                     # 서버 통신의 유일한 계층
   │  ├─ client.ts             # fetch 래퍼: 토큰·세션·CSRF·에러 {code,message}→ApiError
   │  ├─ types/                # 백엔드 DTO와 1:1 TS 타입 (컨텍스트별 파일)
   │  └─ {context}.ts          # exploration·progression·wardrobe·sharing·social·account 엔드포인트 함수
   ├─ features/{기능}/          # map · bag · collection · quests · rank · profile · sharedMap · auth
   │  ├─ components/           # 그 기능의 컴포넌트 (파일 하나에 컴포넌트 하나)
   │  ├─ queries.ts            # 그 기능의 useQuery/useMutation 훅과 쿼리 키
   │  └─ model/                # 그 기능의 순수 TS 표시 로직(포맷·정렬 보조) — React 무의존
   ├─ shared/
   │  ├─ queries/              # 여러 기능이 함께 쓰는 쿼리 훅·키 팩토리(예: territory, progress, recap)
   │  ├─ ui/                   # 공용 프레젠테이션 컴포넌트(모달·토스트·바)
   │  └─ lib/                  # D3 지도 엔진, 픽셀 페인터, 지역 코드 변환 등 순수 모듈
   ├─ store/                   # zustand 스토어 (화면 상태만)
   └─ styles/                  # 전역 CSS(프로토타입 CSS 이전본, 테마 변수)
```

- 기능 폴더 구분은 백엔드 컨텍스트가 아니라 **화면 기능** 기준이다. 이유: 탭 하나가 여러 컨텍스트 API를 쓰고(예: 가방 = wardrobe + catalog), 화면을 고칠 때 한 폴더만 열면 되게 하려는 것 — 백엔드 domain을 애그리거트별로 나눈 것과 같은 이유다.
- 기능 폴더끼리는 서로의 `components/`·`model/`·`queries`를 import하지 않는다. 함께 쓰는 것은 `shared/`로 올린다. 이 경계와 `fetch` 위치(`window.fetch`·`globalThis.fetch` 포함)는 eslint 규칙으로 강제한다(`frontend/eslint.config.js`).
- 에러 코드 목록(`api/types/common.ts`)은 백엔드 실제 코드와 일치해야 하며, 백엔드 소스를 읽어 대조하는 단위 테스트가 이를 지킨다.

## 계층 규칙

1. **서버 호출은 `api/`에만.** 컴포넌트·훅에서 `fetch`를 직접 부르지 않는다. 인증 헤더(`X-Explorer-Token`), 세션 쿠키, CSRF(`XSRF-TOKEN` 쿠키 → `X-XSRF-TOKEN` 헤더), 지역 코드 변환(`KR-11010` ↔ `11010`), 에러 변환은 `client.ts` 한 곳에서. 이유: 인증·CSRF 규칙이 바뀌어도 한 파일만 고치면 된다(프로토타입의 `api` 객체가 이미 이 역할이었다).
2. **서버 상태는 TanStack Query만 갖는다.** 서버 응답을 zustand나 컴포넌트 state에 복사하지 않는다. 쿼리 키는 키 팩토리에서만 만든다 — 한 기능만 쓰면 `features/*/queries.ts`, 여러 기능이 쓰면 `shared/queries/`.
3. **zustand는 화면 상태만**: 현재 탭, 선택 지역, 하이라이트, 칠하기/기록 모드, 지금 보는 지도(mapId), 열린 모달. 이유: 서버 상태를 두 군데 두면 어긋난다.
4. **비동기 반영(outbox)은 쿼리 무효화 + 짧은 재조회 창**으로 처리한다 — 체크인·취소 같은 mutation의 `onSuccess`에서 관련 쿼리를 무효화하고, 반영 대기가 필요한 쿼리는 일정 시간 `refetchInterval`을 켰다가 끈다. `setTimeout` 체인으로 직접 다시 그리지 않는다. 이유: 프로토타입에서 수동 재조회가 전체 재렌더를 연달아 일으켜 캐릭터 이동이 끊겼다(2026-10-03 수정 건).
5. **서버가 계산하는 값은 다시 계산하지 않는다**(XP·레벨·정복률·랭킹·보상·꾸미기 점수). 화면은 표시 포맷만.
6. **컴포넌트는 그리기만.** 판단·집계·정렬 같은 표시 로직은 `features/*/model/` 순수 함수로 빼고 단위 테스트한다 — 백엔드의 "서비스는 얇게, 로직은 도메인"과 같은 원칙.

## D3 지도·캔버스 통합

- 지도 SVG는 `shared/lib/map/` 엔진(순수 TS: 투영·경로·줌·전환)이 그리고, React는 `MapView` 컴포넌트에서 `useRef`로 컨테이너를 넘겨 **마운트 시 1회 생성 → 데이터 변경 시 엔진의 update 메서드 호출** 패턴만 쓴다. 엔진이 소유한 SVG 자식을 React가 렌더하지 않는다. 이유: React 재렌더와 D3 DOM 조작이 같은 노드를 다투면 깜빡임·누수가 생긴다.
- 애니메이션(캐릭터 이동·색칠 전환·줌)은 **목적지가 바뀔 때만** 시작한다. 같은 목적지로 이동 중이면 재렌더가 와도 다시 걸지 않는다(기존 버그의 재발 방지).
- 픽셀 캐릭터·아이템 그림은 `shared/lib/pixel/`의 순수 모듈로 옮기고, 결과를 착용 조합 키로 메모이즈한다.
- GeoJSON·카탈로그는 서버 `/catalog/*`에서 받는다(프로토타입에 내장된 데이터를 번들에 다시 넣지 않는다 — 이미 1단계에서 서버로 옮겼다).

## 타입·명명

- TypeScript `strict`, `any` 금지(불가피하면 `unknown` + 좁히기). API 타입은 백엔드 DTO 필드명·nullable 여부와 1:1로 맞추고, 에러 코드는 문자열 리터럴 유니언으로 둔다.
- 백엔드 명명 규칙을 그대로 적용한다: 한 글자 변수·파라미터·콜백 인자 금지(인덱스 i/j만 예외), 의미 있는 이름, JS 내장 타입과 같은 이름의 컴포넌트·타입 금지(`Map`, `Set` 등 — 지도 컴포넌트는 `MapView`).
- 컴포넌트 `PascalCase.tsx`, 훅 `useXxx`, 순수 모듈 `camelCase.ts`. 파일 하나에 export 컴포넌트 하나.

## 화면 동등성(이전 작업)

- 디자인·문구·동작은 현재 `app-api/src/main/resources/static/index.html`과 **같게** 옮긴다. 전역 CSS는 그대로 이전하고 클래스명·DOM id·`data-*` 속성을 유지한다 — E2E와 사용자 눈 모두의 기준이다.
- `dangerouslySetInnerHTML`은 픽셀 페인터가 만든 SVG처럼 **사용자 입력이 섞이지 않는 생성 마크업**에만 쓴다. handle·메모·지도 이름은 반드시 JSX 텍스트로 렌더한다(XSS).

## 빌드·통합

- `./gradlew build`가 프론트 빌드를 포함하게 한다: Gradle node 플러그인(또는 동등)으로 `npm ci && npm run build` → `frontend/dist`를 app-api의 `static/`로 **빌드 시점에 복사**(산출물을 git에 커밋하지 않는다). 그래서 jar·Docker 이미지·`bootRun`·E2E가 기존처럼 동작한다.
- 개발: `npm run dev`(Vite)가 API 경로를 Spring 서버로 프록시한다(프록시 대상 경로 목록은 `vite.config.ts` 한 곳).
- `npm run check` = `tsc --noEmit` + `eslint` + `vitest run`. CI·완료 기준에 포함.

## 테스트

- **테스트 이름은 도메인 문장으로**(백엔드 implement-context "테스트 작성 규칙"과 같은 원칙): Vitest `describe`는 개념·상황("체크인 미리보기", "시·도 첫 방문일 때"), `it`은 결과 문장("보너스 줄을 보여 준다"). Playwright `test.describe`/`test` 제목도 사용자 이야기("익명으로 칠한 영토는 로그인해도 그대로 남는다"). QA 번호·컴포넌트/함수 이름·구현 용어(쿼리 키, refetch, 프레임, 선택자, 상태 코드)는 제목에 넣지 않는다. 기능 폴더마다 화면 규칙을 빠짐없이 덮고, 같은 기능의 테스트는 한 파일에 이야기 순서로 모은다.
- 단위(Vitest): `api/client.ts`(헤더·CSRF·에러 변환·코드 변환), `features/*/model/` 순수 함수, `shared/lib`(투영·페인터 메모이즈), 핵심 훅(mutation 후 무효화).
- E2E(Playwright, `e2e/`): 기존 스펙 전체가 회귀 기준. 새 화면 동작에는 스펙을 추가한다.
- 성능 회귀 감시: 체크인 후 캐릭터 이동 중 역행 0회(프레임 샘플링) 같은 측정 스펙을 유지한다.
