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
| analytics | (10단계) 분석 이벤트 수집·지표 — **관찰자**(게임 규칙을 바꾸지 않는다) | ExplorerJourney(여정) + 원본 이벤트·집계 | 최종 일관성, 일 배치 + 오늘 실시간 |
| notification | (12단계) 웹 푸시 알림 — 구독(기기)·종류별 설정·알림 3종 계획·발송(게임 규칙을 바꾸지 않는다) | PushRecipient(받는 사람), PushDelivery(발송 기록) | 탐험가 단위 잠금(구독·계획), 발송은 비동기(발송기) |

모듈 의존(ArchUnit로 강제):

| 모듈 | 허용 | 금지 |
|------|------|------|
| common | 없음 | Spring(web·jpa), JPA |
| catalog | common (8단계: application 포트 `RegionVisitorCounts` ← app-api `RegionVisitorCountsAdapter` ← social `api.query.RegionStatsQuery` — 미스터리 지역의 "덜 알려진 곳" 판단, 카탈로그는 여전히 아무 컨텍스트도 참조하지 않는다) | 다른 컨텍스트 |
| exploration | common, catalog | progression·wardrobe·social·sharing |
| progression | common, catalog:api.query, exploration:api.event·api.query | exploration의 domain/application/infra/api.web |
| wardrobe | common, catalog(api.query: RegionCatalog·ItemCatalog), exploration(api.event·api.query), progression(api.event·api.query: CollectionBookQuery) | social·sharing, 다른 컨텍스트 내부(ArchUnit `wardrobe_*` 규칙, 3단계) |
| social | common, exploration(api.event·api.query), progression(api.event·api.query: ExplorerRegionQuery·ProgressQuery) — 5단계 | catalog·wardrobe·sharing(api 포함), 다른 컨텍스트 내부(ArchUnit `social_*`). **sharing 과는 서로 참조하지 않는다**(리더 결정 — 공유의 FRIENDS 판정이 소셜 맞팔을, 소셜 피드·비교가 공유 공개 범위를 물어 순환): 소셜 `application.ProfileAudience` ← app-api `ProfileAudienceAdapter` ← sharing `api.query.ProfileVisibilityQuery`, 공유 `application.FriendDirectory` ← app-api `FriendDirectoryAdapter` ← social `api.query.FriendshipQuery`(ArchUnit `sharing_does_not_reference_social`) |
| sharing | common, catalog, 모든 컨텍스트의 api(Query) — 4단계: catalog·exploration·progression·wardrobe 의 api.event·api.query(진행 `ProgressQuery`, 꾸미기 `SceneQuery` 를 이때 추가) | 다른 컨텍스트의 domain·application·infra·api.web(ArchUnit 규칙 2), 아무도 sharing 을 참조하지 않는다 |
| notification | common, catalog(api.query: MysteryRegionQuery·ProgressionRules), progression(api.query: StreakQuery), exploration(api.event: ExplorerMerged) — 12단계 | wardrobe·social·sharing·analytics, 다른 컨텍스트의 내부(ArchUnit `notificationKnowsOnlyContracts` 허용 목록). **어떤 게임 컨텍스트도 notification 을 참조하지 않는다**(`nobodyKnowsNotification`, 분석만 api.event 관찰) |
| analytics | common, exploration(api.event), progression(api.event), notification(api.event: PushSent — 12단계) — 10단계. 공개 카드 열람은 요청 필터(`/u/**`)로 받아 sharing 을 참조하지 않는다 | catalog·wardrobe·social·sharing, 다른 컨텍스트의 api.query·내부. **아무 컨텍스트도 analytics 를 참조하지 않는다**(ArchUnit `nobodyKnowsAnalytics`·`analyticsKnowsOnlyPublishedFacts`). analytics.api 는 web 만(공개 이벤트·Query 없음) |
| app-api | 전부 | 도메인 로직 작성 금지 |

**공개 범위(2단계 D7)**: 각 컨텍스트 api 는 `api.event`(공개 이벤트) · `api.query`(공개 Query 인터페이스·그 DTO) · `api.web`(컨트롤러·웹 DTO)로 나눈다. 다른 컨텍스트는 `api.event`·`api.query`만 참조할 수 있고(`api.web`·domain·application·infra 금지), `api.event`·`api.query`는 자기 domain·application·infra·api.web 을 참조하지 않는다(ArchUnit). 여러 컨텍스트 컨트롤러가 쓰는 `@CurrentExplorer` 애노테이션은 common(`common.identity`), 그 resolver 는 app-api 에 둔다. 3단계(결정 2)부터 인증은 비밀 접근 토큰 헤더 `X-Explorer-Token`(발급 시 `POST /explorers` 응답에 한 번만, DB 엔 SHA-256 해시) — explorerId 는 공개 식별자라 인증에 쓰지 않는다. resolver 는 exploration `api.query.ExplorerCredentials`(토큰 → explorerId)를 쓴다. 4단계부터 **인증 공존**: 로그인 세션(구글 OIDC, 세션에는 계정 신원만)의 계정 → 그 탐험가(`api.query.AccountCredentials`)가 먼저, 없으면 토큰. 세션 요청의 변경 메서드는 CSRF(쿠키 XSRF-TOKEN → 헤더 X-XSRF-TOKEN), 토큰·관리자 헤더 요청과 세션 없는 요청은 제외(app-api `SecurityConfig`). 공개 정보는 `api.query.ExplorerProfileQuery`(handle ↔ explorerId, 계정 연결 여부, 5단계 `mergedInto` — 병합돼 비활성인 탐험가의 계정 탐험가). 5단계: `@CurrentExplorer(required = false)` 는 인증이 없거나 풀리지 않으면 null(공개 경로의 선택적 방문자 식별). common 에 VS 집합 비교 `model.TerritoryComparison` 추가(공유 VS 카드·소셜 비교 공용).

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
  - 8단계: 고른 지역이 처리 시각이 속한 주의 미스터리 지역이면 `MYSTERY_BONUS` 줄(+50, 라벨 "이번 주 미스터리 보너스")을 더한다 — 같은 보상 함수(`RewardCalculator.checkIn(…, mysteryOfWeek)`), 미스터리 지역은 카탈로그 `MysteryRegionQuery.weekOf(처리 시각)`(application `CatalogRegionDirectory.mysteryRegionAt`), 판정(지역 일치·이미 칠한 곳 제외)은 `CheckInPreview.preview(…, Optional<RegionCode> mysteryRegion, …)`. 주마다 1회라 이번 주에 이미 받았으면 실제 지급은 없다(최대 보상).

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
- 칭호: 레벨(lv{n}) · 세트 완성(set-{id}) · 상시 도전 보상(long-{id}) · 시·도 100%(own-{시·도 코드}) · 연속 탐험 마일스톤(streak-{개월}, 8단계 — 출처 STREAK). `title_earned`에 기록.
- **8단계 게임 요소 1순위**(리더 브리프 `_workspace/08_brief.md`, 값은 카탈로그 `streak-rules.json`·`reward-rules.json`·`mystery.json`·`badges.json`):
  - **보호권**(`StreakFreezes` 일급 컬렉션 — 장부 `streak_freeze`, `StreakFreezeEntry{refId, reason MONTHLY_QUESTS|MILESTONE|USED, amount, month, at}`): 보유 수 = 합계. 받기: 한 달 월간 퀘스트(정책 `monthlyQuestIds`)를 모두 보상 받으면 1(`freeze:{e}:quests:{yyyy-MM}`, `applyQuestReward`·복구 규칙에서 판정), 마일스톤 도달 시 1(`freeze:{e}:milestone:{n}`). 보유 상한 2(`StreakRules.freezeMaxHeld`) — 넘치면 0으로 기록(받을 일이 있었음 → 재전달·자리가 난 뒤에도 다시 주지 않음). 쓰기: `Streak.record(month, held)` → `StreakStep{streak, freezesUsed}` — 빈 달 수 ≤ 보유면 그만큼 써서 +1(빈 달은 개월 수에 더하지 않음), 모자라면 1부터 다시(쓰지 않음), `freeze:{e}:use:{yyyy-MM}`(그 달 첫 체크인 1회). 화면 연속 = `Streak.asOf(current, held)`(이번 달에 칠하면 메울 수 있으면 유지), 쓸 개수 = `emptyMonthsBefore(current)`.
  - **연속 탐험 마일스톤**(`StreakRules.milestones` 3·6·12·24개월, XP 50·100·200·400, 보호권 1): 처음 닿을 때 한 번 — XP refId `milestone:{e}:{n}`(XpSource.STREAK_MILESTONE, 장부 항목이 "받았음" 기록이라 끊겼다 다시 쌓아도 재지급 없음), 칭호 STREAK(장부에 그 refId 가 있으면), 보호권, 결과 `ProgressChange.milestonesReached` → 공개 이벤트 `StreakMilestoneReached{explorerId, months, xp, reachedAt}`.
  - **이번 주 미스터리 보너스**: 체크인 사실 `ProgressVisit.mystery = MysteryFact{weekId(월요일 ISO), region}`(application 이 카탈로그 기록으로 채움 — 기록 없는 지난 주는 null = 소급 없음), 그 지역이면 보상 줄 MYSTERY_BONUS +50, refId `mystery:{e}:{weekStart}`(주마다 1회, 취소해도 회수 없음 — 체크인 취소 비대칭), 결과 `mysteryFound` → `MysteryBonusEarned{explorerId, weekStart, regionCode, xp, earnedAt}`. 뱃지 조건 `MYSTERY_FOUND`(받은 주 수 — mystery1·5·10 "미스터리 탐험가/추적자/마스터").
  - **시·도 정복**: 정책 `ProvinceRoster`(시·도 → **현행** 지역 집합, 폐지 지역 제외)로 `settle` 마다 탐험가 단위 활성 지역이 그 시·도 현행 지역을 모두 덮으면 XP +300 한 번(`conquest:{e}:{provinceCode}`, XpSource.PROVINCE_CONQUEST, 취소해도 회수 없음·다시 100%여도 재지급 없음) → `ProvinceConquered{explorerId, provinceCode, xp, conqueredAt}`. 기존 `own-{p}` 칭호·PROVINCES_COMPLETE 뱃지도 같은 명부 기준(`BadgeFacts.conqueredProvinces`)으로 바꿈. 현황 `provinceCoverage(policy)` → `ProvinceCoverage{covered, total, complete(지금 100%), conqueredAt(왕관 — 회수 없음)}`.
  - **재계산(결정성)**: `rebuildBase` 는 보호권 장부를 비우고(소모가 스트릭에 달려 있어서) 미스터리·정복·마일스톤 XP 는 남긴다(회수 없는 보상). `ProgressionReplay` 는 장부에 남은 보상에서 `freezeGrantsOnRecord(policy)`(마일스톤 XP 항목 시각·월간 퀘스트를 모두 받은 달의 마지막 보상 시각)를 만들어 체크인(소모)과 처리 시각 순으로 섞어 재생(같은 시각이면 체크인 → 테마 완성 → 보호권 받기) — 같은 입력이면 몇 번 돌려도 같은 보유 수, 이벤트 누적과 같은 장부. 복구 규칙: 재생에서 지금 100%인 시·도는 정복 기록이 없으면 지급(refId 멱등), 재생에서 새로 닿은 마일스톤도 지급. 재계산은 이벤트를 내지 않으므로 꾸미기는 자기 재계산에서 `ProgressQuery.achievementsOf` 로 한정 아이템을 복구한다.
    - **수용한 차이(8단계 QA Q3·P3-5)**: 재계산은 남은 방문 기준으로 보호권 사용을 다시 계산하므로, 취소로 필요 없어진 보호권은 재계산 때 돌아온다(실시간 누적은 취소 때 되돌리지 않는다 — 스트릭 자체도 같은 성질).
    - **수용한 차이(8단계 QA P3-4)**: 재계산의 월간 퀘스트 보호권 판정은 **지금 카탈로그**의 월간 퀘스트 id 집합으로 한다 — 운영이 월간 퀘스트 정의를 바꾸면 지난 달의 보호권 받을 일이 재계산에서 달라질 수 있다(정의를 바꿀 때 재계산 영향 확인).
  - 이번 달 보호권 진행(QA P3-2): `monthlyFreezeProgress(policy, month)` → `MonthlyFreezeProgress{period, questsRewarded, questsRequired, reward, earned(이번 달 몫을 받을 일이 생김 — 상한이면 0개여도 true), granted(실제로 늘어난 수)}` — 화면이 "1개"·"채웠어요"를 판정하지 않게 `/progress.streakFreeze.thisMonth` 로 낸다. 다음 마일스톤 선택·마일스톤 칭호 id 도 도메인(`nextMilestone`, `MilestoneStatus.titleId` ← `TitleRules.forStreak`).
  - 공개 Query 추가: `ProgressQuery.achievementsOf(explorerId)` → `AchievementsView{milestones[{months, reachedAt}], conquests[{provinceCode, conqueredAt}]}`(진행 행이 없으면 빈 목록). `ProgressSummaryView.streakMonths` 는 보호권으로 지킬 수 있는 연속 포함(`streakMonthsAsOf`) — 공유 카드 요약 해시에 그대로 반영(그리는 값이라서), 보호권 개수는 카드에 그리지 않아 해시에 없다. 고른 칭호(streak-n 포함) 이름도 해시에 반영(기존 규칙).

