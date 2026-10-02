---
name: verify-architecture
description: 나의 영토(territory) 멀티모듈의 빌드·아키텍처 규칙·이벤트 경계면 정합성을 검증한다. "검증해줘", "QA", "빌드 확인", "아키텍처 테스트", "경계 점검", "제대로 됐는지 확인" 요청이나 architecture-qa 에이전트가 모듈 완성 직후 검증할 때 반드시 이 스킬을 사용할 것.
---

# Verify Architecture — 검증 절차

검증은 세 층이다: **실행 검증**(빌드·테스트가 도는가) → **경계면 교차 비교**(모듈이 맞닿는 지점의 양쪽 코드가 일치하는가) → **설계 대조**(불변식·스키마가 설계 문서와 같은가). 존재 확인("파일이 있다")은 검증이 아니다.

## 1층. 실행 검증

```bash
./gradlew clean build          # 컴파일 + 단위 테스트 + ArchUnit
./gradlew :app-api:bootRun     # 띄워서 (백그라운드)
curl localhost:8080/health     # 설정 바인딩 확인 (dailyCap 5, leaveGraceDays 7)
curl localhost:8080/actuator/health
```

- 빌드 산출물 확인: `app-api/build/libs/territory.jar`만 bootJar여야 한다. 도메인 모듈에 bootJar가 생겼으면 루트 `apply false` 누락.
- API가 추가된 단계면 해당 엔드포인트를 curl로 실제 호출한다(체크인 → 영토 조회 → 취소 왕복).

## 2층. 경계면 교차 비교

모듈 간 맞닿는 지점의 **양쪽 코드를 동시에 열고** 비교한다. 비교할 경계면:

| 경계면 | 발행 측 | 구독 측 | 비교할 것 |
|--------|---------|---------|----------|
| RegionVisited | exploration.api | progression·wardrobe·social·sharing 핸들러 | payload 필드명·타입, isFirstInProvince/nth/isFirstClaim이 발행 측(애그리거트 계산 → application 변환)에서 채워지는가 |
| VisitCancelled | exploration.api | progression·wardrobe | 취소의 비대칭성: 지역 아이템·기본 XP만 회수, 세트·뱃지·퀘스트 보상 유지 |
| SetCompleted | progression.api | wardrobe·progression | 세트 배경이 지도 멤버 전원에게 가는가 |
| ItemGranted/Revoked | wardrobe 내부 | Scene | 착용 중 회수 시 벗겨지는가, autoEquip 조건 |
| refId 규칙 | 발행 이벤트 | xp_ledger 적재 | `region:{explorerId}:{code}` / `claim:{mapId}:{code}:{explorerId}` 형식 일치, UNIQUE 제약 존재 |
| Query 인터페이스 | 각 컨텍스트 api | sharing·social 호출부 | 시그니처 일치, domain 타입이 api 밖으로 새지 않는가 |
| Flyway vs JPA 엔티티 | V*.sql | infra 엔티티 | 컬럼명·타입·유니크 제약 일치 |

## 3층. 설계 대조

`.claude/skills/implement-context/references/domain-model.md`의 해당 애그리거트 섹션과 코드를 대조한다:

