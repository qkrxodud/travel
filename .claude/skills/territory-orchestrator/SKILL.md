---
name: territory-orchestrator
description: 나의 영토(territory) 백엔드·웹 프론트엔드 구현 작업 전체를 조율하는 오케스트레이터. 멀티모듈 뼈대 생성, 단계별 구현(카탈로그·탐험·진행·꾸미기·공유·소셜), 체크인/XP/도감/인벤토리/공유지도 등 도메인 기능 개발, React 프론트 이전·화면 기능 추가(백엔드+화면이 함께 바뀌는 기능 포함) 요청 시 반드시 이 스킬을 사용할 것. "다음 단계 진행해줘", "1단계 해줘", "다시 실행", "재실행", "수정해줘", "보완해줘", "업데이트", "이전 결과 기반으로 개선", "{모듈}만 다시" 같은 후속 요청도 모두 이 스킬로 처리한다. 하네스 자체의 수정은 harness 스킬, 단순 질문은 직접 응답.
---

# Territory Orchestrator — 구현 워크플로우 조율

`doc/`의 설계 문서 2종(셋업 가이드, 도메인 분석)에 따라 territory 백엔드를 단계별로 구현한다. 단계 로드맵과 도메인 스펙은 `.claude/skills/implement-context/references/domain-model.md`(특히 §6 진행 순서)가 기준이다.

## Phase 0: 컨텍스트 확인

실행 전에 반드시 현재 상태를 판별한다:

1. `_workspace/` 존재 여부와 내용(이전 단계 보고·QA 파일), 레포의 구현 상태(모듈 존재 여부, Flyway 버전)를 확인한다.
2. 실행 모드 결정:
   - `_workspace/` 없음 + 멀티모듈 아님 → **초기 실행**: 0단계(뼈대)부터.
   - 이전 산출물 있음 + 사용자가 부분 수정 요청 → **부분 재실행**: 해당 모듈의 context-builder만 재호출하고 QA를 다시 돌린다. 팀 전체를 다시 만들지 않는다.
   - 이전 산출물 있음 + 새 단계 요청 → **다음 단계 실행**: 기존 `_workspace/` 보존, 새 단계 파일 추가.
   - 사용자가 처음부터 다시 요청 → 기존 `_workspace/`를 `_workspace_prev/`로 이동 후 새 실행.
3. 사용자 요청이 어느 단계·어느 모듈에 해당하는지 domain-model.md §6과 대조해 범위를 확정하고, 시작 전에 한 줄로 알린다.

## 실행 모드 (하이브리드)

| 단계 | 실행 모드 | 이유 |
|------|----------|------|
| 0단계: 뼈대 | **서브 에이전트** — context-builder 1명 직접 호출(Agent 도구, `model: "opus"`). 지시에 "**scaffold-multimodule 스킬을 따르라**(implement-context 아님)"를 명시한다. 완료 후 architecture-qa 1명으로 검증 | 단일 선형 작업이라 팀 통신 오버헤드가 이득보다 큼 |
| 1~5단계: 구현 | **에이전트 팀** — TeamCreate로 context-builder 1~2명 + architecture-qa 1명 구성 | 모듈 완성 직후 incremental QA, 결함 리포트 ↔ 수정의 실시간 교환이 필요 |

모든 Agent/팀원 호출에 `model: "opus"`를 명시한다.

## 단계 실행 절차 (1~5단계 공통)

1. **작업 분해**: domain-model.md §6에서 해당 단계의 산출물을 TaskCreate로 등록한다. 한 태스크 = 한 모듈 범위(예: 1단계 = "catalog 구현", "exploration 구현", "app-api 통합+Flyway V1"). 의존 관계를 태스크에 명시한다.
2. **팀 구성**: context-builder(들) + architecture-qa로 TeamCreate. 독립 모듈이 2개면 context-builder 2명 병렬(예: 1단계의 catalog와 exploration 초기 작업).
3. **진행**: 팀원들이 자체 조율한다 — context-builder는 모듈 완성 직후 architecture-qa에게 SendMessage로 검증 요청, QA는 결함을 builder에게 직접 리포트. 리더는 태스크 상태를 모니터링하고 설계 해석 분쟁만 중재한다.
4. **수렴 기준**: QA P1·P2 결함 0 + `./gradlew clean build` 통과 + `docker build` 통과 + 해당 단계 API curl 왕복 확인. 커밋 후 운영 compose 반영 순서: `scripts/backup.sh` → `docker tag territory-app:latest territory-app:prev` → `docker compose up -d --build app` → healthy·Flyway 로그 확인.
5. **종합**: `_workspace/{단계}_summary.md`에 산출물·검증 결과·미해결 사항을 기록하고 팀을 정리한 뒤 사용자에게 보고한다.

## 프론트엔드 작업 (frontend/)

프론트 구현 규칙은 `implement-frontend` 스킬, 담당은 `frontend-builder` 에이전트다(`model: "opus"`).