- **9단계 게임 요소 2순위**(리더 브리프 `_workspace/09_brief.md`, 값은 `reward-rules.json` seasonCompleteBonus 150·revisitStampBonus 10·wishFulfilledBonus 20, `badges.json` revisit5·revisit20·wish3·wish10): 셋 다 회수 없는 보상, refId 한 번 — 계절 회차 완성 `season:{e}:{roundId}`(XpSource.SEASON_COMPLETE, `SeasonCompleted` 수령자마다, 칭호 출처 SEASON `season-{계절}` = 그 계절 회차 보상이 장부에 하나라도 있으면), 재방문 도장 `revisit:{e}:{code}@{year}`(REVISIT_STAMP, `RevisitStamped`, 뱃지 조건 REVISIT_STAMPS = 장부 건수), 가고 싶은 곳 `wish:{e}:{code}`(WISH_FULFILLED, `WishFulfilled`, 지역당 한 번 — 핀을 뺐다 다시 꽂아도, 뱃지 조건 WISHES_FULFILLED). 재계산: `rebuildBase` 가 세 출처를 남기고, 복구 규칙 `recoverRecords` 가 도감의 계절 완성(수령자)·탐험 `RevisitQuery.stampsOf`·`WishlistQuery.fulfilledOf` 에 있는데 장부에 없는 보상을 그 기록 시각으로 지급(병합으로 옮겨 온 도장·핀 포함). 재계산 보류 구독자에 `exploration.stamp-book`·`exploration.wishlist` 추가. `/progress` 에 `revisitStampCount`·`wishFulfilledCount`.

### 2-3. Collection (도감 진행)

- **코드 이름 매핑**: 설계 용어 Collection(도감) ↔ 코드 `CollectionBook`(JDK `java.util.Collection`과 겹치지 않게 — 명명 규칙). 리포지토리 `CollectionBookRepository`, 서비스 `CollectionBookService`, 구독자 `progression.collection-book`. 테이블 `set_progress`, API `/collection`, 이벤트 이름(`SetCompleted`)과 outbox aggregate 이름("Collection")은 그대로.
- **세트 ↔ 테마 매핑**: 설계 용어 세트(CollectionSet·SetProgress·세트 카탈로그) ↔ 코드 `Theme`·`ThemeProgress`·`Themes`(일급 컬렉션), 완성 결과 `ThemeCompletion`, 카탈로그 정의 `ThemeDefinition`, JPA `ThemeProgressJpaEntity` — 클래스명에 JDK 자료형 단어(Set)를 쓰지 않는 명명 규칙. **유지하는 외부 계약 이름**: 공개 이벤트 `SetCompleted`(필드 `setId` = 테마 id), 테이블 `set_progress`·컬럼 `set_id`, API 응답 `sets`·`SetResponse`·`setId`, 카탈로그 공개 Query `ProgressionRules.sets()`·`SetView`·`RewardCalculator.setComplete()`, 정의 JSON `sets.json`, 장부 refId 접두사 `set:`, `XpSource.SET_COMPLETE`, 칭호 출처 `SET`·id `set-{id}`, 뱃지 조건 `SETS_COMPLETED`, 퀘스트 지표 `SET_REGIONS`.
- 루트 `Collection(mapId)` — **지도 단위**. 세트별 진행·완성 여부.
- VO: `SetProgress{setId, collected:Set<RegionCode>, completedAt?}`
- 불변식: 완성은 정의된 지역 전부 모였을 때 단 한 번. 완성 후 지역 취소해도 완성 기록 유지(보상 회수 없음).
- 커맨드: `applyVisit(…, members)`, `revokeVisit(regionStillOnMap)` — 지도에 그 지역이 다른 멤버 방문으로 남아 있으면 진행에서도 빼지 않는다(D2), `revokeRegions`(탈퇴로 지도에서 사라진 지역)·`restoreRegions`(재가입 복구) / 이벤트: `SetCompleted{mapId, setId, explorerId(수령자), completedAt, completedBy, recipientIds}` — 3단계 결정 1: 완성 시점 지도 멤버 **전원**이 수령자이고 **수령자마다 한 건**씩 발행 → 진행(보너스 XP `set:{explorerId}:{setId}`·칭호)·꾸미기(세트 배경)가 explorerId 한 명만 처리. 수령자 목록은 set_progress `completed_member_ids` 에 남기고, 재계산 복구 규칙은 이 목록 기준으로만 지급(R2-1). 나중 합류 멤버는 세트 배경만(꾸미기가 MemberJoined + `progression.api.query.CollectionBookQuery`), XP·칭호 없음. `SetProgressed`는 구독자가 없어 발행하지 않는다.
- ExplorerProgress와 분리한 이유: 세트 정의 추가 시 진행 루트가 커지는 것을 막고 재계산 배치를 독립 실행.
- **9단계 계절 한정 테마 — 도감(CollectionBook)에 통합**(같은 지도 단위 진행·완성 시점 멤버 전원 수령이라 같은 애그리거트, 판정 규칙이 달라 별도 진행 VO·테이블): 정의 catalog `seasons.json`(`SeasonDefinition{id, MonthDay start/end(양 끝 포함, 한 해 안), regions, title}`, 칭호 `season-{id}`), 정책 `Season`·`SeasonRound{roundId={계절}-{연도}, [startsAt, endsAt)}`·`SeasonCalendar`(일급 — 시각에서 열린 회차를 계산, 회차 목록을 미리 만들지 않음, 재계산용 `excludingEndedBy(at)`), 진행 `SeasonProgress{roundId, marks: Set<SeasonMark(지역, 멤버)>, completedAt, completedMembers}`(테이블 `season_progress`, V7). 규칙: 그 회차 기간 안에 처리된 체크인만 표시(기간 전 방문·소급 없음 — 테마처럼 "지도에 칠해진 지역"이 아니라 기간 안에 센 방문의 지역), 기간 안 취소·탈퇴 숨김은 그 (지역, 멤버) 표시만 뺌(다른 멤버의 기간 안 방문이 남으면 진행 유지), 재가입 복구는 원래 처리 시각이 기간 안인 방문만(`VisitsRestored.restoredVisits`), 병합 재귀속(`MemberReassigned`, collection-book 구독)은 표시 주인 교체, 닫힌 회차는 확정 기록(취소·재계산도 안 바꿈), 완성은 회차당 한 번 → `SeasonCompleted`(수령자마다, aggregate Collection/mapId). 나중 합류 멤버 보상 없음(회차 한정 보상). 재계산: `rebuildBase(calendar, at)` 가 아직 닫히지 않은 회차의 표시만 비우고 같은 방문 재생으로 다시 셈. `CollectionBookQuery.completedSeasons(mapId)` → `CompletedSeasonView{roundId, completedAt, recipientIds}`(꾸미기 재계산). API `GET /seasons/current?mapId=`(`_workspace/09_contracts.md` §1). 추후 과제(리더 결정 Q3): 화면의 계절 배지는 개인 지도 기준 — 공유 지도 회차 표시는 후속 단계. `marks` 는 V8 에서 VARCHAR(8000)(QA P3-9).

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
- 구독자: `wardrobe.inventory`(RegionVisited·VisitCancelled·SetCompleted·MemberJoined·MapCreated(루트 행 선생성)·ThemeRewardOwed·InviteRewardOwed·ExplorerMerged(4단계 — 익명 탐험가의 재생 불가 아이템·초대 기록을 계정으로 이전)·**ProvinceConquered·StreakMilestoneReached(8단계)**). dev 시드·지우기는 루트 행(inventory·scene)을 지우지 않는다(Q-R2-2).
- 8단계 업적 보상: `GrantKind.PROVINCE_COMPLETE`(시·도 정복 대표 장식 `conquest:{시·도}` 17종, PROP·LEGEND)·`STREAK_MILESTONE`(마일스톤 한정 아이템 `streak:{n}` 4종) → 출처 EVENT, 회수 없음, `grantRewards` 로 아이템 단위 멱등(기간 판정 = 정복·도달 시각, 카탈로그 `ItemCatalog.grantedByProvinceConquest/grantedByStreakMilestone`). 받는 사람이 병합돼 비활성이면 into(`recipientOf`). `replayable()=false`(진행 기록에 묶인 보상이라 계정 병합 때 익명 쪽 것을 옮긴다). 재계산 `InventoryReplay.replay(…, themeRewards, achievementRewards, at)` — 진행 `achievementsOf` 의 업적 보상 중 빠진 것을 채운다(처음 얻은 시각 유지).