- 불변식이 애그리거트 메서드 안에서 지켜지는가(서비스 레이어에서만 막고 있으면 규칙 위반).
- **infra(JPA) 규칙**(implement-context "infra(JPA) 규칙"): 한 파일에 `@Entity`나 Spring Data 리포지토리가 2개 이상이면 P3, 리포지토리 어댑터에 필드 단위 도메인↔엔티티 매핑 코드가 있으면 P3(변환은 엔티티의 `from`/`apply`/`toDomain`). `grep -c "@Entity"`로 확인.
- **일급 컬렉션 규칙**(implement-context "명명 규칙"): 컬렉션을 감싼 `record`(`grep -rnE "record [A-Za-z]+\((List|Set|Map)<"` in domain), 원본 컬렉션 반환 게터, 호출부의 `.xxx().stream().filter`, 클래스명과 다른 원소 복수형 변수명(`QuestRules quests`), 클래스명에 JDK 자료형 단어(`Set`, `List`, `Map`, `Collection` 등 — 예: `SetCatalog`)는 P3.
- **domain 하위 패키지**(implement-context "domain 하위 패키지 구성"): `domain/` 바로 아래에 애그리거트 클래스가 흩어져 있거나, 종류별(`vo/`·`repository/`) 폴더면 P3. 애그리거트 폴더 간 내부 구현 참조(다른 애그리거트의 엔티티·일급 컬렉션 직접 사용)도 P3.
- **class vs record 기준**(implement-context "class vs record 기준"): 애그리거트·엔티티·일급 컬렉션·행동 있는 값 객체가 record면 P3, 컬렉션 필드가 있는 record에 방어 복사(`copyOf`)가 없으면 P3. domain 패키지 `grep -rn "record "`로 전수 확인.
- **infra 하위 패키지**: JPA 엔티티는 `infra/entity/`, Spring Data 리포지토리·어댑터는 `infra/repository/`에 있어야 한다(위반 P3). infra 엔티티·Spring Data 타입을 infra 밖(application·api)에서 참조하면 P2.
- **생성자 주입**: main 코드에 `@Autowired` 필드·세터 주입이 있으면 P3(`grep -rn "@Autowired" */src/main`).
- **리포지토리 어댑터 판단 금지**(implement-context "infra(JPA) 규칙"): 어댑터에 호출 의도 분기(`rebuilt()` 류 플래그), `Clock` 주입, 비즈니스 판단, 재계산용 diff 코드가 있으면 P3. 포트가 테이블 단위(`appendLedger` 등)로 쪼개져 서비스가 테이블 구조를 알게 되면 P3.
- **명명 규칙**(implement-context "명명 규칙"): 한 글자 변수·파라미터·람다 파라미터(인덱스 루프 `i`/`j` 제외)와 JDK·Spring 타입과 같은 이름의 도메인 클래스는 P3 — 변경된 파일 전체를 grep으로 확인(예: `grep -rnE "\b[A-Z][A-Za-z]+ [a-z]\b[,)= ]"`, `-> ?[a-z] ?->`/`\b[a-z] ->`).
- **application 서비스가 얇은가**: 서비스는 조회·저장·잠금·outbox·정책 VO 조립과 호출 순서만 가진다. 판단·계산·검증·분기(if/반복문으로 된 비즈니스 규칙)가 서비스에 있으면 P2 — 애그리거트·VO·도메인 서비스·일급 컬렉션으로 옮겨야 한다(implement-context 레이어 규칙).
- domain 패키지에 Spring·JPA 어노테이션이 없는가 (ArchUnit이 잡지만 `jakarta.persistence` 규칙을 빼고 시작했을 수 있으니 grep으로도 확인).
- 모든 이벤트 핸들러가 멱등한가 — refId/유니크 키 없이 insert하는 핸들러는 결함.
- 트랜잭션 경계: 커맨드가 애그리거트 두 개를 한 트랜잭션에서 수정하면 결함(outbox 적재는 예외).
- 하드코딩된 게임 규칙 값(5, 72, 7, 10) — TerritoryProperties 주입이어야 한다.

## 결함 보고 형식

`_workspace/{NN}_qa_{모듈}.md`에 기록(`{NN}` = 단계 번호 2자리 zero-pad):

```markdown
# QA: {모듈} ({통과|실패})
## 실행한 검증
- 명령과 결과 요약
## 결함
| # | 심각도 | 위치 | 내용 | 근거(설계 문서/스킬 규칙) |
심각도: P1 빌드·동작 깨짐 / P2 아키텍처 규칙 위반 / P3 권고
## 질문 (결함 아님, 리더 판단 필요)
```

P1·P2가 하나라도 있으면 "실패"로 보고하고 context-builder에게 리포트를 보낸다. P3만 있으면 통과로 보고하되 목록을 남긴다.