| 작업 유형 | 실행 모드 | 구성 |
|----------|----------|------|
| 화면만 바뀜(이전·UI 버그·화면 기능) | 서브 에이전트 | frontend-builder 1명 → architecture-qa 1명(verify-architecture "프론트엔드 검증" 절) |
| 백엔드+화면이 함께 바뀌는 기능 | 에이전트 팀(가능하면) / 서브 병렬 | context-builder(API 먼저) + frontend-builder(화면) + architecture-qa. API 계약은 `_workspace/{NN}_contracts.md`에 context-builder가 먼저 쓰고 frontend-builder가 그것에 맞춘다. 같은 디렉터리에서 동시에 gradle·playwright를 돌리면 결과 파일이 깨지므로 포트·빌드 시점을 나눈다(중간 검증은 scratchpad 복사본) |

- 프론트 단계 번호는 백엔드 단계 다음 번호를 쓴다(예: React 이전 = `06`). 보고 파일 `_workspace/{NN}_frontend_report.md`, QA `_workspace/{NN}_qa_frontend.md`.
- 완료 기준: `npm run check` + `./gradlew clean build`(프론트 빌드 포함) + E2E 전체 2회 연속 + (이전 작업이면) 프로토타입과 화면 동등성 확인.
- E2E·bootRun 포트는 18081~18089(18080은 운영 compose 자리). 사용자 확인용 서버는 커밋 시점 worktree(`../travel-stageN`)에서 18090으로 띄운다. 운영 compose(`docker compose`, 18080)는 사용자가 띄워 둔 것일 수 있으니 재빌드·재시작 전에 사용자에게 알린다.

## 데이터 전달 프로토콜

- **태스크 기반**(조율): TaskCreate/TaskUpdate로 작업 상태·의존 관계 공유.
- **파일 기반**(산출물): `_workspace/` 하위. 파일명은 두 가지로 고정 — builder 보고 `{NN}_{모듈}_report.md`, QA 보고 `{NN}_qa_{모듈}.md`. `{NN}`은 단계 번호 2자리 zero-pad(예: `01_exploration_report.md`, `01_qa_exploration.md`). 중간 파일은 삭제하지 않는다(감사 추적·부분 재실행용).
- **메시지 기반**(실시간): 검증 요청·결함 리포트는 SendMessage로 직접.

## 에러 핸들링

- 에이전트 실패(빌드 불가 보고 등): 원인을 요약해 1회 재지시. 재실패 시 작업을 멈추고 실패 내용을 사용자에게 그대로 보고한다 — 우회 구현으로 단계를 "완료"시키지 않는다.
- QA ↔ builder가 설계 해석으로 2회 이상 왕복하면 리더가 doc/ 원본 PDF를 직접 확인해 판정한다. 원본에도 없으면 사용자에게 질문한다.
- 설계 문서와 충돌하는 기존 코드 발견: 삭제 전 사용자에게 확인(0단계의 Initializr 교체는 scaffold-multimodule 스킬에 명시된 범위라 예외).

## 완료 보고와 피드백

단계 완료 시 보고에 포함한다: 생성 모듈·API 목록, 검증 결과(빌드·curl·QA), 다음 단계 안내(domain-model.md §6 기준). 보고 후 "결과나 워크플로우에서 개선할 점이 있는지" 한 번 묻는다(강요하지 않는다). 반복되는 피드백은 harness 스킬로 하네스 자체를 수정한다.

## 테스트 시나리오

**정상 흐름 — "프론트 React로 옮겨줘"**: Phase 0에서 `frontend/` 부재 확인 → 프론트 작업(이전) 판별 → frontend-builder가 implement-frontend 스킬로 구현·E2E 20건 통과 → architecture-qa 프론트 검증(API↔TS 타입 교차 비교, 규칙 grep, 화면 동등성) → 결함 수정 왕복 → 커밋 → 18090 확인 서버.

**에러 흐름 — 프론트가 서버 응답에 없는 값을 필요로 함**: frontend-builder가 클라이언트에서 재계산하지 않고 리더에게 보고 → 리더가 context-builder에게 API 확장 지시 → 계약 파일 갱신 → frontend-builder 재개.

**정상 흐름 — "1단계 진행해줘"**: Phase 0에서 뼈대 존재 확인 → 없으면 0단계부터 제안 → 있으면 TaskCreate(카탈로그, 탐험, 통합) → 팀 구성 → catalog·exploration 병렬 구현 → 각 모듈 완성 직후 QA → 결함 수정 왕복 → V1 마이그레이션 + API curl 왕복(POST /visits → GET /territory → DELETE) 확인 → summary 기록 → 보고.

**에러 흐름 — QA가 exploration에서 P2(핸들러 멱등성 누락) 발견**: QA가 builder에게 SendMessage 리포트 → builder가 refId 유니크 제약 추가 수정 → QA 회귀 확인 → 통과. builder가 "설계상 불필요"라고 반박하면 리더가 domain-model.md §2-2·원본 PDF로 판정.

**에러 흐름 — 빌드가 Gradle 버전 비호환으로 실패**: builder가 원인 분석 보고 → 리더가 scaffold-multimodule 스킬의 Gradle 호환 절차(8.14로 다운그레이드)를 지시 → 재시도. 재실패 시 사용자 보고.
