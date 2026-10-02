# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

`territory` (나의 영토) — Spring Boot 3.5.6, Java 21, Gradle 8.14 (Groovy DSL) multi-module DDD backend. Group `com.kobi`, base package `com.kobi.territory`, port 8080. Design source of truth: the two PDFs in `doc/`.

Modules (`settings.gradle`):
- `common` — shared kernel (`common.event.DomainEvent`); only `spring-context`.
- `catalog`, `exploration`, `progression`, `wardrobe`, `social`, `sharing` — bounded contexts (`java-library`, no bootJar). Each has fixed packages `api / application / domain / infra` under `com.kobi.territory.<module>`. `domain` stays pure Java (no Spring/JPA).
- `app-api` — the only Spring Boot app (`TerritoryApplication`, bootJar `territory.jar`). Holds `TerritoryProperties` (`territory.*` in `application.yml`), `HealthController` (`GET /health`), `DevController` (`@Profile("local")`: `DELETE /dev/reset`, `POST /dev/seed`), `ArchitectureTest` (ArchUnit module-boundary rules), and the prototype UI at `src/main/resources/static/index.html` (copy of `doc/나의 영토.html`, served at `GET /`).

Dependency rules (stage 0): every domain module → `common`; `exploration`, `wardrobe` → `catalog` (read-only); `app-api` → all. Cross-context references are otherwise forbidden and enforced by `ArchitectureTest`; from stage 1 they evolve to "api-package only" (see `implement-context` skill).

Profiles: `local` (default, H2 in-memory, `/h2-console`) / `prod` (needs `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`; fails to start without them).

E2E: `e2e/` is a Playwright (TypeScript, chromium) project. Its `webServer` starts `./gradlew :app-api:bootRun` and waits on `/health`; every test calls `DELETE /dev/reset` first (`e2e/fixtures.ts`). Manual requests: `http/stage0.http`.

## Commands

```bash
./gradlew projects                       # list modules (8)
./gradlew clean build                    # compile + test all modules (app-api/build/libs/territory.jar)
./gradlew :app-api:bootRun               # run on :8080 with local profile
./gradlew :app-api:bootRun --args='--server.port=18080'   # run on another port
./gradlew test                           # run all tests
./gradlew :app-api:test --tests "com.kobi.territory.ArchitectureTest"   # single test class
./gradlew :app-api:test --tests "*DevControllerLocalProfileTest.*"     # single test method/pattern
java -jar app-api/build/libs/territory.jar --spring.profiles.active=prod  # prod (needs DB_* env)

# E2E (Playwright)
cd e2e && npm install && npx playwright install chromium   # first time
cd e2e && npm test                                          # all E2E tests (starts bootRun if not running)
cd e2e && npm run test:stage0                               # stage 0 boot spec only
cd e2e && E2E_PORT=18080 npx playwright test                # if 8080 is taken by another process
```

## 하네스: 나의 영토(territory) 백엔드 구현

**목표:** `doc/`의 설계 문서 2종(셋업 가이드 · 도메인 분석)에 따라 멀티모듈 DDD 백엔드를 단계별(뼈대 → 카탈로그+탐험 → 진행 → 꾸미기 → 공유 → 소셜)로 구현한다.

**트리거:** territory/나의 영토 구현·수정·검증·단계 진행 작업 요청 시 `territory-orchestrator` 스킬을 사용하라. 단순 질문은 직접 응답 가능.

**변경 이력:**
| 날짜 | 변경 내용 | 대상 | 사유 |
|------|----------|------|------|
| 2026-10-02 | 초기 구성 (에이전트 2종: context-builder, architecture-qa / 스킬 4종: territory-orchestrator, scaffold-multimodule, implement-context, verify-architecture) | 전체 | - |
| 2026-10-02 | 드라이런 교차 점검 결함 수정: 보고 파일명 컨벤션 통일({NN} zero-pad), 0단계 스킬 경로 명시, 의존 규칙 진화(api-예외) 정의, 공개 이벤트 위치(api)·설정값 주입 경로(정책 VO) 확정, claim refId에 수령자 추가, ExpeditionMap→exploration 소속·V1 포함, Flyway 단계 매핑·local 전환 명시 | 에이전트 2종 + 스킬 4종 전체 | 드라이런 점검에서 P1 4건·P2 8건 발견 |
| 2026-10-03 | 얇은 application 서비스 규칙 추가(서비스=DB 접근·호출 순서만, 로직·흐름은 도메인+일급 컬렉션), QA 3층 점검 항목 추가 | implement-context, verify-architecture | 사용자 설계 지시 |