---
name: implement-context
description: 나의 영토(territory) 바운디드 컨텍스트의 도메인 코드를 구현한다. 애그리거트(Territory, ExplorerProgress, Collection, QuestBoard, Inventory, Scene, Friendship, ShareCard, ExpeditionMap), 체크인 API, 이벤트 핸들러, JPA 인프라, Flyway 마이그레이션 등 도메인 구현·수정·보완 작업 전부에 반드시 이 스킬을 사용할 것. 뼈대 생성에는 scaffold-multimodule을 쓴다.
---

# Implement Context — 바운디드 컨텍스트 구현 규칙

`doc/나의 영토 도메인 분석 · 애그리거트 설계.pdf`를 구현 규칙으로 옮긴 스킬이다. 담당 애그리거트의 상세 스펙(루트·VO·불변식·커맨드·이벤트·테이블)은 **`references/domain-model.md`에서 해당 섹션을 읽고 그대로 따른다.** 이 본문은 모든 컨텍스트에 공통인 규칙만 담는다.

## 레이어 규칙 (모듈 내 4패키지)

| 패키지 | 담는 것 | 의존 허용 |
|--------|---------|----------|
| `api` | `api.event`: 다른 컨텍스트에 공개하는 이벤트 · `api.query`: 공개 Query 인터페이스와 그 DTO · `api.web`: Controller·웹 DTO (2단계 D7) | application(api.web만), 다른 컨텍스트의 api.event·api.query |
| `application` | UseCase 서비스, 트랜잭션 경계, 이벤트 발행/구독 핸들러 | domain |
| `domain` | 애그리거트, VO, 도메인 이벤트, Repository 인터페이스 | common만 |
| `infra` | JPA 엔티티/리포지토리 구현, 외부 클라이언트 | domain, application |

- **application 서비스는 얇게 (사용자 확정 규칙)**: 서비스에는 리포지토리 조회·저장, 트랜잭션·잠금, 이벤트/outbox 적재, 정책 VO 조립 같은 **DB·인프라 접근과 호출 순서만** 둔다. 판단·계산·검증·분기(상한 체크, nth·isFirstInProvince 계산, 보상 계산, 정복률 집계 등)는 전부 애그리거트·VO·도메인 서비스, 그리고 **일급 컬렉션**(예: `Visits`, `OwnedItems`, `XpLedger`)으로 옮긴다. 서비스에 `if`/반복문으로 된 비즈니스 규칙이 보이면 QA 결함이다. 이유: 규칙을 Spring 없이 단위 테스트하고, 서비스는 "불러와서 → 도메인에 시키고 → 저장"만 읽히게.
- domain은 순수 Java — Spring·JPA 어노테이션 금지. JPA 엔티티는 infra에 따로 두고 리포지토리가 변환한다. 이유: 애그리거트 단위 테스트가 컨텍스트 없이 돌고, 컨텍스트를 서비스로 떼어낼 때 모듈째 가져갈 수 있다.
- 다른 컨텍스트는 그 컨텍스트의 `api.event`·`api.query` 패키지만 참조한다(`api.web`·domain·application·infra 금지). `api.event`·`api.query`는 자기 domain·application·infra·api.web 을 참조하지 않는다. Gradle로는 못 막으므로 ArchUnit 규칙으로 강제한다 — 위반하면 빌드가 깨진다.
- **의존 규칙의 진화**: 뼈대(0단계)의 Gradle 의존·ArchUnit 규칙은 "도메인 모듈 간 참조 전면 금지"다. 구독 관계가 생기면 domain-model.md §1의 의존 매트릭스에 맞춰 **둘을 함께 갱신한다**: Gradle에 `implementation project(':exploration')` 등 허용된 의존을 추가하고, ArchUnit 규칙은 **허용 목록**으로 쓴다 — "다른 컨텍스트 클래스는 `..X.api.event..`·`..X.api.query..`만 참조 가능"(X.api 루트·api.web·기타 하위 패키지·domain·application·infra 는 모두 금지, 2단계 D7·QA P2-1), "X.api 아래에는 event·query·web 만", "api.event·api.query 는 공개 계약 밖을 참조하지 않는다". 금지 목록으로 쓰면 새 하위 패키지가 빈틈이 된다. 둘 중 하나만 고치면 컴파일 불가 또는 ArchUnit 실패가 난다.
- **공개 이벤트의 위치**: `RegionVisited` 같은 컨텍스트 간 이벤트는 그 컨텍스트의 `api.event` 패키지에 둔다. domain은 api를 참조할 수 없으므로, 애그리거트 커맨드는 계산 결과(지급 목록, isFirstInProvince 등)를 **결과 객체로 반환**하고, application 서비스가 이를 api의 공개 이벤트로 변환해 outbox에 적재한다.
- **게임 규칙 값의 주입 경로**: domain은 Spring을 모르므로 `TerritoryProperties`를 직접 주입받을 수 없다. app-api의 `TerritoryProperties`는 바인딩만 담당하고, application 서비스가 그 값을 정책 VO(예: `CheckInPolicy{dailyCap, onboardingGraceHours}`)나 커맨드 인자로 변환해 애그리거트에 전달한다. domain에 숫자를 하드코딩하면 QA 결함이다.