- 9단계: `GrantKind.SEASON_COMPLETE`(계절 회차 배경 `season:{roundId}`, 출처 SET_REWARD, 회수 없음, `replayable()=false` — 닫힌 회차는 재계산이 다시 세지 않으므로 병합 때 옮김, 카탈로그 `ItemCatalog.grantedBySeasonCompletion`, 구독 SeasonCompleted). 재방문 2회차 색 변형: `RevisitMarks`(일급 — 도장 받은 지역, 테이블 `inventory_revisit`, 지우지 않음, 구독 RevisitStamped → `markRevisited`, 이벤트 없음) → `Inventory.variantOf(itemRegion)` 1|2, 가방·장면 응답 `variant`·`variantLook`(카탈로그 `ItemDefinition.revisitVariantLook()` = 지역 특산물의 주색·보조색 교환). 병합 `absorbMerged` 가 표시도 합치고, 재계산 `InventoryReplay.replay(…, revisits)` 가 `RevisitQuery` 로 채운다(보류 구독자에 `exploration.stamp-book`).

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
- 5단계 구현: domain 폴더 `social/domain/{friendship, feed, ranking, stats}`.
  - `friendship`: `Friendship(follower, followee, since)`(자기 팔로우 불가), `Followings`(일급 — 로그인 필요 LOGIN_REQUIRED·보이는 대상 중복 ALREADY_FOLLOWING·**숨은 대상**(PRIVATE·친구 아닌 FRIENDS 이면서 나를 팔로우하지 않음)은 팔로우를 기록하되 `FollowResult.revealed=false` → 없는 handle 과 같은 404(로그인 확인은 handle 조회 전, PK 경합도 `duplicateError()` 로 404, 숨은 팔로우는 내 팔로잉 수·목록 등에 안 들어감 — `SocialCircle.revealedFollowing`, QA r2 P2-A), 언팔로우 멱등, 동시 경합은 PK + persist/flush 번역 — QA P3-3), `SocialCircle`(일급 — **친구 = 맞팔로우**, 친구 랭킹 대상 = 나 + **프로필이 나에게 보이는** 친구(PRIVATE 맞팔 숨김 — 리더 결정 1), 비교 허용 = 프로필이 나에게 보임, 관계 목록은 한쪽 팔로우한 숨은 대상을 뺀다). 팔로우 대상은 handle 있는(계정 연결) 탐험가만.
  - `feed`(읽기 모델 feed_entry, 구독자 `social.feed`): `FeedEntry`(record — refId = 원본 이벤트 멱등 키: 체크인 `visit:{map}:{code}:{explorer}#{gen}`, 테마 `theme:{e}:{setId}`, 레벨 `level:{e}:{n}`, 뱃지 `badge:{e}:{id}`, 8단계 마일스톤 `milestone:{e}:{n}`(kind STREAK_MILESTONE, detail.months)·시·도 정복 `conquest:{e}:{p}`(PROVINCE_CONQUERED, detail.provinceCode)·미스터리 `mystery:{e}:{weekStart}`(MYSTERY_FOUND, detail.regionCode·weekStart — 같은 지역이어도 주마다 다른 소식)), `ActivityFeed`(일급 — 같은 사람의 같은 소식은 가장 이른 것 하나, 최근 순), `FeedAge`(상대 시각 일 단위: 오늘·어제·N일 전·N주 전·N개월 전 — 정확한 시각·메모·사진 없음). `FeedGeneration`(세대). 취소 = (지금 주인, 지도, 지역) 중 **취소 이전(occurred_at ≤ cancelledAt)·회차 이하(visit_generation, 0 = 모름)** 만 거둠(retracted_at — 병합으로 옮겨진 소식(회차 0)도 거두고, 취소 뒤 재체크인은 남는다, QA P2-5·r2 P2-B), 탈퇴 숨김/복구 = hidden_at, 병합(ExplorerMerged) = from 의 소식을 into 로 귀속 + from 개인 지도 체크인 소식은 into 개인 지도로 + 병합 뒤 늦게 온 from 앞 이벤트도 into 로(`ExplorerProfileQuery.mergedInto`). **공개 범위는 읽을 때** 거른다(PRIVATE 안 보임, FRIENDS 맞팔만, PUBLIC 은 한쪽 팔로우에도 — 리더 Q2). **재구성(리더 결정 4)**: 다음 세대에 outbox 를 처음부터 재생한 뒤 feed_state.live_generation 을 바꾼다 — 릴레이는 `social.feed` 몫만 멈추고(`OutboxRelay.pauseSubscriber`) 나머지 구독자는 계속, 비동기 작업(app-api `readmodel.FeedRebuildJob`, 운영 `POST·GET /admin/rebuild/feed` X-Admin-Token, local `POST /dev/rebuild/feed` 는 끝까지 기다림), 실패하면 쌓던 세대만 버려 이전 피드 보존, 처리 실패는 `territory.social.feed-rebuild.max-skipped`(기본 0) 초과 시 FAILED, 읽기 불가 예전 행은 세기만(QA r2 P3-A). 정지는 즉시 효력이고 그 몫만 남은 행은 릴레이가 id 만 읽고 건너뛴다(QA r2 P3-C).
  - `ranking`(요청 시점 계산): `MapLeaderboard`(지금 멤버만, 이의 방문 제외 — 영토·선점·전설, 경쟁 순위. 선점 방문이 이의 표시되면 다음 이의 아닌 방문이 선점 — 리더 Q1, 탐험 `MapVisitView.claimOrder`), `FriendLeaderboard`(탐험가 단위 중복 제거 지역 수 → 레벨, 같은 지역 수 = 같은 순위). 재료는 탐험 `TerritoryQuery.mapVisits`(보이는 방문 + 선점·이의)·진행 `ExplorerRegionQuery`(explorer_region 활성 집계).
  - `stats`(일 1회 배치 `RankBatchJob`, cron `territory.social.rank-batch-cron`): `RankSnapshot`(class — rank_percentile·region_stats·province_stats 통째 교체, 정렬 한 번 순위·배치 insert), **모집단 = 활성 지역 1곳 이상인 활성 탐험가**(리더 결정 5 — 세 테이블 공통), 상위 % = ⌈100×순위/모집단⌉(최소 1), 지역 방문자는 활성 탐험가만 센다(병합 비활성 탐험가 제외 — `ExplorerRegionQuery.regionVisitorsAmong`, 그래도 모집단을 넘는 지역은 그 지역만 맞추고 `RegionOverflow` 로 드러냄 — QA r2 P3-B), `ProvinceStats.coldStartBaseline`(친구 0명 → 주 활동 시·도 평균, 없으면 전국 'ALL'). Redis 대신 DB + 애플리케이션 캐시(`territory.social.stats-cache-ttl`).
- VS 계산은 공유 커널 `common.model.TerritoryComparison`(4단계 VS 카드 `PublicVisits.versus` 와 소셜 `GET /compare/{handle}` 가 같이 씀). 입력은 호출자가 고른다 — 카드 = 개인 지도, 소셜 비교 = 탐험가 단위 활성 지역(친구 랭킹과 같은 수). 기준 차이는 수용하고 화면·카드에 명시("모든 지도 기준" / "개인 지도 기준" — 리더 결정 3).
- API: `POST·DELETE /friends/{handle}`, `GET /friends`, `GET /feed`, `GET /rankings/friends`, `GET /rankings/maps/{mapId}`, `GET /rankings/me/percentile`, `GET /compare/{handle}`, `GET /catalog/region-stats`. 공개 이벤트 없음(FollowStarted/Ended 구독자 없음).

