# 나의 영토 도메인 모델 요약

`doc/나의 영토 도메인 분석 · 애그리거트 설계.pdf`의 구현용 증류본. 의심스러우면 원본 PDF가 우선한다.

## 목차

1. [바운디드 컨텍스트와 모듈 의존 매트릭스](#1-바운디드-컨텍스트와-모듈-의존-매트릭스)
2. [애그리거트 9개 상세](#2-애그리거트-9개-상세)
3. [체크인 이벤트 흐름](#3-체크인-이벤트-흐름)
4. [테이블 스키마 요약](#4-테이블-스키마-요약)
5. [공유 지도·랭킹·XP 규칙](#5-공유-지도랭킹xp-규칙)
6. [단계별 진행 순서](#6-단계별-진행-순서)
7. [리스크 대응으로 확정된 규칙](#7-리스크-대응으로-확정된-규칙)

---

## 1. 바운디드 컨텍스트와 모듈 의존 매트릭스

컨텍스트 6개. 탐험(Exploration)이 진실 원천이고, 나머지는 이벤트를 구독하는 하류다. 카탈로그는 모두의 상류(Published Language).

| 컨텍스트 | 책임 | 주요 애그리거트 | 일관성 |
|----------|------|----------------|--------|
| catalog | 지역·희귀도·특산물·세트 정의, GeoJSON | (참조 데이터) | 정적, 배포 단위 변경 |
| exploration | 체크인·취소, 방문 기록, 영토, 공유 지도 | Territory, ExpeditionMap | **강한 일관성, 진실 원천** |
| progression | XP·레벨·칭호·스트릭·뱃지·퀘스트·도감 | ExplorerProgress, Collection, QuestBoard | 이벤트 기반 최종 일관성, 재계산 가능 |
| wardrobe | 아이템 보유, 착용, 장면 | Inventory, Scene | 최종 일관성 |
| social | 친구, 랭킹, 비교, 피드 | Friendship + 읽기 모델 | 최종 일관성, 배치·캐시 |
| sharing | 공개 프로필, 자랑 카드 | ShareCard | 스냅샷 캐시 |

모듈 의존(ArchUnit로 강제):

| 모듈 | 허용 | 금지 |
|------|------|------|
| common | 없음 | Spring(web·jpa), JPA |
| catalog | common | 다른 컨텍스트 |
| exploration | common, catalog | progression·wardrobe·social·sharing |
| progression | common, catalog:api.query, exploration:api.event·api.query | exploration의 domain/application/infra/api.web |
| wardrobe | common, catalog(api.query: RegionCatalog·ItemCatalog), exploration(api.event·api.query), progression(api.event·api.query: CollectionBookQuery) | social·sharing, 다른 컨텍스트 내부(ArchUnit `wardrobe_*` 규칙, 3단계) |
| social | common, exploration:api, progression:api | 다른 컨텍스트의 domain |
| sharing | common, catalog, 모든 컨텍스트의 api(Query) — 4단계: catalog·exploration·progression·wardrobe 의 api.event·api.query(진행 `ProgressQuery`, 꾸미기 `SceneQuery` 를 이때 추가) | 다른 컨텍스트의 domain·application·infra·api.web(ArchUnit 규칙 2), 아무도 sharing 을 참조하지 않는다 |
| app-api | 전부 | 도메인 로직 작성 금지 |

**공개 범위(2단계 D7)**: 각 컨텍스트 api 는 `api.event`(공개 이벤트) · `api.query`(공개 Query 인터페이스·그 DTO) · `api.web`(컨트롤러·웹 DTO)로 나눈다. 다른 컨텍스트는 `api.event`·`api.query`만 참조할 수 있고(`api.web`·domain·application·infra 금지), `api.event`·`api.query`는 자기 domain·application·infra·api.web 을 참조하지 않는다(ArchUnit). 여러 컨텍스트 컨트롤러가 쓰는 `@CurrentExplorer` 애노테이션은 common(`common.identity`), 그 resolver 는 app-api 에 둔다. 3단계(결정 2)부터 인증은 비밀 접근 토큰 헤더 `X-Explorer-Token`(발급 시 `POST /explorers` 응답에 한 번만, DB 엔 SHA-256 해시) — explorerId 는 공개 식별자라 인증에 쓰지 않는다. resolver 는 exploration `api.query.ExplorerCredentials`(토큰 → explorerId)를 쓴다. 4단계부터 **인증 공존**: 로그인 세션(구글 OIDC, 세션에는 계정 신원만)의 계정 → 그 탐험가(`api.query.AccountCredentials`)가 먼저, 없으면 토큰. 세션 요청의 변경 메서드는 CSRF(쿠키 XSRF-TOKEN → 헤더 X-XSRF-TOKEN), 토큰·관리자 헤더 요청과 세션 없는 요청은 제외(app-api `SecurityConfig`). 공개 정보는 `api.query.ExplorerProfileQuery`(handle ↔ explorerId, 계정 연결 여부).

common에 두는 것: ExplorerId, RegionCode, Rarity, DomainEvent, Outbox(EventOutbox·EventSubscriber), CurrentExplorer, Clock. Spring 의존 없음(이벤트 퍼블리셔 인터페이스용 spring-context만 예외). Rarity는 공개 이벤트(RegionVisited 등)에 실리는 Published Language 값이고 exploration.domain이 catalog를 참조할 수 없어 공유 커널에 둔다.

## 2. 애그리거트 9개 상세

### 2-1. Territory (탐험 · 핵심)

- 루트 `Territory(mapId)` — **키는 explorerId가 아니라 mapId**(공유 지도 단위). 내부에 Visit 엔티티 컬렉션. Visit은 (지도, 지역, 멤버) 단위 한 행이고 `checkedInBy`, `verification(NONE|GPS, 지금은 항상 NONE)`을 가진다.
- VO: `RegionCode`, `VisitDate`, `Memo(≤40자)`, `PhotoRef`
- 불변식:
  - 같은 (지역, 멤버)는 한 번만 방문 상태(중복 불가). 취소는 방문 상태에서만.
  - 방문일 과거 허용, 미래 거부.
  - 하루 상한: 지도별·멤버별. 상한값의 진실 원천은 해당 지도의 `MapSettings.dailyCheckInCap`이고, 그 기본값이 `territory.check-in.daily-cap`(5)이다. 지도 가입 후 72시간은 온보딩 예외로 미적용.
  - 지도 설정이 `photoRequired`면 사진 없는 체크인 거부.
  - 지도장은 특정 방문에 `disputed` 플래그 가능 — 지도 내 랭킹 집계에서만 제외(개인 영토·전체 랭킹 영향 없음).
- 커맨드: `checkIn(region, date, memo)`, `editVisit(...)`, `cancelVisit(region)`
- 이벤트: `RegionVisited{explorerId, mapId, regionCode, rarity, provinceCode, visitedAt, isFirstInProvince, nth, isFirstClaim}`, `VisitEdited`, `VisitCancelled{explorerId, mapId, regionCode, rarity, provinceCode, wasClaim, remaining, regionStillOnMap, cancelledAt}`
  - `regionStillOnMap`(2단계 D2): 취소 후에도 그 지역이 지도에 다른 멤버의 방문으로 남는가 — Collection(지도 단위) 회수 판단용.
  - 3단계 확장(끝에 추가, 하위 호환): `RegionVisited.visitGeneration`·`VisitCancelled.visitGeneration` = 같은 (지도, 지역, 멤버)의 체크인 회차(결정 6, 1부터, 방문 행을 지워도 `visit_generation` 테이블로 단조 증가, 0 = 예전 이벤트), `RegionVisited.memberIds` = 체크인 시점 지도 멤버(테마 완성 수령자, 결정 1). 진행(ExplorerProgress)은 `explorer_region_mark`(지도별 마지막 회차, 음수 = 취소)로 오래된 회차 이벤트를 무시한다. 회차 0(예전 이벤트)은 그 지도에 표시가 있으면 무시(Q3).
  - 3단계 이벤트: `ClaimTransferred{mapId, regionCode, rarity, provinceCode, fromExplorerId, explorerId(새 선점자), reason CANCELLED|LEFT, transferredAt}`(선점자 취소·탈퇴 → 다음 체크인 멤버, 진행 +10), `VisitsHidden`·`VisitsRestored`(탈퇴 숨김·재가입 복구 — 도감이 구독), `VisitDisputeChanged`(지도장 이의). 확정 시그니처는 `_workspace/03_contracts.md`.
  - `isFirstInProvince`·`nth`·`isFirstClaim`은 Territory가 자기 상태로 계산해 실어 보낸다(하류가 영토를 다시 읽지 않게).
  - `visitedAt`은 처리 시각(서버 시계). `visitDate`는 기록용 표시 값일 뿐 진행에 영향 없음. `VisitEdited`는 진행이 구독하지 않는다.
- `CheckInPreview` 도메인 서비스: 순수 함수(현재 영토 + 지역 → 보상 목록). 체크인 모달의 "획득 XP +90"을 계산한다.
  - 보상 계산 공유(2단계 D1): 순수 보상 함수 본체는 `catalog.domain.RewardRules.checkIn(rarity, firstInProvince, firstClaim)`이고 `catalog.api.query.RewardCalculator`로 공개한다. 탐험은 도메인 포트(`CheckInRewards`)를 application 어댑터가 이 함수에 위임하고, 진행도 같은 함수(`XpRewards` 포트)로 실제 지급을 계산한다. 입력은 사실 값뿐(영토를 모른다).
  - 미리보기는 "이 지도 기준 최대 보상"이다(응답 `xp.basis=MAP_MAX`, `xp.note`) — 기본 XP·시·도·선점 보너스가 탐험가/지도 단위 1회라 실제 지급은 적을 수 있다.

### 2-2. ExplorerProgress (진행)

- 루트 `ExplorerProgress(explorerId)`. XP 장부, 레벨, 선택 칭호, 스트릭, 뱃지 목록.
- VO: `XpLedgerEntry{source, amount, refId, at}`, `Level`, `Title`, `Streak{months, lastMonth}`, `BadgeId`
- 불변식: XP = 장부 합계(감소는 음수 항목으로만). 레벨은 결정적 함수 `floor((1+√(1+xp/5))/2)`(정수형: 레벨 L 하한 = 4·d·L·(L−1), d=5는 카탈로그 levels.json). 뱃지·칭호는 추가만(회수 없음). 선택 칭호는 획득한 것 중에서만.
- 커맨드: `applyVisit(RegionVisited)`, `revokeVisit(VisitCancelled)`, `applySetCompleted(SetCompleted)`, `applyQuestReward(QuestCompleted)`, `selectTitle`(뱃지·칭호는 각 커맨드 끝에서 판정해 추가)
- 이벤트: `LevelUp`, `BadgeEarned`(공개, outbox). `XpGained`·`StreakChanged`는 2단계에선 구독자가 없어 발행하지 않는다.
- 방문 취소 시 장부의 `refId`로 해당 XP만 정확히 되돌린다. 스트릭은 여기(달을 넘나드는 연속 값이라 QuestBoard가 아님). XP·레벨·스트릭은 본인 체크인만 집계.
- 탐험가 단위 지역(2단계 D2): `explorer_region(explorer_id, region_code, first_visited_at, active_map_count)`을 ExplorerProgress 가 유지한다(일급 컬렉션 `ExploredRegions`). 체크인 +1, 취소 −1, 0이 되면 기본 XP 회수. 탈퇴(MemberLeft, 3단계)는 카운트를 줄이지 않는다(전체 랭킹 유지). 멱등을 위해 카운트 대신 활성 지도 id 집합(`active_map_ids`)을 함께 저장한다.
- 시·도 첫 방문 보너스(D3): 탐험가 단위, refId `province:{explorerId}:{provinceCode}`. 판정은 이벤트의 isFirstInProvince(지도 기준)가 아니라 explorer_region 기준. 취소 시 회수하지 않는다.
- 기본 XP 세대 refId(D4): 지급 `region:{e}:{code}#{k}`, 회수 `region:{e}:{code}#{k}:revoke`(음수). k 는 `XpLedger`(일급 컬렉션)가 계산. 불변식: 지역당 "회수 안 된 지급"은 최대 1개 → 지급 재전달은 활성 지급이 있으면 no-op, 회수 재전달은 활성 지급이 없으면 no-op(이벤트 id 없이 멱등).
- 칭호: 레벨(lv{n}) · 세트 완성(set-{id}) · 상시 도전 보상(long-{id}) · 시·도 100%(own-{시·도 코드}). `title_earned`에 기록.

### 2-3. Collection (도감 진행)

- **코드 이름 매핑**: 설계 용어 Collection(도감) ↔ 코드 `CollectionBook`(JDK `java.util.Collection`과 겹치지 않게 — 명명 규칙). 리포지토리 `CollectionBookRepository`, 서비스 `CollectionBookService`, 구독자 `progression.collection-book`. 테이블 `set_progress`, API `/collection`, 이벤트 이름(`SetCompleted`)과 outbox aggregate 이름("Collection")은 그대로.
- **세트 ↔ 테마 매핑**: 설계 용어 세트(CollectionSet·SetProgress·세트 카탈로그) ↔ 코드 `Theme`·`ThemeProgress`·`Themes`(일급 컬렉션), 완성 결과 `ThemeCompletion`, 카탈로그 정의 `ThemeDefinition`, JPA `ThemeProgressJpaEntity` — 클래스명에 JDK 자료형 단어(Set)를 쓰지 않는 명명 규칙. **유지하는 외부 계약 이름**: 공개 이벤트 `SetCompleted`(필드 `setId` = 테마 id), 테이블 `set_progress`·컬럼 `set_id`, API 응답 `sets`·`SetResponse`·`setId`, 카탈로그 공개 Query `ProgressionRules.sets()`·`SetView`·`RewardCalculator.setComplete()`, 정의 JSON `sets.json`, 장부 refId 접두사 `set:`, `XpSource.SET_COMPLETE`, 칭호 출처 `SET`·id `set-{id}`, 뱃지 조건 `SETS_COMPLETED`, 퀘스트 지표 `SET_REGIONS`.
- 루트 `Collection(mapId)` — **지도 단위**. 세트별 진행·완성 여부.
- VO: `SetProgress{setId, collected:Set<RegionCode>, completedAt?}`
- 불변식: 완성은 정의된 지역 전부 모였을 때 단 한 번. 완성 후 지역 취소해도 완성 기록 유지(보상 회수 없음).
- 커맨드: `applyVisit(…, members)`, `revokeVisit(regionStillOnMap)` — 지도에 그 지역이 다른 멤버 방문으로 남아 있으면 진행에서도 빼지 않는다(D2), `revokeRegions`(탈퇴로 지도에서 사라진 지역)·`restoreRegions`(재가입 복구) / 이벤트: `SetCompleted{mapId, setId, explorerId(수령자), completedAt, completedBy, recipientIds}` — 3단계 결정 1: 완성 시점 지도 멤버 **전원**이 수령자이고 **수령자마다 한 건**씩 발행 → 진행(보너스 XP `set:{explorerId}:{setId}`·칭호)·꾸미기(세트 배경)가 explorerId 한 명만 처리. 수령자 목록은 set_progress `completed_member_ids` 에 남기고, 재계산 복구 규칙은 이 목록 기준으로만 지급(R2-1). 나중 합류 멤버는 세트 배경만(꾸미기가 MemberJoined + `progression.api.query.CollectionBookQuery`), XP·칭호 없음. `SetProgressed`는 구독자가 없어 발행하지 않는다.
- ExplorerProgress와 분리한 이유: 세트 정의 추가 시 진행 루트가 커지는 것을 막고 재계산 배치를 독립 실행.

### 2-4. QuestBoard (월간 퀘스트 · 상시 도전)

- 루트 `QuestBoard(explorerId, yearMonth)`. 달이 바뀌면 새 보드. 상시 도전은 yearMonth 센티넬 `'ALL'` 보드 하나.
- VO: `QuestProgress{questId, current, target, claimedAt?}`
- 불변식: 보상은 달성 후 한 번만. 지난 달 보드는 불변. 월간 퀘스트는 본인 체크인 기준, 소급 없음(체크인 시점 기준).
- 커맨드: `applyVisit`, `claim(questId)` / 이벤트: `QuestCompleted{explorerId, period, questId, xp, claimedAt}` → 진행이 구독(refId `quest:{e}:{period}:{questId}`, 상시 도전은 칭호도).
- 진행 집계는 센 지역 키("시·도|지역") 집합(`QuestTally`)이라 같은 지역은 한 번만 센다(재전달·취소 후 재체크인 멱등). 지표: NEW_REGIONS·NON_COMMON_REGIONS·FIRST_IN_PROVINCE·SET_REGIONS·LEGEND_REGIONS·PROVINCES_WITH_MIN_REGIONS. 지난 달 보드는 처리 시각 기준으로 닫힌다.
- FIRST_IN_PROVINCE(mprov, "처음 가는 시·도")는 **explorer_region 기준**(탐험가 단위, QA P3-3): 체크인 처리 시각 이전에 그 시·도에서 처음 밟은 시각이 있는 지역(취소한 지역 포함)이 하나도 없으면 첫 방문. 지도 기준 이벤트 값(isFirstInProvince)은 쓰지 않는다 — 공유 지도에서도 탐험가 단위로 맞는다.

### 2-5. Inventory (보유 아이템)

- 루트 `Inventory(explorerId)`.
- VO: `OwnedItem{itemId, grantKind, source(REGION|SET_REWARD|EVENT — grantKind 에서 정해짐), acquiredAt, favorite, basis}` — itemId는 지역 아이템 `region:{code}`, 세트 보상 `set:{setId}`, 운영 추가 `event:…` 등. `basis` = 그 아이템을 지금 뒷받침하는 활성 방문 `VisitKey(region, mapId)` 집합(체크인 아이템만, 보상은 빈 집합). 일급 컬렉션 `OwnedItems`.
- 방문 흔적 `VisitTrace{region, mapId, generation, active}`(일급 컬렉션 `VisitTraces`): (지역, 지도)마다 마지막 반영 세대 — 옛 세대 이벤트 무시(체크인 k 는 k > 기록이거나 같은 세대가 아직 활성일 때만, 취소 k 는 k ≥ 기록일 때만, 세대 0 = 예전 이벤트는 그 (지도, 지역)에 회차 기록(세대 ≥ 1)이 있으면 무시, 없으면 반영 — 리더 결정 Q3). progression `explorer_region` 과 같은 원리를 꾸미기가 자기 데이터로 둔다(공개 Query 대신 — 구독 순서가 서로 독립이라).
- 불변식: 같은 itemId 한 번만. **체크인으로 받은 아이템(지역 특산물 REGION + 기간·시·도 이슈 EVENT)은 근거 방문이 모두 취소되면 회수**(같은 조건을 만족한 다른 활성 방문 — 다른 지도 포함 — 이 남으면 유지, 3단계 리더 결정 Q2: "체크인 → 즉시 취소"로 이슈 아이템을 얻는 경로 차단). 세트 보상·수동 지급은 회수 없음. **탈퇴는 회수 사유가 아니다**(방문 "취소"만 — explorer_region 이 탈퇴로 줄지 않는 것과 같다).
- 커맨드: `applyCheckIn(CheckInGrant)`, `applyCancel(mapId, region, generation)`, `grantRewards(items)`, `markFavorite` / 결과 `InventoryChange` → 이벤트: `ItemGranted{explorerId, itemId, source, acquiredAt}`, `ItemRevoked{explorerId, itemId, revokedAt}`(공개, aggregate Inventory/explorerId) → Scene이 구독.
- 지역·이슈 아이템은 체크인한 본인에게(지급 판정 = 카탈로그 `ItemCatalog.grantedByCheckIn`, 날짜는 **처리 시각** 기준 — 소급 방지). 세트 보상은 `SetCompleted`(수령자별 1건) 때 **그 수령자 Inventory 하나만**(기간 판정은 완성 시각), 완성 직후 합류해 수령자 목록에 없는 지금 멤버에게는 완성자 몫의 SetCompleted 처리에서 `ThemeRewardOwed{mapId, setId, explorerId, completedAt}`(공개, aggregate Inventory/explorerId)를 내 각자 트랜잭션에서 지급(QA P3-1·P3-R2-5, 판단 `ThemeRewardRecipients`), `MemberJoined` 시 `CollectionBookQuery.completedSets`(완성 시각 포함)로 이미 완성된 세트 보상. **소급 지급 없음**(리더 결정 Q-R2-1): 이슈 아이템은 체크인 처리 시각이 유효 기간 안이고 정의 생성 시각(item_definition.created_at) 이후일 때만, 테마 보상 기간은 완성 시각 기준 — 재계산도 같은 기준.
- 재계산(일관성 원칙 3, QA P2-1): `InventoryReplay` — 지금 멤버인 지도의 활성 흔적·근거만 비우고(탈퇴한 지도의 근거·취소된 흔적·보상은 유지, 근거가 하나도 없는 방문형 아이템은 정리 — P3-R2-2) 그 지도들의 본인 방문을 처리 시각 순으로 재생 + 그 지도들의 완성 테마 보상, 예전 아이템의 acquiredAt·favorite 유지. 서비스 `InventoryRecalculateService`(루트 선잠금, replace, 루트 잠금 뒤 같은 트랜잭션에서 `wardrobe.*` 구독자 미전달 이벤트가 있으면 보류 — P3-R2-3, 탐험가별 실패 격리). 장면은 가방에 없게 된 착용만 벗긴다(`Scene.keepOnly`). 진입점: `POST /dev/recalculate`(진행 다음, 응답 `wardrobe` 필드), 운영 `recalculate-on-startup`(진행 다음).
- 아이템 생김새(슬롯·룩·색·이름)는 카탈로그의 ItemDefinition 참조 — 여기 없음. 꾸미기 쪽 사양은 `item/ItemSpec{itemId, slot, tier, grantKind}`.
- 구독자: `wardrobe.inventory`(RegionVisited·VisitCancelled·SetCompleted·MemberJoined·MapCreated(루트 행 선생성)·ThemeRewardOwed·InviteRewardOwed·ExplorerMerged(4단계 — 익명 탐험가의 재생 불가 아이템·초대 기록을 계정으로 이전)). dev 시드·지우기는 루트 행(inventory·scene)을 지우지 않는다(Q-R2-2).

### 2-6. Scene (장면 · 꾸미기)

- 루트 `Scene(explorerId)`. 성별, 슬롯별 착용, 장식 3칸.
- VO: `Gender(M|F)`, `EquipSlot(HAT|HAND|BADGE|BAG|PET|BG)`, `PropSlots(≤3, 순서 = 배치)`, `EquippedSlots`, 착용 검증용 `Holdings`(Inventory 보유 id — 폴더 간 내부 참조 대신 id 만), `StylePolicy`(희귀도별 점수, 설정 `territory.wardrobe.style-points.*`).
- 불변식: 사용자가 입히는 아이템은 Inventory에 있어야 한다. 한 슬롯에 하나, 장식 ≤3(`PropSlots.MAX`, 설계 불변식), 아이템 슬롯과 장착 슬롯 일치(PROP 은 장식 칸).
- 커맨드: `edit(SceneEdit{gender, unequip, equip, props})`(PUT /scene), `autoEquip(item)`(빈 슬롯이거나 **더 높은** 희귀도 — 같으면 유지, PROP 은 빈 칸이 있을 때), `takeOff(itemId)`(ItemRevoked), `keepOnly(holdings)`(재계산 정리).
- 꾸미기 점수 `stylePoints` = 착용(슬롯 + 장식) 희귀도 점수 합(서버 계산, 기본 1·3·8).
- 이벤트: `SceneChanged{explorerId, changedAt}` → 공유가 구독(카드 스냅샷 무효화, 4단계 — 3단계는 발행만). 바뀐 경우에만.
- 구독자: `wardrobe.scene`(ItemGranted·ItemRevoked — aggregate Inventory/explorerId 한 줄이라 획득·회수 순서 유지). 사용자 편집과 자동 착용 경합은 scene.version 낙관적 락(사용자 409, 릴레이는 재시도).
- Inventory와 분리한 이유: 보유는 탐험 파생, 착용은 사용자 선택 — 변경 빈도·주체가 다름.

### 2-7. Friendship (소셜)

- 루트 `Friendship(fromExplorerId, toExplorerId)` — 팔로우 관계 한 건이 애그리거트 하나.
- 불변식: 자기 팔로우 불가, 중복 불가. 커맨드: `follow`, `unfollow`
- 랭킹·VS 비교·피드는 애그리거트가 아니라 **읽기 모델**: `RegionVisited`·`SetCompleted`·`LevelUp`을 구독해 `ActivityFeedEntry`·`LeaderboardRow`를 쌓는 프로젝터.

### 2-8. ShareCard (공유)

- 루트 `ShareCard(explorerId, mapId, kind, version)`. 종류: 영토·최근 여행·리캡·VS.
- 불변식: 참조한 영토·장면 버전 기록, 버전 바뀌면 재생성.
- 커맨드: `render()`, `invalidate()`. 무효화만 하고 렌더링은 공유 링크가 실제 열릴 때(lazy), 최소 TTL 10분.
- 4단계 구현(파트 B, QA P2-1·P2-2 반영): domain 폴더 `sharing/domain/{card, privacy, showcase}`.
  - `card`: `ShareCard`(render = `markRendered(CardBasis, imageKey, at)`, 다시 그릴지 `needsRender(current, now, CardCachePolicy)` — 그린 적 없음 → 그림, **handle 이 바뀜(익명 → 계정, 변경) → TTL 면제하고 바로 그림**, 요약이 바뀜 + 최소 TTL 지남 → 그림, 기준 그대로면 TTL 무관하게 안 그림), `ShareCardId(explorerId, mapId=개인 지도, kind)`(VS 는 저장 안 함 — 메모리 LRU), `CardBasis(summaryHash, handle)`, `CardImageStorage` 포트(이미지 키에 기준 해시 — 같은 기준의 이미지만 기록).
  - **기준 = 실제로 그리는 공개 요약(Showcase + 종류 + 연도)의 SHA-256**(리더 결정). "invalidate()"는 별도 커맨드 없이, 렌더 요청 때 지금 요약 해시가 마지막 렌더 해시와 다르면 낡음. 이벤트를 구독하지 않는다 — 재계산·병합·칭호 변경처럼 이벤트 없이 바뀐 값도 놓치지 않는다(초안의 이벤트 시각 기반 CardSource·`sharing.card-source` 구독자는 QA P2-1 로 제거).
  - `privacy`: `PrivacySettings(explorerId, PUBLIC|FRIENDS|PRIVATE)` — **기본 PRIVATE**(사용자 결정 Q1, 프로필 탭 "공개하기"), FRIENDS 는 5단계 전까지 PRIVATE 처럼, 공개 아니면 공개 경로 404(존재 숨김). 공개 Query `sharing.api.query.ProfileVisibilityQuery` — app-api 가 탐험의 `ProfileJoinGate` 포트로 이어 프로필 비공개면 프로필 합류도 404.
  - `showcase`(도메인 서비스·값): 공개 정보 `Showcase`(색칠·집계·월 단위 `VisitMonth` — 메모·사진·정확한 날짜 없음, `summaryHash`), `PublicVisits`(일급 컬렉션: 정복률·시·도 정복·전설·리캡·VS), `CardComposer`(Showcase → `CardContent` 4종), `CardRenderer` 포트(infra Java2D, OFL 글꼴 번들).
- API: `GET /u/{handle}`(HTML + OG), `/u/{handle}/card/{territory|recent|recap}.png`, `/u/{handle}/vs/{other}.png`(둘 다 공개), `GET /me/cards`, `/me/cards/{kind}.png`, `GET·PUT /me/privacy`. 프로필 링크 합류는 exploration `POST /maps/join-via-profile/{handle}` {mapId} — 지도장 + 공개 범위 PUBLIC 공유 지도만(`ExpeditionMap.openToProfileOf`), 초대코드 노출 없음.
- 초대 보상(§7, 4단계): `MemberJoined.invitedBy`(초대코드 = 지도장, 프로필 = 프로필 주인). 꾸미기 `wardrobe.inventory` 가 초대받은 쪽 Inventory 에서 판단(`Invitations` — 처음 합류·재가입 아님·셀프 아님·같은 쌍 1회, invite_reward)하고 받은 뒤, 초대한 쪽은 `InviteRewardOwed`(aggregate Inventory/inviterId)로 각자 트랜잭션에서. 아이템은 카탈로그 `INVITATION` 규칙(HOST·GUEST, 한정 = 유효 기간), EVENT 출처·회수 없음·재계산 유지.

### 2-9. ExpeditionMap (공유 지도)

- **소속: exploration 컨텍스트** (Territory와 생명주기가 묶여 있고 별도 모듈이 없다. 단, 같은 트랜잭션에서 둘을 수정하지는 않는다 — 체크인은 멤버 여부만 읽기 참조).
- 루트 `ExpeditionMap(mapId)`. 이름, 초대코드, 생성자, 멤버 목록, 국가 코드. 가입 시 개인 지도 자동 생성(멤버 1명). 지도는 여러 개 가질 수 있고 합류 시 개인 지도는 병합하지 않는다.
- VO: `InviteCode(8자, 재발급 가능)`, `Member{explorerId, role(OWNER|MEMBER), joinedAt}`, `CountryCode`, `MapSettings{photoRequired, dailyCheckInCap(기본 = territory.check-in.daily-cap, 지도장은 1..기본값으로 낮추기만 — Q1), visibility}`(개인 지도는 설정 변경 불가). 사진 필수는 새 체크인에만 적용(기존 방문의 메모·날짜 수정 허용, 있던 사진 삭제만 거부 — Q5). 탈퇴 유예 중인 사람은 자리를 차지하지 않아 그사이 4명이 차면 재가입은 MAP_FULL(의도 — 탈퇴 경고에 안내)
- 불변식: 멤버 ≤4. 초대코드는 지도당 하나·전체 유일. OWNER 한 명, 탈퇴 불가(양도 후 가능). 중복 가입 불가.
- 커맨드: `create(owner, name, country)`, `join(inviteCode, explorerId)`, `leave`, `transferOwner`, `regenerateInviteCode`
- 이벤트: `MapCreated` → 탐험이 빈 Territory·Collection 생성. `MemberJoined` → 인벤토리가 완성된 세트 보상을 새 멤버에게 지급. `MemberLeft` → 탈퇴 유예 처리.
- 탈퇴는 7일 소프트 삭제: 즉시 해당 멤버 방문에 `hidden_at`, 선점은 다음 체크인 멤버에게 이전(`ClaimTransferred`). 7일 내 재가입 시 방문 복구(선점은 안 돌아옴), 7일 후 배치가 하드 삭제.
- Territory와 분리한 이유: 멤버 가입·탈퇴가 방문 250건과 같은 락을 잡을 이유가 없다. 체크인은 멤버 여부만 읽기 참조로 확인.
- 생성 시 예외: Explorer와 개인 ExpeditionMap(+territory 행)은 한 트랜잭션에서 함께 생성한다(모두 신규 행이라 잠금 경합이 없고, 개인 지도 없는 탐험가를 막기 위한 원자성이 필요). 공유 지도 생성(ExpeditionMap + 빈 territory 행)도 같은 이유로 함께. "커맨드 하나가 애그리거트 둘을 수정" 금지 규칙의 유일한 예외.
- 3단계 구현: 탈퇴 유예 중인 멤버는 `Departure`(map_member.left_at, 일급 컬렉션 `Departures`). 지도 커맨드(join·leave·purge)는 Territory 를 같은 트랜잭션에서 고치지 않고 `MemberLeft`·`MemberJoined(rejoined)`·`MemberPurged` 를 내며, 탐험 자신의 구독자 `exploration.territory` 가 territory 를 잠그고 숨김(hidden_at)·선점 이전·복구·하드 삭제를 한다. 재가입은 원래 가입 시각으로 복귀(온보딩 예외 재사용 방지), 복구된 방문의 선점 순서(`claim_rank_at`)는 복구 시각이라 넘어간 선점은 돌아오지 않는다. 유예 종료 배치 `MapPurgeJob`(territory.map.purge-interval-ms). 지도장만: 설정·초대코드 재발급·지도장 넘기기·방문 이의. 개인 지도는 합류·탈퇴·양도 불가. mapId 지정 요청은 멤버 확인을 잠금보다 먼저(N2, READ_COMMITTED 라 안전).

## 3. 체크인 이벤트 흐름

```
[동기·한 트랜잭션] Territory.checkIn → 저장 → RegionVisited를 outbox에 → 커밋
[비동기·최종 일관성]
  RegionVisited → Inventory(지역 아이템 지급) → ItemGranted → Scene(빈 슬롯이면 자동 착용)
               → Collection(세트 진행·완성 판정) → SetCompleted → Inventory(세트 배경 전원 지급)
                                                               → ExplorerProgress(보너스 XP)
               → QuestBoard(퀘스트 진행) → QuestCompleted → ExplorerProgress
               → ExplorerProgress(기본 XP·스트릭) → LevelUp
               → Social 읽기 모델(피드·랭킹 갱신)
  SceneChanged·RegionVisited → ShareCard 무효화
```

체크인 응답에는 동기로 계산한 것만 담는다: 영토 반영, 받을 아이템, 예상 XP(CheckInPreview).

## 4. 테이블 스키마 요약

| 테이블 | 애그리거트 | 키 | 비고 |
|--------|-----------|-----|------|
| expedition_map | ExpeditionMap | id PK | name, country, invite_code UNIQUE, owner_id FK, version(V3 — 지도 커맨드는 행을 FOR UPDATE + 강제 증가로 잠근다, QA P1-1) |
| map_member | ExpeditionMap | PK(map_id, explorer_id) | role, joined_at, left_at(V3 — 탈퇴 유예 중, 멤버 수에 안 셈), ≤4 |
| explorer | (계정 루트) | id PK | handle UNIQUE(4단계 — 계정 탐험가만, 소문자), access_token_hash UNIQUE(V3, SHA-256 — 계정 연결·병합되면 NULL), created_at, status(ACTIVE\|MERGED)·merged_into·merged_at(V4). 가입 시 개인 지도 자동 생성 |
| account | Explorer(자식, 1:1) | explorer_id PK/FK | provider·subject UNIQUE(구글 OIDC sub), email, created_at — V4. 연결 후 불변 |
| handle_reservation | Explorer(자식) | PK(explorer_id, handle) | reserved_until — V4. 바꾸기 전 handle 을 territory.account.handle-reservation-days(기본 30일) 동안 다른 탐험가가 못 가져간다(본인은 되돌릴 수 있음, QA P3-10) |
| recalculation_request | (app-api 공통 인프라) | explorer_id PK | reason, requested_at, generation(다시 예약될 때 +1 — 배치는 읽은 값 그대로일 때만 삭제), attempts — V4. 병합처럼 이벤트 없이 영토가 바뀐 탐험가의 재계산 예약(배치가 보류 규칙 만족 시 처리 후 삭제). FK 없음 |
| visit | Territory | UQ(map_id, region_code, checked_in_by) | map_id FK, checked_in_by FK, verification, visit_date, memo, photo_url, generation, claim_rank_at, hidden_at, disputed(V3). 취소는 물리 삭제 + 이벤트 |
| visit_generation | Territory | PK(map_id, region_code, explorer_id) | last_generation — 방문을 지워도 남는 체크인 회차(결정 6, V3) |
| territory | Territory | map_id PK/FK | created_at. Territory 루트 행 — 체크인·수정·취소를 지도 단위로 직렬화하는 잠금 대상(SELECT … FOR UPDATE). 지도 생성 시 함께 생성 |
| explorer_progress | ExplorerProgress | explorer_id PK/FK | xp, level, title_id, streak_months, streak_last_month, version(낙관적 락) |
| xp_ledger | ExplorerProgress | id | source, amount, ref_id UNIQUE(멱등), created_at |
| badge_earned | ExplorerProgress | PK(explorer_id, badge_id) | earned_at |
| title_earned | ExplorerProgress | PK(explorer_id, title_id) | earned_at — 칭호도 추가만(2단계 추가) |
| set_progress | Collection | PK(map_id, set_id) | collected_codes JSON, completed_at NULL, completed_member_ids(V3 — 완성 시점 멤버 = 수령자), version — **지도 단위** |
| quest_progress | QuestBoard | PK(explorer_id, quest_period, quest_id) | current_count, tally(센 지역 키 집합), claimed_at, version. 상시 도전은 quest_period='ALL'(year_month 는 MySQL 예약어라 이름 변경) |
| inventory | Inventory | explorer_id PK/FK | version, updated_at — 루트 행(자식만 바뀌어도 version 강제 증가, 재계산·이벤트 처리의 잠금 대상). 3단계 V3_1 추가 |
| owned_item | Inventory | PK(explorer_id, item_id) | source(REGION\|SET_REWARD\|EVENT), grant_kind(방문형 여부 — 재계산 정리), acquired_at, favorite. §4 초안의 map_id 는 두지 않음(회수 판단은 근거 행) |
| owned_item_basis | Inventory | PK(explorer_id, item_id, region_code, map_id) | 체크인 아이템의 근거 활성 방문 — 모두 사라지면 회수(Q2). 3단계 V3_1 추가 |
| inventory_visit | Inventory | PK(explorer_id, region_code, map_id) | generation, active — 방문 흔적(세대 무시 규칙). 행은 지우지 않고 active=false. 3단계 V3_1 추가 |
| scene | Scene | explorer_id PK/FK | gender, slot_hat·slot_hand·slot_badge·slot_bag·slot_pet·slot_bg(초안의 slots JSON 대신 슬롯별 컬럼), props(쉼표 구분 ≤3, 순서 유지), version, updated_at |
| friendship | Friendship | PK(from_id, to_id) | created_at |
| feed_entry | 읽기 모델 | id PK | actor_id, map_id, kind, payload JSON, IDX(actor_id, created_at) |
| share_card | ShareCard | PK(explorer_id, map_id, kind) | summary_hash·rendered_handle(초안의 scene_ver·visit_ver 대체 — 공개 요약 해시 + 찍힌 handle), image_key(초안 image_url, 기준 해시 포함), rendered_at, version — 4단계 V4_1. VS 는 저장하지 않음 |
| privacy_settings | PrivacySettings(공유) | explorer_id PK | visibility(PUBLIC·FRIENDS·PRIVATE, 행 없으면 PRIVATE), updated_at, version — 4단계 V4_1 |
| invite_reward | Inventory(꾸미기) | PK(invitee_id, inviter_id) | map_id, rewarded_at — 같은 쌍 1회, 지우지 않음. 4단계 V4_1 |
| explorer_region | ExplorerProgress(읽기 모델 겸용) | (explorer_id, region_code) | province_code, rarity, first_visited_at, active_map_count, active_map_ids + 자식 explorer_region_mark(explorer_id, region_code, map_id, mark — V3, 지도별 마지막 회차) — 전체 랭킹·상위%·도감 뱃지 + 기본 XP 회수 판단(D2) |
| region_stats / rank_percentile | 집계 | — | 일 1회 배치 → Redis |
| outbox | 공통 | id PK | aggregate, event_type, payload JSON, published_at. FK 없음 |
| outbox_delivery | 공통 | PK(event_id, subscriber) | status(PENDING·DELIVERED·FAILED), attempts, conflicts, first_conflict_at(V3), last_error, delivered_at — 구독자별 전달 기록(2단계 D5) |
| item_definition | 참조(운영 추가) | item_id PK | name, emoji, slot, tier, theme, look·color_primary·color_secondary(룩), grant_rule·grant_ref(초안의 JSON 대신 두 컬럼), valid_from/to(DATE, 양 끝 포함), created_at(이슈 아이템 소급 판정 — Q-R2-1) — 3단계 V3_1 로 DB화(지역 250 + 세트 배경 9 이관, `tools/catalog/gen-item-sql.js`). 소유 = catalog(`ItemCatalog` Query, 30초 캐시 — 무효화는 커밋 뒤 세대 증가, 읽는 동안 세대가 바뀐 결과는 캐시하지 않음, 다른 인스턴스 추가분은 최대 30초 지연 허용 — P3-R2-6). `region:`·`set:` id 는 이관 전용(운영 추가 금지 — dev reset 이 운영 추가분만 지운다) |

- 애그리거트 경계를 넘는 FK는 두지 않는다(예: scene.slots → owned_item 금지).
- gender는 Scene이 바꾸는 값이므로 scene 테이블에 둔다(explorer 아님).
- collected_codes·slots·props는 조회 조건이 아니므로 JSON 컬럼. 필요해지면 읽기 모델로 펼친다.
- explorer_progress와 scene은 1:1이지만 변경 주체(이벤트 vs 사용자)와 낙관적 락 범위를 분리하려고 떨어뜨렸다.

## 5. 공유 지도·랭킹·XP 규칙

- 공유 지도는 협력이 아니라 **경쟁**. 같은 지역을 멤버가 각자 체크인할 수 있고, 지역 색은 선점자(최초 체크인 멤버) 색.
- **랭킹 두 층, 다른 집계**: 지도 안 랭킹은 visit을 (map_id, checked_in_by)로 센다. 전체·친구 랭킹은 탐험가별 **중복 제거한 지역 수**(explorer_region) — 여러 지도에서 같은 지역을 찍어도 1. 탈퇴로 지도 visit이 삭제돼도 explorer_region은 남아 전체 랭킹은 줄지 않는다.
- XP: 기본 XP는 탐험가당 지역당 활성 한 번(`ref_id = region:{explorerId}:{code}#{k}`, 회수 `…#{k}:revoke` — D4 세대 규칙), 시·도 첫 발 +15는 탐험가당 시·도당 한 번(`province:{explorerId}:{provinceCode}`, 회수 없음 — D3), 선점 보너스 +10은 지도마다·수령자마다(`claim:{mapId}:{code}:{explorerId}` — 수령자를 포함해야 선점 이전 시 새 선점자의 ref_id가 달라진다), 세트 완성 +100은 탐험가당 세트당(`set:{explorerId}:{setId}`), 퀘스트는 `quest:{explorerId}:{period}:{questId}`. 선점 이전 시 새 선점자에게도 +10(`ClaimTransferred`). 떠난 사람 보너스는 회수 안 함.
- outbox 릴레이(D5, QA P1-1·P1-2 수정): 구독자 = 소비 애그리거트 하나(목록은 아래 "구독자 목록"). 구독자별로 전달·트랜잭션 분리(REQUIRES_NEW), `outbox_delivery`에 기록. 순서 단위 (aggregateId, 구독자) 안에서 앞 이벤트가 DELIVERED 가 아니면 뒤 이벤트를 보내지 않는다(head-of-line, 건너뛰지 않음). 낙관적 락 충돌은 상한에 세지 않고 지수 백오프+지터(`territory.outbox.relay.backoff.*`)로 재시도, 그 밖의 실패는 백오프 재시도 후 5회에 FAILED — 그 단위는 재전달(`OutboxRedelivery.redeliverFailed`, local `POST /dev/outbox/redeliver`)까지 멈추고 다른 단위는 계속 진행. 다중 인스턴스(SKIP LOCKED)는 하지 않음.
- 구독자 목록(3단계 기준, id 는 outbox_delivery.subscriber 키 — 바꾸지 않는다):
  - `progression.progress`(ExplorerProgress): RegionVisited · VisitCancelled · SetCompleted(수령자별) · QuestCompleted · ClaimTransferred(새 선점자 +10) · MapCreated(개인 지도 → 진행 루트 선생성, S3-1)
  - `progression.collection-book`(CollectionBook): RegionVisited · VisitCancelled · VisitsHidden · VisitsRestored
  - `progression.quest-board`(QuestBoard): RegionVisited
  - `exploration.territory`(Territory): MemberLeft(숨김·선점 이전) · MemberJoined(rejoined → 복구) · MemberPurged(하드 삭제)
  - `exploration.territory`(4단계 추가): ExplorerMerged(계정 탐험가 개인 지도로 방문 흡수 → VisitsMerged + 재계산 예약) · VisitsMerged(병합된 익명 탐험가 개인 지도 정리) · MemberReassigned(공유 지도 방문·선점 재귀속 + 재계산 예약)
  - `exploration.expedition-map`(ExpeditionMap, 4단계): MembershipHandover(탐험 내부 이벤트 — 병합 때 공유 지도 자리 넘기기 → MemberPurged?·MemberJoined?·MemberReassigned. **MemberLeft 는 내지 않는다**)
  - `wardrobe.inventory`(Inventory): RegionVisited · VisitCancelled · SetCompleted · MemberJoined · MapCreated(루트 행 선생성) · ThemeRewardOwed(완성 직후 합류자 세트 보상, 3단계 QA r2)
  - `wardrobe.scene`(Scene): ItemGranted · ItemRevoked
  - (4단계) `wardrobe.inventory` 에 InviteRewardOwed(MemberJoined 에서 초대 보상 판단)·ExplorerMerged(재생 불가 아이템·초대 기록 이전) 추가. 공유(sharing)는 구독자가 없다(카드 기준 = 요약 해시).
- 재계산 보류 기준(S3-3, QA P3-5·P3-6, 4단계 P3-R3-1·P3-R3-2): 진행 재계산은 그 탐험가·지도의 이벤트 중 **`progression.*`·`exploration.territory`·`exploration.expedition-map` 구독자에게 아직 DELIVERED 가 아닌 것**이 있으면 보류하고, 판정은 진행 루트를 잠근 뒤 같은 트랜잭션에서 한다. 꾸미기(인벤토리) 재계산도 같은 방식으로 **`wardrobe.*`·`exploration.territory`·`exploration.expedition-map`** 구독자의 미전달(`EventBacklog.hasUndelivered(ids, prefixes)`)이면 보류한다(인벤토리 루트 잠금 뒤 판정). `exploration.territory` 를 넣는 이유: 재가입 복구(VisitsRestored)·탈퇴 숨김·병합 흡수가 아직 영토에 반영되지 않았을 때 재계산이 그 지도를 덜 읽어 아이템·기본 XP 를 회수하고, 두 구독자는 그 결과 이벤트를 구독하지 않아 다음 재계산 전까지 복구되지 않는다. 재계산은 취소된 지역의 회차 표시(진행 explorer_region_mark·꾸미기 흔적)를 다시 만들지 못하고 손상된 표시를 고치지 못한다 — 보류 규칙으로 늦게 올 예전 이벤트가 없다는 전제에서 수용(QA P3-R2-4).
- 체크인 잠금 순서(3단계 QA P1-2): 체크인·수정·취소·이의는 지도 행 공유 잠금(FOR SHARE) → territory 배타 잠금. 지도 커맨드(합류·탈퇴·양도·설정·초대코드·유예 종료)는 지도 행 배타 잠금만 잡는다(territory 는 구독자가 별도 트랜잭션). 그래서 탈퇴와 동시 체크인이 직렬화되고(탈퇴 커밋 뒤 체크인은 403, 그 전 체크인은 탈퇴 시각보다 앞섬) 순환 잠금이 없다.
- 재계산 복구 규칙(Q2 승인, 3단계 R2-1 수정): 도감에 완성 기록이 있고 **그 탐험가가 완성 시점 멤버(completed_member_ids)인데** 장부에 그 세트 보너스(`set:{e}:{setId}`)가 없으면 지급, 보상 받은(claimed) 퀘스트인데 장부에 퀘스트 XP(`quest:…`)가 없으면 지급(지난 달 보드 포함). 칭호·뱃지는 함께 보정. refId 가 같아 멱등. 재계산은 지금 멤버인 지도만 다시 만들고, 탈퇴한 지도로 남은 explorer_region 활성과 그 기본 XP 는 그대로 둔다(탈퇴는 줄이지 않음).
- 재계산 운영(3단계 S3-2·S3-3): 탐험가(와 그가 속한 지도)에 아직 전달되지 않은 outbox 이벤트(FAILED 포함)가 있으면 그 탐험가는 건너뛰고 보고서의 `deferred` 에 넣는다(릴레이보다 앞선 영토를 읽어 "잠깐 있었던 완성"을 놓치지 않게). `recalculate-on-startup` 은 릴레이가 outbox 를 비울 때까지(territory.progression.recalculate-wait, 기본 5분) 기다린 뒤 돈다. **재계산은 트래픽이 적은 시간에 돌린다 — 실행 중(진행 루트를 잠그는 동안) 사용자의 칭호 선택(PUT /progress/title)은 409 로 실패할 수 있다.**
- outbox 충돌 시간 상한(3단계 결정 5): 낙관적 락 충돌 재시도는 횟수 상한에 세지 않지만 첫 충돌부터 `territory.outbox.relay.conflict-retry-limit`(기본 10분)을 넘기면 FAILED → 재전달 경로. 릴레이는 페이지(100행)의 전달 기록을 한 번에 읽고, FAILED 수를 최대 1분에 한 번 WARN 으로 알린다(R2-2).
- 친구 랭킹은 요청 시점 조인 계산(친구 수 적음). 시·도별/전국 정복률은 Territory 로드 후 메모리 계산(250건). 지역별 방문자 비율·상위 N%는 일 1회 배치 → Redis.

## 6. 단계별 진행 순서

| 단계 | 범위 | 산출물 | 목표일 |
|------|------|--------|--------|
| 0 | 뼈대 | 멀티모듈 스캐폴드 (scaffold-multimodule 스킬) | — |
| 1 | 카탈로그 + 탐험 | Flyway V1(explorer, **expedition_map, map_member**(개인 지도 자동 생성에 필요한 최소), **territory**(Territory 루트·잠금 행), visit, outbox) + app-api에 flyway 의존성 추가·local도 Flyway 전환, `POST /visits`, `DELETE /visits/{code}`, `GET /territory`, CheckInPreview, 지역 250개 JSON·GeoJSON | 2026-10-13 |
| 2 | 진행 | V2(explorer_progress, xp_ledger, badge_earned, title_earned, explorer_region, set_progress, quest_progress, outbox_delivery), 이벤트 핸들러 3개(진행·도감·퀘스트), 재계산 배치, XP 공식·뱃지 12·세트 9·퀘스트 정의, api 패키지 분리(api.event·api.query·api.web) | 2026-10-27 |
| 3 | 꾸미기 | V3__shared_map(explorer 토큰 해시, map_member.left_at, visit 회차·숨김·이의, visit_generation, explorer_region_mark, expedition_map.version, set_progress.completed_member_ids, outbox_delivery.first_conflict_at) + V3_1__wardrobe(owned_item, scene, item_definition), `PUT /scene`, 자동 착용 핸들러, 아이템 정의 DB화 + `POST /admin/items`, 지도 설정·초대·탈퇴 유예 전체 기능(MapSettings, disputed, hidden_at) | 2026-11-10 |
| 4 | 공유 + 구글 로그인 | V4__account(explorer.status·merged_into, account, recalculation_request — 파트 A) + V4_1__sharing(share_card·privacy 등 — 파트 B), 구글 OIDC 로그인(클라이언트 ID 없으면 비활성)·세션·CSRF, handle, claimExplorer 병합, 공개 프로필 `/u/{handle}`, OG 카드 렌더러(lazy), 스냅샷 저장·무효화, 초대 보상 | 2026-11-24 |
| 5 | 소셜 | V5(friendship, feed_entry), 피드·랭킹 프로젝터, VS 비교 쿼리, 상위 % 배치 | 2026-12-08 |

Flyway 규칙(QA P3-R2-10): 커밋된 마이그레이션은 고치지 않는다 — 커밋 이후의 스키마 변경은 새 버전 번호(V3_2, V4 …)로만 한다(3단계 V3·V3_1 은 커밋 전이라 그 자리에서 수정했다).

3단계 데이터 주의(Q4): 3단계 이전에 만든 탐험가는 접근 토큰 해시가 없어 인증할 수 없고 진행 루트 선생성(S3-1)도 되어 있지 않다. 운영 데이터가 없으므로 backfill 하지 않는다 — 로컬은 `DELETE /dev/reset` 으로 정리하고 다시 발급한다.

Flyway 번호 갱신(4단계): 4단계는 마이그레이션이 생겨 V4(파트 A)·V4_1(파트 B)을 쓰고, 원래 V4 였던 5단계 소셜은 **V5** 로 민다. V3·V3_1 은 3단계 커밋(1cfca7c) 이후라 고치지 않는다(P3-R3-7).

4단계 계정·병합(claimExplorer, 사용자 확정 "기존 계정으로 병합"):
- Explorer 애그리거트에 계정(`Account{AccountIdentity(provider, subject, email), linkedAt}`, account 테이블 1:1)과 상태(ACTIVE·MERGED)를 둔다. 로그인 규칙은 도메인 `LoginPlan` — 계정에 탐험가 B 가 있고 지금 기기의 활성 익명 A 가 있으면 MERGE(A→B), A 가 없거나 병합할 수 없으면 SIGN_IN, 계정이 처음이면 LINK(A 연결) 또는 CREATE(새 탐험가). 연결·병합하면 익명 토큰은 무효(세션으로만).
- handle: `^[a-z0-9][a-z0-9_-]{2,19}$`, 금칙어 최소(admin·api·dev·u·me·login 등). 최초 로그인 때 **이메일과 무관한 랜덤** `explorer-xxxx`(소문자·숫자 4자, 헷갈리는 l·o·0·1 제외, 겹치면 다시 뽑고 5번 겹치면 6자 — 사용자 결정 Q1: 이메일 로컬파트가 공개 주소가 되지 않게). 사용자가 `PUT /me/handle` 로 바꾼다. 바꾸기 전 handle 은 기본 30일 예약(handle_reservation — 공유된 옛 링크 탈취 방지), 다른 탐험가의 사용·예약 중이면 409 HANDLE_TAKEN(동시 변경 UNIQUE 위반도 같은 코드로 번역).
- 병합 연쇄(한 트랜잭션 한 애그리거트, 모두 멱등): 로그인 tx(Explorer A — 탐험가 행 배타 잠금 → MERGED) → outbox `ExplorerMerged{from, into, fromPersonalMapId, intoPersonalMapId}`(aggregate Explorer/into) + A 가 멤버인 공유 지도마다 `MembershipHandover`(탐험 내부) → `exploration.territory` 가 B 개인 territory 를 잠그고 A 개인 지도 방문 흡수(`Territory.absorb` — 같은 지역은 방문일이 더 이른 쪽, 메모·사진은 남는 쪽 것, 옮긴 방문은 B 의 다음 회차) → `VisitsMerged` + 재계산 예약 → A 개인 지도 정리(`releaseMember`). 공유 지도는 `ExpeditionMap.handOver` — B 가 멤버가 아니면 B 가 A 의 자리(역할·가입 시각)를 잇고(유예 중 탈퇴 기록이 있으면 재가입), B 가 이미 멤버면 A 만 빠진다(지도장이었으면 B 가 지도장). A 의 탈퇴 유예 기록은 만들지 않는다.
- 공유 지도 방문 재귀속(사용자 결정 Q2): handOver 는 `MemberLeft` 대신 `MemberReassigned{mapId, from, into}`(api.event)를 낸다 — MemberLeft 를 내면 기존 구독자가 A 방문을 숨기고 선점을 **다른 멤버**에게 넘겨 버린다. `exploration.territory` 가 그 지도 territory 를 잠그고 `Territory.reassignMember(A, B)`: A 방문을 선점 순서(claim_rank_at)·처리 시각·방문일·메모·사진·숨김·이의를 그대로 둔 채 B 것으로 바꾼다(회차만 B 의 다음 회차). 그래서 다른 멤버의 선점·순위는 바뀌지 않는다. **충돌 규칙: B 도 같은 지도·같은 지역 방문이 있으면 선점 순서가 이른 쪽을 남긴다**(개인 지도 흡수의 "이른 방문일" 규칙과 다르다 — 지도 안 선점 위치를 지키는 것이 기준), 메모·사진은 남는 쪽 것. 선점 보너스 refId 가 수령자를 포함(`claim:{map}:{code}:{B}`)하므로 B 재계산을 예약하고, 보류 판정에 `exploration.expedition-map` 구독자를 넣는다. 하루 상한은 체크인 입력 규칙이라 재귀속에는 적용하지 않는다. 지도에 칠해진 지역 집합이 그대로라 도감(지도 단위)은 바뀌지 않는다. 멱등: A 방문이 남아 있지 않으면 no-op.
- 재계산: 병합으로 영토가 이벤트(RegionVisited) 없이 바뀌므로 `RecalculationRequests`(common 포트, app-api recalculation_request)에 예약하고, app-api `RecalculationRequestJob` 이 보류 규칙을 만족할 때 진행 → 인벤토리 순으로 재계산한 뒤 예약을 지운다(그사이 다시 예약되면 남긴다). 구독자 안에서 바로 재계산하지 않는 이유: 처리 중인 이벤트 자체가 "미전달"이라 늘 보류된다.
- 병합 중 경합: 체크인·수정·취소·이의는 territory 잠금 뒤, 지도 커맨드는 지도 잠금 뒤 탐험가 행을 **공유 잠금**(FOR SHARE, refresh)으로 다시 읽어 활성인지 본다. 병합(탐험가 행 배타 잠금)과 직렬화돼 병합 전에 잠근 쓰기는 병합이 기다렸다 읽고(옮겨짐·숨겨짐), 병합 뒤의 쓰기는 404 로 거절된다. 잠금 순서 지도 S → territory X → 탐험가 S, 병합은 탐험가 X 하나라 순환 없음. 같은 계정 두 기기 동시 첫 로그인은 account UNIQUE 에 한쪽이 걸리고 재시도에서 병합된다.
- 병합된 탐험가(A)의 진행·인벤토리 행은 남겨 두되(복구용) 재계산 전체 대상(`TerritoryQuery.explorerIds` = 활성만)에서 빠진다.

Kafka는 5단계까지 불필요.

## 7. 리스크 대응으로 확정된 규칙

- 소급 금지는 스트릭·월간 퀘스트에만. 영토·아이템·세트·리캡은 과거 날짜 인정(초기 유입 = "예전에 간 곳 채우기").
- 치팅 대응: 사진 필수 옵션, 하루 상한 5, disputed 플래그, 전체 랭킹은 참고용(상위 % 표시)으로 약하게.
- 콘텐츠: 계절 한정 세트, 재방문 도장(같은 지역 2회차부터 카운트), 세계 확장은 시즌 2.
- 아이템 희석 방지: 일반 아이템은 키링 슬롯(3개)으로, 진열은 희귀·전설·세트·이슈 중심, favorite 플래그. 합성·강화 없음.
- 프라이버시: 메모·사진 기본 비공개, 공개 프로필은 색칠·집계만, 날짜는 월 단위 반올림, 공개 범위 설정(전체·친구·비공개).
- 행정구역 개편: Region에 version·replacedBy·retiredAt. 폐지 코드는 숨김 처리, 세트는 대체 코드로 재평가.
- 상호·상표 금지: 아이템명은 일반명사화("대전 튀김소보로").
- 이슈 아이템 지급 규칙은 3종만(기간 내 체크인 / 특정 시·도 / 세트 완성). 규칙 엔진 안 만든다. 3단계 구현: grantRule 5종 = `REGION_VISIT`(지역 특산물, 기본) · `PERIOD_CHECK_IN`(기간 필수, 처리 시각 날짜 기준) · `PROVINCE_CHECK_IN` · `THEME_COMPLETE` · `MANUAL`(자동 지급 없음 — 수동 지급 API 는 4단계 역할 기반 인가와 함께 이월). 체크인 이슈 아이템은 근거 방문이 모두 취소되면 회수(Q2), 정의 생성 전 체크인에는 소급 지급 없음(Q-R2-1). `POST /admin/items` 는 `X-Admin-Token`(territory.admin.token, prod 환경변수) — 4단계 로그인 후 역할 기반으로 대체.