## 명명 규칙 (사용자 확정)

- **한 글자 변수·파라미터명 금지** — 지역 변수, 메서드 파라미터, 람다 파라미터 모두. 타입이나 역할을 드러내는 이름을 쓴다: `Collection c` ✗ → `CollectionBook collectionBook` ✓, `RegionVisited e` ✗ → `RegionVisited event` ✓, `forEach(s -> …)` ✗ → `forEach(completion -> …)` ✓. 특히 `e`는 예외로 읽히므로 이벤트에 쓰지 않는다. 예외는 숫자 인덱스 루프의 `i`/`j`뿐이다.
- **JDK·Spring 타입과 같은 이름의 도메인 클래스 금지** (`Collection`, `List`, `Map`, `Optional`, `Event`, `Order` 등). 도메인 의미를 살려 구분한다 — 예: 도감은 `CollectionBook`. 설계 문서 용어와 다르게 지었으면 domain-model.md에 매핑을 적는다.
- **클래스명에 JDK 자료형 단어 금지** (`Set`, `List`, `Map`, `Collection`, `Array`, `Queue` 등이 **자료형으로 읽히는 경우** — 예: `SetCatalog`은 "Set들의 카탈로그"로 읽힘. `CollectionBook`(도감)처럼 도메인 합성어로 의미가 분명하면 허용. 도메인 용어 "지도"는 `ExpeditionMap`, `MapId`, `MapSettings`처럼 합성어로만 쓰고 단독 `Map` 클래스(설정 record 포함)는 금지): `SetCatalog`·`CollectionSet`·`SetProgress` ✗ → 도메인 용어로(세트=테마: `Theme`, `Themes`, `ThemeProgress`) ✓. 공개 이벤트·테이블·API처럼 이미 커밋된 외부 계약 이름은 바꾸지 않고 domain-model.md에 매핑을 적는다(예: `SetCompleted`·`set_progress` = Theme).
- **일급 컬렉션 변수·접근자 이름은 클래스명을 따른다**: `QuestRules quests` ✗ → `QuestRules questRules` ✓ (접근자도 `questRules()`). 원소 복수형은 `List`로 읽혀 일급 컬렉션임이 가려진다. 클래스명 자체가 복수 명사(`Visits`, `Members`, `Themes`)면 `visits`, `members`, `themes` 그대로.
- **일급 컬렉션은 `record`로 만들지 않는다**: `final class` + `private final` 컬렉션, 원본 컬렉션을 반환하는 게터 금지. 밖에는 행동 메서드(`find`, `containing`, `byScope`, `count…`)만 공개하고, 꼭 순회가 필요하면 불변 뷰(`List.copyOf`)나 `stream()`만. 호출부에서 `xxx.list().stream().filter(...)`가 보이면 그 로직을 일급 컬렉션 안으로 옮긴다.
- 이유: 코드만 보고 무엇인지 읽혀야 하고, import 충돌로 FQCN을 쓰게 되는 일을 막는다.

## domain 하위 패키지 구성 (사용자 확정)

- `domain/` 바로 아래에 클래스를 늘어놓지 않고 **애그리거트별 하위 패키지**로 나눈다. 폴더 하나 = 애그리거트 하나: 루트·엔티티·VO·일급 컬렉션·결과 record·**리포지토리 포트**를 그 애그리거트 폴더에 함께 둔다. 예: `progression/domain/{progress, collectionbook, quest, badge, replay}`, `exploration/domain/{territory, map, explorer}`, `catalog/domain/{region, item, reward, definition}`.
- 종류별(`vo/`, `collection/`, `repository/`) 분류는 하지 않는다 — 애그리거트 하나가 여러 폴더로 흩어진다.
- 여러 애그리거트가 함께 쓰는 정책 VO·도메인 서비스만 별도 폴더(`policy/`, `replay/` 등)로 뺀다. 애그리거트 폴더끼리는 내부 구현을 참조하지 않고 id(VO)로만 가리킨다.
- 폴더를 나누며 package-private 이던 것을 무분별하게 `public`으로 열지 않는다 — 꼭 필요한 것만.