### 2-8. ShareCard (공유)

- 루트 `ShareCard(explorerId, mapId, kind, version)`. 종류: 영토·최근 여행·리캡·VS.
- 불변식: 참조한 영토·장면 버전 기록, 버전 바뀌면 재생성.
- 커맨드: `render()`, `invalidate()`. 무효화만 하고 렌더링은 공유 링크가 실제 열릴 때(lazy), 최소 TTL 10분.
- 4단계 구현(파트 B, QA P2-1·P2-2 반영): domain 폴더 `sharing/domain/{card, privacy, showcase}`.
  - `card`: `ShareCard`(render = `markRendered(CardBasis, imageKey, at)`, 다시 그릴지 `needsRender(current, now, CardCachePolicy)` — 그린 적 없음 → 그림, **handle 이 바뀜(익명 → 계정, 변경) → TTL 면제하고 바로 그림**, 요약이 바뀜 + 최소 TTL 지남 → 그림, 기준 그대로면 TTL 무관하게 안 그림), `ShareCardId(explorerId, mapId=개인 지도, kind)`(VS 는 저장 안 함 — 메모리 LRU), `CardBasis(summaryHash, handle)`, `CardImageStorage` 포트(이미지 키에 기준 해시 — 같은 기준의 이미지만 기록).
  - **기준 = 실제로 그리는 공개 요약(Showcase + 종류 + 연도)의 SHA-256**(리더 결정). "invalidate()"는 별도 커맨드 없이, 렌더 요청 때 지금 요약 해시가 마지막 렌더 해시와 다르면 낡음. 이벤트를 구독하지 않는다 — 재계산·병합·칭호 변경처럼 이벤트 없이 바뀐 값도 놓치지 않는다(초안의 이벤트 시각 기반 CardSource·`sharing.card-source` 구독자는 QA P2-1 로 제거).
  - `privacy`: `PrivacySettings(explorerId, PUBLIC|FRIENDS|PRIVATE)` — **기본 PRIVATE**(사용자 결정 Q1, 프로필 탭 "공개하기"), FRIENDS 는 5단계부터 맞팔로우에게만(`visibleTo(viewer, 맞팔?)` — **주인 본인은 공개 범위와 무관하게 항상**(리더 결정 2 — 미리보기, 로그인 응답 `Cache-Control: private` + `Vary: Cookie`), 익명은 친구 아님, 공개 경로 /u·카드·VS·프로필 합류 모두 보는 사람 기준, `/u/**` 는 `@CurrentExplorer(required=false)` 로 선택적 식별, `PrivacyRoster` 로 여러 주인 한 번에), 공개 아니면 공개 경로 404(존재 숨김). 공개 Query `sharing.api.query.ProfileVisibilityQuery` — app-api 가 탐험의 `ProfileJoinGate` 포트로 이어 프로필 비공개면 프로필 합류도 404.
  - `showcase`(도메인 서비스·값): 공개 정보 `Showcase`(색칠·집계·월 단위 `VisitMonth` — 메모·사진·정확한 날짜 없음, `summaryHash`), `PublicVisits`(일급 컬렉션: 정복률·시·도 정복·전설·리캡·VS), `CardComposer`(Showcase → `CardContent` 4종), `CardRenderer` 포트(infra Java2D, OFL 글꼴 번들).
- API: `GET /u/{handle}`(HTML + OG), `/u/{handle}/card/{territory|recent|recap}.png`, `/u/{handle}/vs/{other}.png`(둘 다 공개), `GET /me/cards`, `/me/cards/{kind}.png`, `GET·PUT /me/privacy`. 프로필 링크 합류는 exploration `POST /maps/join-via-profile/{handle}` {mapId} — 지도장 + 공개 범위 PUBLIC 공유 지도만(`ExpeditionMap.openToProfileOf`), 초대코드 노출 없음.
- 9단계 카드 요약 해시 영향 확인: 계절 진행은 `completedSetIds`(테마)에 들어가지 않아 "도감 세트 N/9" 불변, 계절 칭호를 고르면 칭호 이름으로 반영(기존 규칙), 재방문 색 변형은 카드가 착용 아이템 이름만 그려 해시에 없음 — 코드 변경 없음.
- 초대 보상(§7, 4단계): `MemberJoined.invitedBy`(초대코드 = 지도장, 프로필 = 프로필 주인). 10단계: `MemberJoined.joinedVia`(`INVITE_CODE` \| `PROFILE_LINK`, 초대가 아닌 합류·예전 이벤트는 null — 분석이 초대 경로를 나눠 센다, 끝에 추가한 하위 호환 필드). 꾸미기 `wardrobe.inventory` 가 초대받은 쪽 Inventory 에서 판단(`Invitations` — 처음 합류·재가입 아님·셀프 아님·같은 쌍 1회, invite_reward)하고 받은 뒤, 초대한 쪽은 `InviteRewardOwed`(aggregate Inventory/inviterId)로 각자 트랜잭션에서. 아이템은 카탈로그 `INVITATION` 규칙(HOST·GUEST, 한정 = 유효 기간), EVENT 출처·회수 없음·재계산 유지.

### 2-9. ExpeditionMap (공유 지도)

- **소속: exploration 컨텍스트** (Territory와 생명주기가 묶여 있고 별도 모듈이 없다. 단, 같은 트랜잭션에서 둘을 수정하지는 않는다 — 체크인은 멤버 여부만 읽기 참조).
- 루트 `ExpeditionMap(mapId)`. 이름, 초대코드, 생성자, 멤버 목록, 국가 코드. 가입 시 개인 지도 자동 생성(멤버 1명). 지도는 여러 개 가질 수 있고 합류 시 개인 지도는 병합하지 않는다.
- VO: `InviteCode(8자, 재발급 가능)`, `Member{explorerId, role(OWNER|MEMBER), joinedAt}`, `CountryCode`, `MapSettings{photoRequired, dailyCheckInCap(기본 = territory.check-in.daily-cap, 지도장은 1..기본값으로 낮추기만 — Q1), visibility}`(개인 지도는 설정 변경 불가). 사진 필수는 새 체크인에만 적용(기존 방문의 메모·날짜 수정 허용, 있던 사진 삭제만 거부 — Q5). 탈퇴 유예 중인 사람은 자리를 차지하지 않아 그사이 4명이 차면 재가입은 MAP_FULL(의도 — 탈퇴 경고에 안내)
- 불변식: 멤버 ≤4. 초대코드는 지도당 하나·전체 유일. OWNER 한 명, 탈퇴 불가(양도 후 가능). 중복 가입 불가.
- 커맨드: `create(owner, name, country)`, `join(inviteCode, explorerId)`, `leave`, `transferOwner`, `regenerateInviteCode`
- 이벤트: `MapCreated` → 탐험이 빈 Territory·Collection 생성. `MemberJoined` → 인벤토리가 완성된 세트 보상을 새 멤버에게 지급. `MemberLeft` → 탈퇴 유예 처리.
- 탈퇴는 7일 소프트 삭제: 즉시 해당 멤버 방문에 `hidden_at`, 선점은 다음 체크인 멤버에게 이전(`ClaimTransferred`). 7일 내 재가입 시 방문 복구(선점은 안 돌아옴), 7일 후 배치가 하드 삭제.
- Territory와 분리한 이유: 멤버 가입·탈퇴가 방문 250건과 같은 락을 잡을 이유가 없다. 체크인은 멤버 여부만 읽기 참조로 확인.

### 2-10. StampBook · Wishlist (탐험 — 탐험가 단위 기록, 9단계)

