# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

`territory` (나의 영토) — Spring Boot 3.5.6, Java 21, Gradle 8.14 (Groovy DSL) multi-module DDD backend. Group `com.kobi`, base package `com.kobi.territory`, port 8080. Design source of truth: the two PDFs in `doc/`.

Modules (`settings.gradle`):
- `common` — shared kernel (`common.event.DomainEvent`); only `spring-context`.
- `catalog`, `exploration`, `progression`, `wardrobe`, `social`, `sharing` — bounded contexts (`java-library`, no bootJar). Each has fixed packages `api / application / domain / infra` under `com.kobi.territory.<module>`. `domain` stays pure Java (no Spring/JPA).
- `app-api` — the only Spring Boot app (`TerritoryApplication`, bootJar `territory.jar`). Holds `TerritoryProperties` (`territory.*` in `application.yml`), `HealthController` (`GET /health`), `DevController` (`@Profile("local")`: `DELETE /dev/reset`, `POST /dev/seed`), `ArchitectureTest` (ArchUnit module-boundary rules), and serves the web UI at `GET /` — the React app in `frontend/` (Vite + React + TypeScript), built by Gradle and copied into `static/` at build time (never committed). The pre-React vanilla UI is kept for comparison/rollback at `doc/legacy-index.html`.

Dependency rules (stage 0): every domain module → `common`; `exploration`, `wardrobe` → `catalog` (read-only); `app-api` → all. Cross-context references are otherwise forbidden and enforced by `ArchitectureTest`; from stage 1 they evolve to "api-package only" (see `implement-context` skill).

Profiles: `local` (default, H2 in-memory, `/h2-console`) / `prod` (needs `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`; fails to start without them).

Frontend: `frontend/` (React 18 · TanStack Query · zustand · D3 7 · Vitest). `./gradlew build` runs `npm ci` → `npm run build` (Node 24.7.0 downloaded by the Gradle node plugin into `.gradle/nodejs`) and `npm run check` (tsc + eslint + vitest) as part of `check`; `processResources` copies `frontend/dist` → `static/`. Rules: `implement-frontend` skill.

E2E: `e2e/` is a Playwright (TypeScript, chromium) project. Its `webServer` starts `./gradlew :app-api:bootRun` and waits on `/health`; every test calls `DELETE /dev/reset` first (`e2e/fixtures.ts`). Manual requests: `http/stage0.http`.

## Commands

```bash
./gradlew projects                       # list modules (8)
./gradlew clean build                    # compile + test all modules (app-api/build/libs/territory.jar)
./gradlew :app-api:bootRun               # run on :8080 with local profile
./gradlew :app-api:bootRun --args='--server.port=18080'   # run on another port
./gradlew test                           # run all tests
./gradlew testDocs                       # rule docs from test sources in declaration order (+ ✓/✗ from last results) → build/reports/test-docs/{module}.md, index.md
./gradlew :app-api:test --tests "com.kobi.territory.ArchitectureTest"   # single test class
./gradlew :app-api:test --tests "*DevControllerLocalProfileTest.*"     # single test method/pattern
java -jar app-api/build/libs/territory.jar --spring.profiles.active=prod  # prod (needs DB_* env)

# Frontend (frontend/ — Vite + React + TypeScript). ./gradlew build already builds it; these are for front-only work
cd frontend && npm ci                                       # first time (or use ./gradlew :app-api:npmInstall)
cd frontend && npm run dev                                  # Vite :5173, API paths proxied to Spring (VITE_API_TARGET, default http://localhost:18081)
cd frontend && VITE_API_TARGET=http://localhost:18081 npm run dev   # when the API server runs on another port
cd frontend && npm run check                                # tsc --noEmit + eslint + vitest run
cd frontend && npx vitest run src/api/client.test.ts        # single unit test file
cd frontend && npm run build                                # frontend/dist only (Gradle copies it into app-api static/)
./gradlew :app-api:frontendBuild                            # same via Gradle (Node downloaded by the node plugin)

# E2E (Playwright)
cd e2e && npm install && npx playwright install chromium   # first time
cd e2e && npm test                                          # all E2E tests (starts bootRun if not running)
cd e2e && npm run test:app-shell                            # app shell spec only (tests/<domain>.spec.ts: app-shell, map-check-in, progress, wardrobe, shared-map, account, sharing, social, character-move)
cd e2e && E2E_PORT=18080 npx playwright test                # if 8080 is taken by another process

# Docker Compose local operation (prod profile, MySQL 8.4, 127.0.0.1:18080) — see doc/operations.md "Docker Compose 로컬 운영"
cp .env.example .env                                        # first time: fill secrets (.env is gitignored)
docker compose up -d --build                                # start / redeploy (project: territory)
docker compose logs -f app                                  # logs
docker compose stop                                         # stop (volumes kept; never `down -v` in operation)
scripts/backup.sh && scripts/restore.sh backups/<file>.sql.gz   # mysqldump backup / restore
docker compose -p territory-e2e -f compose.yaml -f compose.e2e.yaml up -d --build --wait   # E2E on MySQL (dev endpoints, :18090)
cd e2e && E2E_PORT=18090 npx playwright test
docker compose -p territory-e2e -f compose.yaml -f compose.e2e.yaml down -v
```

## 하네스: 나의 영토(territory) 백엔드 구현

**목표:** `doc/`의 설계 문서 2종(셋업 가이드 · 도메인 분석)에 따라 멀티모듈 DDD 백엔드를 단계별(뼈대 → 카탈로그+탐험 → 진행 → 꾸미기 → 공유 → 소셜)로 구현하고, 웹 프론트(frontend/, React)를 함께 유지한다.