## class vs record 기준 (사용자 확정)

| 종류 | 형태 | 예 |
|------|------|----|
| 애그리거트·엔티티 | **class** | `Territory`, `ExplorerProgress`, `CollectionBook` |
| 일급 컬렉션 | **class** (record 금지 — 원본 컬렉션이 노출됨) | `Visits`, `Members`, `Themes`, `QuestRules` |
| 다음 상태를 계산하는(`withX`/`record(month)`처럼 새 값을 돌려주는) 값 객체, 내부 표현을 숨기거나 팩토리를 강제해야 하는 값 객체 — 판정·조회 메서드(`isX`, `contains`)만 있으면 record 유지 | **class** (`equals`/`hashCode` 직접 구현) | `Streak`, `LevelCurve`, `ThemeProgress` |
| 값 하나를 감싸 검증만 하는 단순 값 객체 | record (compact constructor에서 검증) | `RegionCode`, `Memo`, `VisitDate`, `InviteCode` |
| 넘겨주는 값 묶음 — 결과·사실·정책·커맨드 | record | `CheckInResult`, `VisitFacts`, `CheckInPolicy`, `ProgressChange` |
| 이벤트·DTO·카탈로그 정의(읽기 전용 참조 데이터) | record | `RegionVisited`, `*Response`, `Region`, `ItemDefinition` |

- record에 컬렉션 필드가 있으면 compact constructor에서 `List.copyOf`/`Set.copyOf`/`Map.copyOf`로 방어 복사해 불변으로. 컬렉션을 감싸는 것 자체가 목적이면 record가 아니라 일급 컬렉션 class로.
- 판단이 애매하면: "이 타입에 상태를 바꾸거나 다음 값을 계산하는 메서드가 있는가?" → 있으면 class.

## 의존성 주입

- 스프링 빈은 **생성자 주입**만 쓴다(`@Autowired` 필드·세터 주입 금지). 순환 의존이 생기면 주입 방식이 아니라 설계를 고친다. 선택적 빈은 생성자 파라미터의 `ObjectProvider<T>`로.

## infra(JPA) 규칙 (사용자 확정)

- **엔티티 하나 = 파일 하나.** 여러 `@Entity`를 한 파일의 중첩 클래스로 묶지 않는다. Spring Data 리포지토리 인터페이스도 하나에 파일 하나(`XxxJpaRepository.java`). 가시성을 숨기고 싶으면 package-private 최상위 클래스로 둔다.
- 이름은 `{도메인개념}JpaEntity`(예: `ExplorerProgressJpaEntity`, `XpLedgerJpaEntity`), 복합키는 엔티티 안의 `Key` 또는 별도 `{이름}Key` 파일.
- **도메인 ↔ 엔티티 변환은 그 엔티티가 가진다**: `static XxxJpaEntity from(도메인)`/`void apply(도메인)`(갱신), `도메인 toDomain()`. 리포지토리 어댑터(`JpaXxxRepository implements 도메인 포트`)는 조회·저장 호출과 엔티티 조합만 하고 필드 단위 매핑 코드를 갖지 않는다. 여러 엔티티로 하나의 애그리거트를 복원해야 하면 루트 엔티티의 `toDomain(자식 엔티티들)`로.
- 이유: 테이블 하나를 볼 때 그 파일 하나만 열면 매핑까지 다 보이게.
- **infra 하위 패키지는 종류별 2개 (사용자 확정)**: `infra/entity/`(JPA 엔티티 + `CsvColumn` 같은 매핑 보조), `infra/repository/`(Spring Data 리포지토리 + 도메인 포트를 구현한 어댑터). 애그리거트 구분은 domain 폴더가 하므로 infra는 애그리거트별로 나누지 않는다. 패키지가 갈리면서 엔티티·변환 메서드·Spring Data 인터페이스가 `public`이 되는 것은 허용(infra 밖에서 참조하지 않는 것은 ArchUnit이 막는다). 예외: app-api의 outbox는 조립 모듈의 인프라+릴레이 프로세스 묶음이라 나누지 않는다(나누면 상태 전이 메서드를 public으로 열어야 함).
- **리포지토리 어댑터는 "어떻게 저장할지"만, "무엇을/왜"는 호출자가 (사용자 확정 — 애그리거트 단위 저장 유지)**: 포트는 애그리거트 단위(`save(aggregate)`, `replace(aggregate)` 등)로 두고 서비스가 테이블 구조를 모르게 한다. 어댑터에 허용되는 것은 저장 기술뿐 — 매핑 호출, 새 행만 insert/변경 행 update 같은 변경 반영, 삭제 후 재삽입. **금지**: 호출 의도를 추측하는 분기(`if (aggregate.rebuilt())` ✗ → 서비스가 `save`/`replace` 중 골라 호출), `Clock` 주입·시각 결정(시각은 도메인이 들고 온다), 비즈니스 판단. 재계산처럼 통째로 바꾸는 경로는 기존 행과 비교(diff)하지 말고 `replace`가 그 애그리거트 행을 지우고 다시 넣는다.