- **StampBook(explorerId)** — 재방문 도장(§7). `RevisitStamp{region, year, stampedAt}`, 일급 `RevisitStamps`, 테이블 `revisit_stamp(explorer_id, region_code, stamp_year, stamped_at)`(연도 컬럼은 예약어를 피해 stamp_year). 불변식: 탐험가 단위로 칠한 지역(숨기지 않은 방문이 어느 지도든)만, 지금 연도(처리 시각, 서울) > 처음 칠한 해(`TerritoryRepository.firstVisibleVisitAt` — 남은 보이는 방문 중 가장 이른 처리 시각), 지역·연도당 하나, 취소 없음, **하루 상한을 개인 지도 체크인과 공유**(`CheckInContext.alsoUsing(n)`·`capReachedWith`, `ExpeditionMap.capSharedWithStamps` — 개인 지도 체크인은 오늘 도장 수를 더해 센다, 공유 지도는 0). `judge` → `StampEligibility{year, firstYear, stampedYears, refusal NOT_PAINTED|SAME_YEAR|ALREADY_STAMPED|DAILY_CAP, availableFromYear}`, `stamp` → `StampResult` → `RevisitStamped`(api.event, aggregate StampBook/explorerId). 동시성: 체크인과 같은 잠금(개인 지도 S → 개인 territory X → 탐험가 S, `MemberTerritoryLock` 으로 추출). 오류 코드(리더 결정 — 전용): 422 REVISIT_NOT_PAINTED·422 REVISIT_SAME_YEAR·409 REVISIT_ALREADY_STAMPED·422 DAILY_CAP_EXCEEDED(기존). 병합: 구독자 `exploration.stamp-book`(ExplorerMerged) — 합치고 재계산 예약. 공개 Query `RevisitQuery.stampsOf`.
- **Wishlist(explorerId)** — 가고 싶은 곳(비공개). 루트 `wishlist`(직렬화 잠금, 처음 꽂을 때 따로 커밋되는 트랜잭션에서 insert-if-absent) + `wish_pin`. 불변식: 칠하지 않은 지역만(409 WISH_ALREADY_VISITED), 아직 안 다녀온 핀 ≤ `WishlistPolicy.maxPins`(`territory.wishlist.max-pins` 30, 초과 422 WISHLIST_FULL), 핀 뒤에 칠하면(visitedAt ≥ pinnedAt) 한 번 다녀옴 → `WishFulfilled`(aggregate Wishlist/explorerId). 구독자 `exploration.wishlist`(RegionVisited 다녀옴 판정 — 핀 뒤의 체크인이거나 지금 칠해져 있으면(QA P3-2), ExplorerMerged 합치기 — 같은 지역은 먼저 꽂은 시각·다녀옴 우선, 상한 미적용, 합친 뒤 계정·익명 어느 쪽 방문으로든 이미 칠해진 대기 핀은 다녀옴 + WishFulfilled(리더 결정 Q1)). 잠금·격리: 핀 꽂기·빼기·병합·다녀옴은 READ_COMMITTED + 루트 잠금이 첫 조회(QA P1-1), 루트 생성은 따로 커밋되는 READ_COMMITTED 트랜잭션에서 하고 동시 생성의 유일성 위반·교착은 목표 상태로 흡수(QA P3-1). 공개 Query `WishlistQuery.fulfilledOf`.
- API: `POST·GET /revisits/{code}`, `GET /revisits`, `GET /wishlist`, `GET·PUT·DELETE /wishlist/{code}` — `_workspace/09_contracts.md`.
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
| xp_ledger | ExplorerProgress | id | source(8단계: + MYSTERY_BONUS·PROVINCE_CONQUEST·STREAK_MILESTONE), amount, ref_id UNIQUE(멱등), created_at |
| streak_freeze | ExplorerProgress | id | ref_id UNIQUE(멱등 — freeze:{e}:quests:{yyyy-MM}·milestone:{n}·use:{yyyy-MM}), reason(MONTHLY_QUESTS·MILESTONE·USED), amount(받음 양수·상한이면 0, 씀 음수), used_month, created_at — 8단계 V6. 재계산은 그 탐험가 행을 지우고 다시 넣는다 |
| mystery_week | 참조(카탈로그 소유) | week_start PK(월요일, Asia/Seoul) | region_code, selected_at — 8단계 V6. 전체 사용자 공통 선택 기록, 넣은 뒤 불변, 지난 주는 나중에 고르지 않음, dev 초기화도 지우지 않음 |
| badge_earned | ExplorerProgress | PK(explorer_id, badge_id) | earned_at |
| title_earned | ExplorerProgress | PK(explorer_id, title_id) | earned_at — 칭호도 추가만(2단계 추가) |
| set_progress | Collection | PK(map_id, set_id) | collected_codes JSON, completed_at NULL, completed_member_ids(V3 — 완성 시점 멤버 = 수령자), version — **지도 단위** |
| quest_progress | QuestBoard | PK(explorer_id, quest_period, quest_id) | current_count, tally(센 지역 키 집합), claimed_at, version. 상시 도전은 quest_period='ALL'(year_month 는 MySQL 예약어라 이름 변경) |
| inventory | Inventory | explorer_id PK/FK | version, updated_at — 루트 행(자식만 바뀌어도 version 강제 증가, 재계산·이벤트 처리의 잠금 대상). 3단계 V3_1 추가 |
| owned_item | Inventory | PK(explorer_id, item_id) | source(REGION\|SET_REWARD\|EVENT), grant_kind(방문형 여부 — 재계산 정리), acquired_at, favorite. §4 초안의 map_id 는 두지 않음(회수 판단은 근거 행) |
| owned_item_basis | Inventory | PK(explorer_id, item_id, region_code, map_id) | 체크인 아이템의 근거 활성 방문 — 모두 사라지면 회수(Q2). 3단계 V3_1 추가 |
| inventory_visit | Inventory | PK(explorer_id, region_code, map_id) | generation, active — 방문 흔적(세대 무시 규칙). 행은 지우지 않고 active=false. 3단계 V3_1 추가 |
| scene | Scene | explorer_id PK/FK | gender, slot_hat·slot_hand·slot_badge·slot_bag·slot_pet·slot_bg(초안의 slots JSON 대신 슬롯별 컬럼), props(쉼표 구분 ≤3, 순서 유지), version, updated_at |
| friendship | Friendship | PK(from_id, to_id) | created_at, IDX(to_id) — V5, FK 없음 |
| feed_entry | 읽기 모델 | id PK | generation(세대 — feed_state.live_generation), (generation, ref_id) UNIQUE(멱등), actor_id, map_id, kind, region_code(숨김 선택용), payload JSON(VARCHAR), occurred_at, retracted_at, hidden_at, IDX(actor_id, occurred_at) — V5 |
| share_card | ShareCard | PK(explorer_id, map_id, kind) | summary_hash·rendered_handle(초안의 scene_ver·visit_ver 대체 — 공개 요약 해시 + 찍힌 handle), image_key(초안 image_url, 기준 해시 포함), rendered_at, version — 4단계 V4_1. VS 는 저장하지 않음 |
| privacy_settings | PrivacySettings(공유) | explorer_id PK | visibility(PUBLIC·FRIENDS·PRIVATE, 행 없으면 PRIVATE), updated_at, version — 4단계 V4_1 |
| invite_reward | Inventory(꾸미기) | PK(invitee_id, inviter_id) | map_id, rewarded_at — 같은 쌍 1회, 지우지 않음. 4단계 V4_1 |
| explorer_region | ExplorerProgress(읽기 모델 겸용) | (explorer_id, region_code) | province_code, rarity, first_visited_at, active_map_count, active_map_ids + 자식 explorer_region_mark(explorer_id, region_code, map_id, mark — V3, 지도별 마지막 회차) — 전체 랭킹·상위%·도감 뱃지 + 기본 XP 회수 판단(D2) |
| region_stats / rank_percentile / province_stats | 집계 | region_code / explorer_id / province_code PK | 일 1회 배치(통째 교체) → DB + 애플리케이션 캐시(Redis 는 트래픽 이후) — V5. rank_percentile(region_count, rank_position, population, top_percent), region_stats(visitor_count, population), province_stats(explorer_count, region_count_sum, 'ALL' = 전국) |
| outbox | 공통 | id PK | aggregate, event_type, payload JSON, published_at. FK 없음 |
| outbox_delivery | 공통 | PK(event_id, subscriber) | status(PENDING·DELIVERED·FAILED), attempts, conflicts, first_conflict_at(V3), last_error, delivered_at — 구독자별 전달 기록(2단계 D5) |
| season_progress | Collection | PK(map_id, round_id) | marks(기간 안에 센 "지역\|멤버"), completed_at, completed_member_ids, version — 9단계 V7, 닫힌 회차도 지우지 않음 |
| revisit_stamp | StampBook | PK(explorer_id, region_code, stamp_year) | stamped_at — 9단계 V7, 지우지 않음 |
| wishlist / wish_pin | Wishlist | explorer_id PK / PK(explorer_id, region_code) | created_at / pinned_at, fulfilled_at — 9단계 V7 |
| inventory_revisit | Inventory | PK(explorer_id, region_code) | marked_at — 9단계 V7(재방문 2회차 색 변형), 지우지 않음 |
| item_definition | 참조(운영 추가) | item_id PK | name, emoji, slot, tier, theme, look·color_primary·color_secondary(룩), grant_rule·grant_ref(초안의 JSON 대신 두 컬럼), valid_from/to(DATE, 양 끝 포함), created_at(이슈 아이템 소급 판정 — Q-R2-1) — 3단계 V3_1 로 DB화(지역 250 + 세트 배경 9 이관, `tools/catalog/gen-item-sql.js`). 소유 = catalog(`ItemCatalog` Query, 30초 캐시 — 무효화는 커밋 뒤 세대 증가, 읽는 동안 세대가 바뀐 결과는 캐시하지 않음, 다른 인스턴스 추가분은 최대 30초 지연 허용 — P3-R2-6). `region:`·`set:` id 는 이관 전용(운영 추가 금지 — dev reset 이 운영 추가분만 지운다) |

| analytics_event | (분석 원본, 10단계 V9) | id PK, dedup_key UNIQUE | name·source(CLIENT·SERVER·REQUEST)·occurred_at(UTC)·event_day(서울)·actor_key(탐험가 해시 \| `v:`+방문 ID \| NULL)·explorer_hash·visitor_id·device·country·label·props(JSON 문자열). 90일 보관(일 배치 삭제). 커버링 인덱스 (event_day, actor_key, device)·(event_day, name, label, actor_key, device)·(actor_key, event_day, device)·(visitor_id, actor_key). JDBC(JPA 엔티티 없음) |
| analytics_visitor | (분석 방문) | visitor_id PK | first_seen_at·first_seen_day·entry·explorer_hash·device — 한 문장 upsert(ON DUPLICATE KEY UPDATE, 비어 있는 칸만 채움 — 중복 INSERT 후 UPDATE 는 MySQL 교착) |
| analytics_explorer | ExplorerJourney | explorer_hash PK | created_day·first_check_in_day·revisit_deadline·invited_join_day·invite_acquired — 서버 사실 구독자만 쓴다 |
| analytics_daily / analytics_daily_breakdown / analytics_cohort | (분석 집계) | metric_day PK / PK(metric_day, kind, name) / cohort_day PK | 하루 지표·기능별(FEATURE)·오류 코드별(ERROR)·코호트(퍼널 + D1/D7/D30, 아직 셀 수 없으면 NULL). 원본을 지워도 남는다. analytics_daily.partial_window(V10 — 30일 구간 앞부분 원본이 지워진 채 계산) |
| push_recipient | PushRecipient | explorer_id PK | mystery_enabled·streak_enabled·season_enabled(처음 모두 켜짐), created_at·updated_at — 12단계 V10. 구독·해지·설정·발송 계획을 탐험가 단위로 줄 세우는 잠금 대상(FOR UPDATE, READ_COMMITTED) |
| push_device | PushRecipient(자식) | endpoint_hash PK(구독 주소 SHA-256) | explorer_id(IDX), endpoint(≤1024), p256dh·auth(base64url), registered_at — 한 브라우저 구독은 한 탐험가에게만, 탐험가당 ≤ territory.push.max-devices(5, 넘으면 오래된 기기부터 뺌) |
| push_delivery | PushDelivery | id PK, UQ(explorer_id, kind, period) | delivery_day(서울 — 하루 최대 개수, IDX(explorer_id, delivery_day)), status(PENDING·SENDING·SENT·FAILED·EXPIRED·CANCELLED, IDX(status, next_attempt_at)), title·body·url·tag(계획 때 정한 문구), due_at·immediate(local 즉시 발송), next_attempt_at·attempts·claimed_at·sent_at·delivered_devices·last_error, version(발송기 잡기 낙관적 잠금) |