**트리거:** territory/나의 영토 구현·수정·검증·단계 진행 작업 요청 시 `territory-orchestrator` 스킬을 사용하라. 단순 질문은 직접 응답 가능.

**변경 이력:**
| 날짜 | 변경 내용 | 대상 | 사유 |
|------|----------|------|------|
| 2026-10-02 | 초기 구성 (에이전트 2종: context-builder, architecture-qa / 스킬 4종: territory-orchestrator, scaffold-multimodule, implement-context, verify-architecture) | 전체 | - |
| 2026-10-02 | 드라이런 교차 점검 결함 수정: 보고 파일명 컨벤션 통일({NN} zero-pad), 0단계 스킬 경로 명시, 의존 규칙 진화(api-예외) 정의, 공개 이벤트 위치(api)·설정값 주입 경로(정책 VO) 확정, claim refId에 수령자 추가, ExpeditionMap→exploration 소속·V1 포함, Flyway 단계 매핑·local 전환 명시 | 에이전트 2종 + 스킬 4종 전체 | 드라이런 점검에서 P1 4건·P2 8건 발견 |
| 2026-10-03 | 얇은 application 서비스 규칙 추가(서비스=DB 접근·호출 순서만, 로직·흐름은 도메인+일급 컬렉션), QA 3층 점검 항목 추가 | implement-context, verify-architecture | 사용자 설계 지시 |
| 2026-10-03 | 명명 규칙 추가(한 글자 변수·람다 파라미터 금지, JDK 타입과 같은 도메인 클래스명 금지 → 도감 `Collection`을 `CollectionBook`으로), QA 3층 점검 항목 추가 | implement-context, verify-architecture | 사용자 코드 리뷰 지적 |
| 2026-10-03 | infra(JPA) 규칙 추가(엔티티·Spring Data 리포지토리 1개=파일 1개, 도메인↔엔티티 변환은 엔티티의 from/apply/toDomain), QA 3층 점검 항목 추가 | implement-context, verify-architecture | 사용자 코드 리뷰 지적(ProgressJpaEntities 중첩 묶음) |
| 2026-10-03 | 일급 컬렉션·클래스명 규칙 추가(클래스명에 JDK 자료형 단어 금지 → 세트 `SetCatalog`/`CollectionSet`/`SetProgress`를 `Themes`/`Theme`/`ThemeProgress`로, 변수·접근자명은 클래스명 따름, record 금지·원본 컬렉션 게터 금지), QA 3층 점검 항목 추가 | implement-context, verify-architecture | 사용자 코드 리뷰 지적(`SetCatalog` 클래스명) |
| 2026-10-03 | 리포지토리 어댑터 규칙 추가(애그리거트 단위 저장 유지, 어댑터는 저장 기술만 — 의도 분기·Clock·diff 금지, 재계산은 replace), QA 3층 점검 항목 추가 | implement-context, verify-architecture | 사용자 결정(A안: JpaExplorerProgressRepository 리뷰) |
| 2026-10-03 | class vs record 기준 추가(애그리거트·엔티티·일급 컬렉션·행동 있는 VO=class / 단순 VO·결과·정책·커맨드·이벤트·DTO·정의=record, 컬렉션 필드 copyOf), QA 3층 점검 항목 추가 | implement-context, verify-architecture | 사용자 결정(도메인 record 리뷰) |
| 2026-10-03 | domain 하위 패키지 구성 규칙 추가(애그리거트별 폴더, 리포지토리 포트 동거, 종류별 분류 금지), QA 3층 점검 항목 추가 | implement-context, verify-architecture | 사용자 요청(domain 한눈에 보이게) |
| 2026-10-03 | infra 하위 패키지 규칙 추가(infra/entity, infra/repository 2개로 종류별 분리 — 애그리거트 구분은 domain이 담당), QA 점검 항목 추가 | implement-context, verify-architecture | 사용자 결정 |
| 2026-10-03 | 의존성 주입 규칙 명시(생성자 주입만), QA 점검 항목 추가 | implement-context, verify-architecture | 3단계 QA r3 P3-R3-5(DevController 필드 주입) |
| 2026-10-03 | 프론트엔드 확장: 에이전트 frontend-builder, 스킬 implement-frontend(Vite+React+TS, API 계층 단일화, TanStack Query/zustand, D3 엔진 통합, 빌드 산출물 app-api static 통합), verify-architecture에 "프론트엔드 검증" 절(API↔TS 타입 교차 비교), 오케스트레이터에 프론트 작업 흐름 | agents/frontend-builder, skills/implement-frontend, verify-architecture, architecture-qa, territory-orchestrator | 사용자 결정(프론트 React 이전 + 프론트 규칙) |
| 2026-10-04 | 테스트 작성 규칙 추가(DisplayName·describe·E2E 제목은 도메인 문장, QA 번호·구현 용어 금지, @Nested 이야기 구조, 불변식·커맨드별 빠짐없는 대응), QA 점검 항목 추가 | implement-context, implement-frontend, verify-architecture | 사용자 지시(테스트가 문서처럼 읽히게, 띄엄띄엄한 구성 개선) |
| 2026-10-04 | 단계 수렴 기준에 운영 이미지 빌드(docker build) 추가, 운영 compose 반영 절차(백업 → prev 태그 → 재빌드) 명시 | verify-architecture, territory-orchestrator | 8단계 배포 시 Dockerfile buildSrc 누락으로 이미지 빌드 실패(QA 기준에 docker build 없음) |