## 테스트 작성 규칙 (사용자 확정)

테스트는 **도메인 문서처럼 읽혀야 한다.** 테스트 보고서(트리)만 펼쳐 봐도 그 애그리거트의 규칙이 이야기처럼 보이게 쓴다.

- **이름은 `@DisplayName`에 도메인 문장으로.** 클래스는 개념("체크인", "탐험가 진행"), `@Nested`는 상황("같은 지역을 이미 칠했을 때", "가입 후 72시간이 지났을 때"), 테스트는 결과 문장("다시 칠할 수 없다", "하루 다섯 곳까지만 칠해진다"). 메서드 이름은 짧은 영문 식별자로 두고 의미는 DisplayName이 맡는다.
- **DisplayName에 넣지 않는 것**: QA·이슈 번호(P1-2, Q2, R2-1), 클래스·메서드·필드 이름, 구현 용어(outbox, upsert, refId, DTO, MockMvc, 트랜잭션, 락, 409·FAILED 같은 상태 코드, null). 사용자가 쓰는 말과 설계 문서의 유비쿼터스 언어(지역, 영토, 선점, 도감, 테마, 보호권…)만 쓴다. HTTP·동시성·인프라 테스트도 사용자 입장의 결과로 쓴다("같은 탐험가가 동시에 여러 곳을 칠해도 하루 다섯 곳까지만 칠해진다").
- **구조는 이야기 순서로**: 테스트 클래스는 domain 폴더(애그리거트)와 같은 단위·같은 패키지 구조. 안은 `@Nested`로 커맨드 → 상황 → 결과. 정상 흐름을 먼저, 거절·경계를 뒤에.
- **빠짐없이**: domain-model.md의 각 애그리거트 불변식·커맨드·이벤트마다 그것을 설명하는 테스트가 최소 하나 있어야 한다(애그리거트별 "규칙 ↔ 테스트" 대응이 비면 결함). 한 테스트는 한 규칙만 말한다.
- **준비 코드는 도메인 말로**: 테스트 데이터는 `Fixtures`/빌더(`탐험가()`, `방문(종로구).날짜(어제)` 같은 의미 단위)로 만들어 본문에 잡음이 없게. given/when/then 주석 대신 본문 순서로 드러낸다.

## 이벤트 통신 규칙

1. 컨텍스트 간 호출은 outbox 테이블 + 릴레이로만 한다(공개 Query 인터페이스 조회는 예외). 구독은 application 계층이 common `EventSubscriber` 빈으로 등록하는데, **소비 대상 애그리거트 하나당 구독자 하나**(예: `progression.progress`가 RegionVisited·VisitCancelled·SetCompleted·QuestCompleted 를 모두 받아 타입별로 나눠 처리)로 만든다 — 이벤트 타입마다 구독자를 따로 두면 같은 애그리거트로 가는 체크인·취소의 순서가 깨진다(QA P1-1). 릴레이(2단계 D5, `outbox_delivery`)는 구독자별로 전달·트랜잭션 분리하고, 순서 단위 (aggregateId, 구독자) 안에서 앞 이벤트가 DELIVERED 가 아니면 뒤 이벤트를 보내지 않는다(head-of-line). 낙관적 락 충돌은 상한 없이 지수 백오프+지터로 재시도, 그 밖의 실패는 5회 후 FAILED → 그 단위가 멈추고 재전달(`OutboxRedelivery`, local `POST /dev/outbox/redeliver`)로 푼다. 핸들러 트랜잭션은 애그리거트 로드·저장만 담아 짧게 둔다. Kafka는 지금 안 붙인다(릴레이는 `@Scheduled`).
2. 트랜잭션은 커맨드를 받은 애그리거트 하나만 잠근다. 체크인이면 Territory만 커밋하고, `RegionVisited`를 같은 트랜잭션의 outbox에 쌓는다. 나머지 애그리거트는 이벤트를 구독해 각자 자기 트랜잭션에서 갱신한다.
3. 이벤트에는 하류가 원본을 다시 읽지 않아도 되도록 계산된 값을 실어 보낸다(예: `RegionVisited`의 `isFirstInProvince`, `nth`, `isFirstClaim`).
4. **모든 핸들러는 멱등하다.** `xp_ledger.ref_id` 같은 유니크 키로 중복 적용을 막는다. refId 형식(2단계 D3·D4 반영):
   - 기본 XP 지급 `region:{explorerId}:{code}#{k}`, 회수 `region:{explorerId}:{code}#{k}:revoke`(음수) — k 는 세대(XpLedger 가 계산). 지역당 회수 안 된 지급은 최대 1개라 지급·회수 재전달이 이벤트 id 없이 no-op 이 된다.
   - 시·도 첫 발 `province:{explorerId}:{provinceCode}`(탐험가 단위, 판정은 explorer_region 기준, 취소해도 회수 없음).
   - 선점 보너스 `claim:{mapId}:{code}:{explorerId}`(수령자를 포함해야 선점 이전 시 새 선점자 지급이 UNIQUE에 막히지 않는다).
   - 세트 완성 `set:{explorerId}:{setId}`, 퀘스트 `quest:{explorerId}:{period}:{questId}`.
   - 카운터(+1/−1)는 그대로 쌓지 말고 집합으로 들고 있는다(예: explorer_region 의 활성 지도 id 집합, 퀘스트의 센 지역 키 집합).
   - 취소의 지도 단위/탐험가 단위 판단: `VisitCancelled.regionStillOnMap`(지도 단위 — 도감), explorer_region.active_map_count(탐험가 단위 — 기본 XP·지역 아이템)(D2).