- 애그리거트 경계를 넘는 FK는 두지 않는다(예: scene.slots → owned_item 금지).
- gender는 Scene이 바꾸는 값이므로 scene 테이블에 둔다(explorer 아님).
- collected_codes·slots·props는 조회 조건이 아니므로 JSON 컬럼. 필요해지면 읽기 모델로 펼친다.
- explorer_progress와 scene은 1:1이지만 변경 주체(이벤트 vs 사용자)와 낙관적 락 범위를 분리하려고 떨어뜨렸다.

## 5. 공유 지도·랭킹·XP 규칙

- 공유 지도는 협력이 아니라 **경쟁**. 같은 지역을 멤버가 각자 체크인할 수 있고, 지역 색은 선점자(최초 체크인 멤버) 색.
- **랭킹 두 층, 다른 집계**: 지도 안 랭킹은 visit을 (map_id, checked_in_by)로 센다. 전체·친구 랭킹은 탐험가별 **중복 제거한 지역 수**(explorer_region) — 여러 지도에서 같은 지역을 찍어도 1. 탈퇴로 지도 visit이 삭제돼도 explorer_region은 남아 전체 랭킹은 줄지 않는다.
- 8단계 XP: 이번 주 미스터리 +50 `mystery:{e}:{weekStart}`(주당 1회), 시·도 정복 +300 `conquest:{e}:{p}`(시·도당 1회), 마일스톤 +50·100·200·400 `milestone:{e}:{n}` — 셋 다 회수 없음.
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
  - (5단계) `social.feed`(친구 소식 읽기 모델): RegionVisited · VisitCancelled · VisitsHidden · VisitsRestored · SetCompleted · LevelUp · BadgeEarned · ExplorerMerged. 재계산 보류 판정에는 넣지 않는다(읽기 모델). 새 구독자라 이미 발행된 outbox 행은 받지 않는다 — 배포 뒤 한 번 재구성(outbox 재생)한다.
  - (8단계) `wardrobe.inventory` 에 ProvinceConquered·StreakMilestoneReached(aggregate ExplorerProgress/explorerId — 업적 한정 아이템), `social.feed` 에 StreakMilestoneReached·ProvinceConquered·MysteryBonusEarned 추가(새 구독 타입 — 배포 뒤 한 번 재구성하면 지난 이벤트도 소식이 된다).
  - (12단계) 새 구독자 `notification.recipient`(PushRecipient): ExplorerMerged(익명 기기 → 계정). `analytics.events` 에 PushSent(aggregate PushDelivery/explorerId — 알림 공개 이벤트, push_sent).
  - (9단계) `progression.progress` 에 SeasonCompleted·RevisitStamped·WishFulfilled, `progression.collection-book` 에 MemberReassigned, `wardrobe.inventory` 에 SeasonCompleted·RevisitStamped, `social.feed` 에 SeasonCompleted·RevisitStamped(새 구독 타입 — 배포 뒤 재구성 권장), 새 구독자 `exploration.wishlist`(RegionVisited·ExplorerMerged)·`exploration.stamp-book`(ExplorerMerged).
  - (5단계 N1) `wardrobe.inventory` 의 MemberJoined·InviteRewardOwed 는 받는 사람이 병합돼 비활성이면 into 에게(`ExplorerProfileQuery.mergedInto`) — 병합 흡수보다 늦게 처리돼도 비활성 가방에 남지 않는다.
- 재계산 보류 기준(S3-3, QA P3-5·P3-6, 4단계 P3-R3-1·P3-R3-2): 진행 재계산은 그 탐험가·지도의 이벤트 중 **`progression.*`·`exploration.territory`·`exploration.expedition-map` 구독자에게 아직 DELIVERED 가 아닌 것**이 있으면 보류하고, 판정은 진행 루트를 잠근 뒤 같은 트랜잭션에서 한다. 꾸미기(인벤토리) 재계산도 같은 방식으로 **`wardrobe.*`·`exploration.territory`·`exploration.expedition-map`** 구독자의 미전달(`EventBacklog.hasUndelivered(ids, prefixes)`)이면 보류한다(인벤토리 루트 잠금 뒤 판정). `exploration.territory` 를 넣는 이유: 재가입 복구(VisitsRestored)·탈퇴 숨김·병합 흡수가 아직 영토에 반영되지 않았을 때 재계산이 그 지도를 덜 읽어 아이템·기본 XP 를 회수하고, 두 구독자는 그 결과 이벤트를 구독하지 않아 다음 재계산 전까지 복구되지 않는다. 재계산은 취소된 지역의 회차 표시(진행 explorer_region_mark·꾸미기 흔적)를 다시 만들지 못하고 손상된 표시를 고치지 못한다 — 보류 규칙으로 늦게 올 예전 이벤트가 없다는 전제에서 수용(QA P3-R2-4).
- 체크인 잠금 순서(3단계 QA P1-2): 체크인·수정·취소·이의는 지도 행 공유 잠금(FOR SHARE) → territory 배타 잠금. 지도 커맨드(합류·탈퇴·양도·설정·초대코드·유예 종료)는 지도 행 배타 잠금만 잡는다(territory 는 구독자가 별도 트랜잭션). 그래서 탈퇴와 동시 체크인이 직렬화되고(탈퇴 커밋 뒤 체크인은 403, 그 전 체크인은 탈퇴 시각보다 앞섬) 순환 잠금이 없다.
- 재계산 복구 규칙(Q2 승인, 3단계 R2-1 수정): 도감에 완성 기록이 있고 **그 탐험가가 완성 시점 멤버(completed_member_ids)인데** 장부에 그 세트 보너스(`set:{e}:{setId}`)가 없으면 지급, 보상 받은(claimed) 퀘스트인데 장부에 퀘스트 XP(`quest:…`)가 없으면 지급(지난 달 보드 포함). 칭호·뱃지는 함께 보정. refId 가 같아 멱등. 재계산은 지금 멤버인 지도만 다시 만들고, 탈퇴한 지도로 남은 explorer_region 활성과 그 기본 XP 는 그대로 둔다(탈퇴는 줄이지 않음).
- 재계산 운영(3단계 S3-2·S3-3): 탐험가(와 그가 속한 지도)에 아직 전달되지 않은 outbox 이벤트(FAILED 포함)가 있으면 그 탐험가는 건너뛰고 보고서의 `deferred` 에 넣는다(릴레이보다 앞선 영토를 읽어 "잠깐 있었던 완성"을 놓치지 않게). `recalculate-on-startup` 은 릴레이가 outbox 를 비울 때까지(territory.progression.recalculate-wait, 기본 5분) 기다린 뒤 돈다. **재계산은 트래픽이 적은 시간에 돌린다 — 실행 중(진행 루트를 잠그는 동안) 사용자의 칭호 선택(PUT /progress/title)은 409 로 실패할 수 있다.**
- outbox 충돌 시간 상한(3단계 결정 5): 낙관적 락 충돌 재시도는 횟수 상한에 세지 않지만 첫 충돌부터 `territory.outbox.relay.conflict-retry-limit`(기본 10분)을 넘기면 FAILED → 재전달 경로. 릴레이는 페이지(100행)의 전달 기록을 한 번에 읽고, FAILED 수를 최대 1분에 한 번 WARN 으로 알린다(R2-2).
- 친구 랭킹은 요청 시점 조인 계산(친구 수 적음). 시·도별/전국 정복률은 Territory 로드 후 메모리 계산(250건). 지역별 방문자 비율·상위 N%는 일 1회 배치 → (5단계) DB + 애플리케이션 캐시, Redis 는 트래픽 이후.

## 6. 단계별 진행 순서

| 단계 | 범위 | 산출물 | 목표일 |
|------|------|--------|--------|
| 0 | 뼈대 | 멀티모듈 스캐폴드 (scaffold-multimodule 스킬) | — |
| 1 | 카탈로그 + 탐험 | Flyway V1(explorer, **expedition_map, map_member**(개인 지도 자동 생성에 필요한 최소), **territory**(Territory 루트·잠금 행), visit, outbox) + app-api에 flyway 의존성 추가·local도 Flyway 전환, `POST /visits`, `DELETE /visits/{code}`, `GET /territory`, CheckInPreview, 지역 250개 JSON·GeoJSON | 2026-10-13 |
| 2 | 진행 | V2(explorer_progress, xp_ledger, badge_earned, title_earned, explorer_region, set_progress, quest_progress, outbox_delivery), 이벤트 핸들러 3개(진행·도감·퀘스트), 재계산 배치, XP 공식·뱃지 12·세트 9·퀘스트 정의, api 패키지 분리(api.event·api.query·api.web) | 2026-10-27 |
| 3 | 꾸미기 | V3__shared_map(explorer 토큰 해시, map_member.left_at, visit 회차·숨김·이의, visit_generation, explorer_region_mark, expedition_map.version, set_progress.completed_member_ids, outbox_delivery.first_conflict_at) + V3_1__wardrobe(owned_item, scene, item_definition), `PUT /scene`, 자동 착용 핸들러, 아이템 정의 DB화 + `POST /admin/items`, 지도 설정·초대·탈퇴 유예 전체 기능(MapSettings, disputed, hidden_at) | 2026-11-10 |
| 4 | 공유 + 구글 로그인 | V4__account(explorer.status·merged_into, account, recalculation_request — 파트 A) + V4_1__sharing(share_card·privacy 등 — 파트 B), 구글 OIDC 로그인(클라이언트 ID 없으면 비활성)·세션·CSRF, handle, claimExplorer 병합, 공개 프로필 `/u/{handle}`, OG 카드 렌더러(lazy), 스냅샷 저장·무효화, 초대 보상 | 2026-11-24 |
| 9 | 게임 요소 2순위 | V7__seasons_revisits_wishlist, 계절 한정 테마(도감 통합 `SeasonProgress`·`SeasonCalendar`, `SeasonCompleted`), 재방문 도장(탐험 `StampBook`, `RevisitStamped`, 하루 상한 공유), 가고 싶은 곳(탐험 `Wishlist`, `WishFulfilled`), 진행 보상·뱃지 4·칭호 2, 꾸미기 계절 배경·2회차 색 변형, 소식 2종, `GET /seasons/current`·`/revisits`·`/wishlist` | — |
| 12 | PWA + 웹 푸시(백엔드) | V10__push_notifications(push_recipient·push_device·push_delivery + analytics_daily.partial_window), 새 컨텍스트 `notification`: 구독 `POST·DELETE /push/subscriptions`·공개 키 `GET /push/vapid-public-key`·설정 `GET·PUT /push/preferences`, 알림 3종 스케줄(계절 시작일·월요일 미스터리·월말 3일 전 스트릭 지키기 — 진행 `StreakQuery` 추가), 하루 1개·조용한 시간·멱등·재시도·만료, 웹 푸시 발송(RFC 8291 암호화 + RFC 8292 VAPID, JDK 표준 암호 — 외부 라이브러리 없음), 분석 push_sent·push_open, local `/dev/push/*`. 10단계 QA r2 P3 4건 | — |
| 10 | 분석 이벤트(백엔드) | V9__analytics, 새 컨텍스트 `analytics`(관찰자): 화면 이벤트 수집 `POST /events`(허용 목록·필드 검증·개인정보 필드 차단·묶음 50개·본문 32KB·방문/주소 레이트 리밋·익명 수집 + 토큰·세션이면 탐험가 해시 연결), 서버 사실 구독자 `analytics.events`(멱등 지문), 공개 카드 열람 필터, 일 배치·90일 삭제, `/admin/metrics`, local 시드. 탐험 `MemberJoined.joinedVia` 추가(초대 경로) | — |
| 8 | 게임 요소 1순위 | V6__game_rewards(streak_freeze, mystery_week, conquest: 17·streak: 4 아이템 이관), 보호권·마일스톤·이번 주 미스터리·시·도 정복(진행), 미스터리 선택(카탈로그 `MysteryRegionQuery`, `MysteryDraw`), 업적 아이템(꾸미기), 소식 3종(소셜), `GET /mystery/this-week`, `GET /progress` 확장, local `GET·POST·DELETE /dev/clock`(앞으로만 미는 서버 시계 `AdjustableClock`) | — |
| 5 | 소셜 | V5__social(friendship, feed_entry, rank_percentile, region_stats, province_stats), 피드 프로젝터(social.feed)·재구성, 랭킹 두 층(요청 시점), VS 비교(공유 커널), 상위 % 배치, FRIENDS 공개 범위 실동작 | 2026-12-08 |

Flyway 규칙(QA P3-R2-10): 커밋된 마이그레이션은 고치지 않는다 — 커밋 이후의 스키마 변경은 새 버전 번호(V3_2, V4 …)로만 한다(3단계 V3·V3_1 은 커밋 전이라 그 자리에서 수정했다).

3단계 데이터 주의(Q4): 3단계 이전에 만든 탐험가는 접근 토큰 해시가 없어 인증할 수 없고 진행 루트 선생성(S3-1)도 되어 있지 않다. 운영 데이터가 없으므로 backfill 하지 않는다 — 로컬은 `DELETE /dev/reset` 으로 정리하고 다시 발급한다.

Flyway 번호 갱신(4단계): 4단계는 마이그레이션이 생겨 V4(파트 A)·V4_1(파트 B)을 쓰고, 원래 V4 였던 5단계 소셜은 **V5** 로 민다. V3·V3_1 은 3단계 커밋(1cfca7c) 이후라 고치지 않는다(P3-R3-7).

8단계(게임 요소 1순위) Flyway **V6__game_rewards**(V1~V5 수정 없음 — 7단계는 마이그레이션 없음).

9단계(게임 요소 2순위) Flyway **V7__seasons_revisits_wishlist**(season_progress, revisit_stamp, wishlist, wish_pin, inventory_revisit, 계절 회차 배경 `season:autumn-2026`~`season:autumn-2030` 9종 이관 — 이후 회차는 운영이 SEASON_COMPLETE 규칙으로 추가). V1~V6 수정 없음.

12단계(웹 푸시) Flyway **V10__push_notifications**(push_recipient·push_device·push_delivery, analytics_daily.partial_window 추가 — V1~V9 무수정, 게임 테이블 FK 없음). 11단계는 마이그레이션 없음.

10단계(분석 이벤트) Flyway **V9__analytics**(analytics_event·analytics_visitor·analytics_explorer·analytics_daily·analytics_daily_breakdown·analytics_cohort — 게임 테이블 무수정·FK 없음).

10단계 분석(analytics 컨텍스트 — `analytics/domain/{tracking, actor, journey, metrics, ratelimit}`):
- 이벤트 허용 목록 `EventDefinitions`(화면 10·서버 사실 14·공개 페이지 3, 계약 `_workspace/10_contracts.md` §1). 필드는 짧은 식별자·오류 코드·지역 코드·작은 수·참거짓만(자유 문장 없음), 정의 밖 필드·개인정보로 보이는 키(memo·handle·email·ip·userAgent·위치·token·explorerId 등)는 이벤트째 거절. 서버 사실 이름을 화면이 보내면 거절.
- 개인정보: 탐험가 id → `ExplorerHasher`(HMAC-SHA256, 비밀값 `territory.analytics.salt` — local 고정값, 운영 `TERRITORY_ANALYTICS_SALT` 필수). 서버 사실 멱등 지문 = 같은 해시 함수(공개 이벤트 종류 + record 내용 전체). IP·User-Agent 원문 저장 없음(기기 유형 `DeviceType` 다섯 갈래·나라 `CF-IPCountry` 만), handle·메모 없음.
- 사람 세기: 행위자 열쇠 `ActorKey` = 요청의 탐험가 → 방문에 이어진 탐험가 → `v:`+방문 ID. 방문이 처음(또는 다시) 그 요청의 탐험가로 이어지면 방문 열쇠로 남은 이벤트를 탐험가로 다시 묶는다(`VisitorLink.relinkTarget`). 봇은 사람 지표에서 뺀다.
- 여정 `ExplorerJourney`(가입일·첫 체크인·재방문 마감일·첫 초대 합류·초대 유입, 모두 한 번만): 가입 사실(MapCreated PERSONAL) 없이 처음 본 탐험가(분석 전 가입)는 가입일을 모르고 첫 체크인 퍼널·리텐션 코호트에 넣지 않는다. 쓰는 쪽은 구독자 하나(릴레이 한 스레드)라 불러와 바꾸고 저장한다.
- 지표 정의(`MetricsPolicy` 기간 설정값): DAU/WAU/MAU(그날로 끝나는 1·7·30일 활동 사람), 퍼널(그날 처음 본 방문 → 첫 화면 날부터 7일 안 첫 체크인 → 첫 체크인 다음 날부터 7일 안 재방문), DN 리텐션(가입일 + N 일째 하루의 활동, 그날이 다 지나야 셈), K 계수(30일 구간 가입자 중 초대 유입 ∪ 카드 유입 ÷ 30일 활성 탐험가), 기능별 사용률(7일, 기능 이벤트를 쓴 사람 ÷ 활성 사람), 상위 오류 코드(7일 error_toast).
- 일 배치 `MetricsBatchJob`(cron, 수동 `POST /admin/metrics/batch`·local `POST /dev/analytics/batch`): 원본이 남은 지난 90일(`MetricsPolicy.backfillRange`) 중 하루 지표는 최근 3일 + 비어 있던 날, 코호트는 최근 35일 + 비어 있던 날 — 지표 조회의 `missingDays` 와 같은 범위(QA P2-1, 보관 기간 지난 날은 `expiredDays`). 원본은 90일 지나면 5,000줄씩 삭제, 마지막 활동이 보관 기간보다 오래된 방문·여정도 삭제. 오늘은 조회 때 실시간(운영 60초 재사용).
- 레이트 리밋 `IngestThrottle`(토큰 버킷 — 방문 ID·요청 주소 두 겹, 인스턴스 메모리 = 단일 인스턴스 가정). 요청 주소 = `TrustedProxies`(설정 `territory.analytics.trusted-proxies`, 기본 없음): 실제 접속 주소가 믿는 프록시일 때만 `CF-Connecting-IP` → `X-Forwarded-For` 오른쪽부터 믿는 프록시를 건너뛴 첫 주소(QA P2-2). 접속 주소·헤더는 가장 안쪽 서블릿 요청에서 읽는다(framework 전략의 감싼 요청 회피). `POST /events` 는 CSRF 제외(JSON 본문만, sendBeacon). 이벤트 자리 null·타입 오류는 400 `MALFORMED_REQUEST`(서버 오류 기록 없음), 받은 이벤트가 없는 묶음은 방문으로 세지 않는다.
- infra 는 JDBC(JPA 엔티티 없음 — 저장 방식 예외는 사용자 결정 대기). 보강: 모든 SQL 상수를 MySQL 에서 EXPLAIN 하는 테스트(`AnalyticsMySqlTest.everyQueryMatchesSchema`), props JSON 은 직렬화기(Jackson)로.
- 운영 비밀값 강도(app-api `ProductionSecrets`, prod): 관리자 토큰·미스터리·분석 비밀값이 16자 미만·자리표시자면 기동 실패.