5. 순서가 중요한 연쇄(세트 완성 → 보상)는 2차 이벤트(`SetCompleted`)로 잇는다.

## 일관성 3원칙

1. 핸들러 멱등성 (위 4번).
2. **취소의 비대칭성**: 체크인 취소는 지역 아이템과 기본 XP만 되돌린다. 세트 완성·뱃지·퀘스트 보상은 유지한다. 이유: 반복 획득은 이미 멱등성으로 막혀 있어 회수 로직의 복잡도를 들일 가치가 없다.
3. **재계산 가능성**: 진행·도감·인벤토리는 Territory로부터 전부 재계산할 수 있어야 한다(RecalculateService — 정의 변경·버그 복구용 배치).

## 구현 작업 절차

1. `references/domain-model.md`에서 담당 애그리거트 섹션과 모듈 의존 매트릭스를 읽는다.
2. domain부터 작성한다(순수 Java + 단위 테스트). 불변식은 애그리거트 메서드 안에서 지키고, 위반은 예외로 거부한다.
3. application(커맨드 서비스·이벤트 핸들러) → infra(JPA 엔티티·Flyway 마이그레이션) → api(컨트롤러·공개 이벤트) 순서로 올라간다.
4. 테이블은 domain-model.md의 스키마 요약을 따르고, Flyway 마이그레이션은 V1=1단계(카탈로그+탐험), V2=2단계(진행), V3·V3_1=3단계(공유 지도·꾸미기), V4·V4_1=4단계(계정·공유), V5=5단계(소셜)로 추가한다 — 4단계에 계정·공유 테이블이 생겨 소셜이 V5 로 밀렸다(4단계 리더 결정). 커밋된 마이그레이션은 고치지 않고 새 번호(V4_2 …)로만 바꾼다. 1단계에서 app-api에 `flyway-core`(MySQL 전환 시 `flyway-mysql`도) 의존성을 추가하고, local 프로파일도 Flyway + `ddl-auto: validate`로 전환한다(create-drop 유지 금지 — 마이그레이션이 로컬에서 검증되지 않는다).
5. `./gradlew build` 통과 확인. ArchUnit 포함.

## 참조 데이터

Region(250개)·ItemDefinition·CollectionSetDefinition·BadgeDefinition·QuestDefinition은 애그리거트가 아니다. catalog 모듈의 리소스 JSON으로 두고 시작 시 메모리에 올린다(단, ItemDefinition은 운영이 수시로 추가하므로 3단계(꾸미기)에서 DB 테이블로 옮긴다). Region에는 `countryCode`·`version`·`replacedBy`·`retiredAt`을 처음부터 넣고, RegionCode는 `KR-11010` 형식이다.

## 게임 규칙 값

하루 상한(5)·온보딩 예외(72h)·탈퇴 유예(7일)·카드 TTL(10분)은 코드에 박지 않는다. app-api의 `TerritoryProperties`(`territory.*`)에서 주입받는다.