12단계 웹 푸시 알림(notification 컨텍스트 — `notification/domain/{recipient, delivery, campaign, push, policy}`):
- 받는 사람 `PushRecipient`(루트 push_recipient + 기기 `Devices` 일급 컬렉션): 구독 = 기기 등록(같은 endpoint 면 키·등록 시각만 갱신, 상한 넘으면 가장 오래 전에 등록한 기기를 뺀다 — 지금 켠 브라우저는 꼭 받음), 받을 주소 `EndpointRules`(알려진 푸시 서비스 호스트만·https — SSRF 방지, local 은 localhost 허용), 해지·만료(404·410 — 보낸 뒤 다시 구독한 기기는 남김), 종류별 설정 `NotificationPreferences`(세 값 모두), 닿는지 `Reach`(기기 없음 = 동의 안 함), 병합 `absorb`. 같은 브라우저를 다른 탐험가가 구독하면 그 탐험가로 옮긴다.
- 발송 기록 `PushDelivery`: 멱등 열쇠 `DeliveryKey`(탐험가·종류·기간), 계획 판단 `DeliveryPlanner`(같은 열쇠 → 설정·기기 → 그날 받을 알림 수 ≥ daily-limit(1) 순), 잡기 `claim`(보낼 때가 된 PENDING 또는 claim-timeout 넘은 SENDING, 그날 안·조용한 시간 밖이 아니면 EXPIRED), 보내기 직전 재확인 `confirmReach`, 결과 `complete(SendReport)`(한 기기라도 받으면 SENT, 모두 GONE 이면 CANCELLED, 잠시 실패면 `RetryPolicy` 지수 백오프·Retry-After — 다음 시도가 그날 밖·조용한 시간이면 EXPIRED, 횟수 끝·거절이면 FAILED), 자기가 잡은 claim 이 아니면 `DeliveryReclaimed`. 보냈으면 `DeliverySent` → application 이 공개 이벤트 `notification.api.event.PushSent` 로 outbox.
- 달력 `CampaignCalendar`(조용한 시간 `QuietHours` 22~08 서울, 스트릭 날 = 그 달 마지막 날 − 3): 미스터리는 그 주 월요일(기간 = 월요일 날짜, 주는 카탈로그 `MysteryRegionQuery.thisWeek()`), 스트릭은 그 날(기간 = 달, 대상은 진행 `StreakQuery` 의 atRisk — `Streak.atRiskIn`: 이번 달 미활동 + 보호권으로 이어지는 연속 있음), 계절은 시작일(기간 = 회차 id, `SeasonStarts`). 보낼 시각은 조용한 시간을 피하고 그 시각의 서울 날짜가 하루 개수의 "하루". local 즉시 발송(force)은 날짜 조건·조용한 시간만 건너뛴다(`Campaign.immediate`). 문구 `CampaignMessages`(지역 이름 비공개, 클릭 경로 `/?from=push&push={kind}#{탭}`).
- 잠금·격리: 구독·해지·설정·계획 모두 READ_COMMITTED + 루트 잠금이 첫 조회(루트 없으면 따로 커밋되는 트랜잭션에서 만듦). 계획은 사람마다 짧은 트랜잭션, 같은 열쇠 동시 계획은 UNIQUE 가 한 번 더 막는다. MySQL 동시성 테스트(기기 상한·같은 브라우저·하루 1개).
- 발송기 `DeliveryDispatcher`(fixed delay, 100개씩, 초당 20건 `SendPacer` — 단일 인스턴스 가정): 잡기 → 재확인 → 기기마다 보내기(트랜잭션 밖) → 기록(받는 사람 잠금 → 없어진 기기 지움 → 상태 → outbox). 보내기 포트 `PushSender` 의 구현 `infra/webpush/WebPushClient`(RFC 8291 aes128gcm 암호화 — RFC 부록 A 벡터로 검증, RFC 8292 VAPID ES256, JDK HttpClient·리디렉션 안 따라감). VAPID 키는 기동 때 형식·한 쌍 검사, prod 는 로컬 시험용 키·예시 연락처면 기동 실패(app-api `ProductionSecrets`). JWT 만료는 실제 시각(서버 시계를 밀어도).
- infra 하위 패키지 예외: `infra/webpush`(외부 클라이언트 — entity·repository 와 별도, 리더 확인 요청).

8단계 이번 주 미스터리 지역(카탈로그 소유 — `catalog/domain/mystery/`):
- 주 = 서비스 시간대(Asia/Seoul) 월요일 0시 ~ 다음 월요일 0시 직전(`MysteryWeek.weekStartOf`, 시계 = Clock 빈의 zone). 전체 사용자 공통 한 곳.
- 고르기 `MysteryDraw`(순수 함수): 현행 지역 중 `mystery.json` 희귀도(RARE·LEGEND)를 방문자 비율(일 1회 집계 region_stats — `VisitorShares`, 없으면 0) 오름차순·같으면 **그 주의 섞기 수**(`MysterySeed.of(week, region)` — QA P3-1: 통계가 0이어도 주마다 전국에서 고르게) 순으로 세운 뒤 아래쪽 `bottomFraction`(0.3, 올림·최소 1곳)에서 주차 시드(`MysterySeed.of(week)`)로 한 곳 — 같은 입력이면 같은 답.
- 주차 시드 `MysterySeed`(QA Q1): SHA-256(서버 비밀값 `territory.mystery.salt` + 주차[+ 지역]) — local 은 application-local.yml 고정값, 운영은 환경변수 `TERRITORY_MYSTERY_SALT` 필수(없거나 비면 기동 실패, `.env.example`·compose.yaml·operations.md). 비밀값을 모르면 공개 규칙·통계로 다음 주를 미리 셀 수 없고, 같은 주·같은 비밀값이면 재시작해도 같다. 값은 app-api → catalog `application.MysterySettings` 빈.
- 기록 `mystery_week`(PK 주): 기록이 있으면 그대로(통계가 바뀌어도, 서버를 다시 띄워도), 없으면 **지금 주일 때만** 골라 기록(`MysteryWeek.drawableAt` — 지난 주를 나중에 고르지 않음 = 소급 없음, 다음 주를 미리 고르지 않음). 동시에 고르면 PK 충돌 쪽이 새 트랜잭션에서 기록을 읽는다. 읽기·쓰기는 호출자 트랜잭션과 따로(REQUIRES_NEW) — 체크인·진행 이벤트 처리 중에 불려도 스냅숏·잠금에 묶이지 않게. 한 번 읽은 주는 메모리 캐시(불변 기록), 기록이 없다고 확인한 **지난 주**도 캐시(다시 고르지 않으므로 — QA P3-7).
- `GET /mystery/this-week` 의 `revealed`(QA P3-8): 이번 주 보너스를 받았으면 true — 화면은 지역 이름을 바로 공개, 아니면 지금처럼 ❓ 마커·카드를 누른 뒤에.
- 공개 Query `catalog.api.query.MysteryRegionQuery`(`weekOf(Instant)` · `thisWeek()`) → `MysteryWeekView{weekStart, startsAt, endsAt, regionCode, selectedAt}`. 진행(보너스 판정·`GET /mystery/this-week`)과 탐험(미리보기 보너스 줄)이 같이 쓴다. 보너스 액수는 보상 함수(`RewardCalculator.mysteryBonus`·`checkIn(…, mysteryOfWeek)`).
- dev 시드·지우기의 진행 초기화(`dev.ExplorerDataReset`, QA P2-1): 레벨 1 칭호는 이미 있으면 건너뛴다(가입 직후 비동기 개인 지도 생성 처리와 겹쳐도 유일성 위반 없음).
- 서버 시계(local E2E): app-api `config.AdjustableClock`(시스템 시계 + 앞으로만 미는 간격) — 미는 길은 local 전용 `POST /dev/clock {days|hours|minutes|to}`·`GET /dev/clock`·`DELETE /dev/clock`, `DELETE /dev/reset` 도 되돌린다. 운영은 간격 0.

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
- 이슈 아이템 지급 규칙은 3종만(기간 내 체크인 / 특정 시·도 / 세트 완성). 규칙 엔진 안 만든다. 8단계: 업적 규칙 2종 추가 — `PROVINCE_COMPLETE`(grantRef = 시·도 코드, 시·도 정복)·`STREAK_MILESTONE`(grantRef = 개월 수, 연속 탐험 마일스톤), 회수 없음, 운영이 나중에 더한 것은 정의 생성 뒤의 정복·도달에만(이관 데이터 conquest:·streak: 는 예외), `Catalog.requireReferences` 가 시·도·정의된 마일스톤을 확인. 3단계 구현: grantRule 5종 = `REGION_VISIT`(지역 특산물, 기본) · `PERIOD_CHECK_IN`(기간 필수, 처리 시각 날짜 기준) · `PROVINCE_CHECK_IN` · `THEME_COMPLETE` · `MANUAL`(자동 지급 없음 — 수동 지급 API 는 4단계 역할 기반 인가와 함께 이월). 체크인 이슈 아이템은 근거 방문이 모두 취소되면 회수(Q2), 정의 생성 전 체크인에는 소급 지급 없음(Q-R2-1). `POST /admin/items` 는 `X-Admin-Token`(territory.admin.token, prod 환경변수) — 4단계 로그인 후 역할 기반으로 대체.
