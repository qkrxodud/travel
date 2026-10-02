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
| progression | common, catalog, exploration:api | exploration의 domain/infra |
| wardrobe | common, catalog, exploration:api, progression:api | — |
| social | common, exploration:api, progression:api | 다른 컨텍스트의 domain |
| sharing | common, catalog, 모든 컨텍스트의 api(Query) | 다른 컨텍스트의 domain |
| app-api | 전부 | 도메인 로직 작성 금지 |

common에 두는 것: ExplorerId, RegionCode, Rarity, DomainEvent, Outbox, Clock. Spring 의존 없음(이벤트 퍼블리셔 인터페이스용 spring-context만 예외). Rarity는 공개 이벤트(RegionVisited 등)에 실리는 Published Language 값이고 exploration.domain이 catalog를 참조할 수 없어 공유 커널에 둔다.

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
- 이벤트: `RegionVisited{explorerId, mapId, regionCode, rarity, provinceCode, visitedAt, isFirstInProvince, nth, isFirstClaim}`, `VisitEdited`, `VisitCancelled`
  - `isFirstInProvince`·`nth`·`isFirstClaim`은 Territory가 자기 상태로 계산해 실어 보낸다(하류가 영토를 다시 읽지 않게).
  - `visitedAt`은 처리 시각(서버 시계). `visitDate`는 기록용 표시 값일 뿐 진행에 영향 없음. `VisitEdited`는 진행이 구독하지 않는다.
- `CheckInPreview` 도메인 서비스: 순수 함수(현재 영토 + 지역 → 보상 목록). 체크인 모달의 "획득 XP +90"을 계산하고, 같은 함수를 이벤트 핸들러가 재사용해 예상/실제 불일치를 막는다.

### 2-2. ExplorerProgress (진행)

- 루트 `ExplorerProgress(explorerId)`. XP 장부, 레벨, 선택 칭호, 스트릭, 뱃지 목록.
- VO: `XpLedgerEntry{source, amount, refId, at}`, `Level`, `Title`, `Streak{months, lastMonth}`, `BadgeId`
- 불변식: XP = 장부 합계(감소는 음수 항목으로만). 레벨은 결정적 함수 `floor((1+√(1+xp/5))/2)`. 뱃지는 추가만(회수 없음). 선택 칭호는 획득한 것 중에서만.
- 커맨드: `applyVisit(RegionVisited)`, `revokeVisit(VisitCancelled)`, `grantBadge`, `selectTitle`, `grantQuestReward`
- 이벤트: `XpGained`, `LevelUp`, `BadgeEarned`, `StreakChanged`
- 방문 취소 시 장부의 `refId`로 해당 XP만 정확히 되돌린다. 스트릭은 여기(달을 넘나드는 연속 값이라 QuestBoard가 아님). XP·레벨·스트릭은 본인 체크인만 집계.

### 2-3. Collection (도감 진행)

- 루트 `Collection(mapId)` — **지도 단위**. 세트별 진행·완성 여부.
- VO: `SetProgress{setId, collected:Set<RegionCode>, completedAt?}`
- 불변식: 완성은 정의된 지역 전부 모였을 때 단 한 번. 완성 후 지역 취소해도 완성 기록 유지(보상 회수 없음).
- 커맨드: `applyVisit`, `revokeVisit` / 이벤트: `SetProgressed{setId, have, total}`, `SetCompleted{setId}` → 진행(보너스 XP)·꾸미기(세트 배경 전원 지급)가 구독.
- ExplorerProgress와 분리한 이유: 세트 정의 추가 시 진행 루트가 커지는 것을 막고 재계산 배치를 독립 실행.

### 2-4. QuestBoard (월간 퀘스트 · 상시 도전)

- 루트 `QuestBoard(explorerId, yearMonth)`. 달이 바뀌면 새 보드. 상시 도전은 yearMonth 센티넬 `'ALL'` 보드 하나.
- VO: `QuestProgress{questId, current, target, claimedAt?}`
- 불변식: 보상은 달성 후 한 번만. 지난 달 보드는 불변. 월간 퀘스트는 본인 체크인 기준, 소급 없음(체크인 시점 기준).
- 커맨드: `applyVisit`, `claim(questId)` / 이벤트: `QuestCompleted{questId, xp}` → 진행이 구독.

### 2-5. Inventory (보유 아이템)

- 루트 `Inventory(explorerId)`.
- VO: `OwnedItem{itemId, source(REGION|SET_REWARD|EVENT), acquiredAt, favorite}` — itemId는 지역 아이템 `region:{code}`, 세트 보상 `set:{setId}`
- 불변식: 같은 itemId 한 번만. 지역 아이템은 해당 지역 방문 취소 시 회수. 세트 보상은 회수 없음.
- 커맨드: `grant(itemId, source)`, `revoke(itemId)` / 이벤트: `ItemGranted`, `ItemRevoked` → Scene이 구독(착용 중 회수되면 벗김).
- 지역 아이템은 체크인한 본인에게, 세트 보상은 지도 멤버 전원에게. `MemberJoined` 시 이미 완성된 세트 보상을 새 멤버에게 지급.
- 아이템 생김새(슬롯·룩·색·이름)는 카탈로그의 ItemDefinition 참조 — 여기 없음.

### 2-6. Scene (장면 · 꾸미기)

- 루트 `Scene(explorerId)`. 성별, 슬롯별 착용, 장식 3칸.
- VO: `Gender`, `EquipSlot(HAT|HAND|BADGE|BAG|PET|BG)`, `PropSlots(≤3)`
- 불변식: 착용 아이템은 모두 Inventory에 존재. 한 슬롯에 하나, 장식 ≤3, 아이템 슬롯과 장착 슬롯 일치.
- 커맨드: `equip(slot, itemId)`, `unequip(slot)`, `setGender`, `autoEquip(itemId)`(빈 슬롯이거나 더 높은 희귀도일 때)
- 이벤트: `SceneChanged` → 공유가 구독(카드 스냅샷 무효화).
- Inventory와 분리한 이유: 보유는 탐험 파생, 착용은 사용자 선택 — 변경 빈도·주체가 다름.

### 2-7. Friendship (소셜)

- 루트 `Friendship(fromExplorerId, toExplorerId)` — 팔로우 관계 한 건이 애그리거트 하나.
- 불변식: 자기 팔로우 불가, 중복 불가. 커맨드: `follow`, `unfollow`
- 랭킹·VS 비교·피드는 애그리거트가 아니라 **읽기 모델**: `RegionVisited`·`SetCompleted`·`LevelUp`을 구독해 `ActivityFeedEntry`·`LeaderboardRow`를 쌓는 프로젝터.

### 2-8. ShareCard (공유)

- 루트 `ShareCard(explorerId, mapId, kind, version)`. 종류: 영토·최근 여행·리캡·VS.
- 불변식: 참조한 영토·장면 버전 기록, 버전 바뀌면 재생성.
- 커맨드: `render()`, `invalidate()`. 무효화만 하고 렌더링은 공유 링크가 실제 열릴 때(lazy), 최소 TTL 10분.

### 2-9. ExpeditionMap (공유 지도)

- **소속: exploration 컨텍스트** (Territory와 생명주기가 묶여 있고 별도 모듈이 없다. 단, 같은 트랜잭션에서 둘을 수정하지는 않는다 — 체크인은 멤버 여부만 읽기 참조).
- 루트 `ExpeditionMap(mapId)`. 이름, 초대코드, 생성자, 멤버 목록, 국가 코드. 가입 시 개인 지도 자동 생성(멤버 1명). 지도는 여러 개 가질 수 있고 합류 시 개인 지도는 병합하지 않는다.
- VO: `InviteCode(8자, 재발급 가능)`, `Member{explorerId, role(OWNER|MEMBER), joinedAt}`, `CountryCode`, `MapSettings{photoRequired, dailyCheckInCap(기본 5), visibility}`
- 불변식: 멤버 ≤4. 초대코드는 지도당 하나·전체 유일. OWNER 한 명, 탈퇴 불가(양도 후 가능). 중복 가입 불가.
- 커맨드: `create(owner, name, country)`, `join(inviteCode, explorerId)`, `leave`, `transferOwner`, `regenerateInviteCode`
- 이벤트: `MapCreated` → 탐험이 빈 Territory·Collection 생성. `MemberJoined` → 인벤토리가 완성된 세트 보상을 새 멤버에게 지급. `MemberLeft` → 탈퇴 유예 처리.
- 탈퇴는 7일 소프트 삭제: 즉시 해당 멤버 방문에 `hidden_at`, 선점은 다음 체크인 멤버에게 이전(`ClaimTransferred`). 7일 내 재가입 시 방문 복구(선점은 안 돌아옴), 7일 후 배치가 하드 삭제.
- Territory와 분리한 이유: 멤버 가입·탈퇴가 방문 250건과 같은 락을 잡을 이유가 없다. 체크인은 멤버 여부만 읽기 참조로 확인.
- 생성 시 예외: Explorer와 개인 ExpeditionMap(+territory 행)은 한 트랜잭션에서 함께 생성한다(모두 신규 행이라 잠금 경합이 없고, 개인 지도 없는 탐험가를 막기 위한 원자성이 필요). "커맨드 하나가 애그리거트 둘을 수정" 금지 규칙의 유일한 예외.

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
| expedition_map | ExpeditionMap | id PK | name, country, invite_code UNIQUE, owner_id FK |
| map_member | ExpeditionMap | PK(map_id, explorer_id) | role, joined_at, ≤4 |
| explorer | (계정 루트) | id PK | handle UNIQUE, created_at. 가입 시 개인 지도 자동 생성 |
| visit | Territory | UQ(map_id, region_code, checked_in_by) | map_id FK, checked_in_by FK, verification, visit_date, memo, photo_url, hidden_at. 취소는 물리 삭제 + 이벤트 |
| explorer_progress | ExplorerProgress | explorer_id PK/FK | xp, level, title_id, streak_months, streak_last_month, version(낙관적 락) |
| xp_ledger | ExplorerProgress | id | source, amount, ref_id UNIQUE(멱등), created_at |
| badge_earned | ExplorerProgress | PK(explorer_id, badge_id) | earned_at |
| set_progress | Collection | PK(map_id, set_id) | collected_codes JSON, completed_at NULL — **지도 단위** |
| quest_progress | QuestBoard | PK(explorer_id, year_month, quest_id) | current, claimed_at. 상시 도전은 year_month='ALL' |
| owned_item | Inventory | PK(explorer_id, item_id) | source(REGION\|SET_REWARD\|EVENT), acquired_at, map_id NULL, favorite |
| scene | Scene | explorer_id PK/FK | gender, slots JSON, props JSON(≤3), version |
| friendship | Friendship | PK(from_id, to_id) | created_at |
| feed_entry | 읽기 모델 | id PK | actor_id, map_id, kind, payload JSON, IDX(actor_id, created_at) |
| share_card | ShareCard | PK(explorer_id, map_id, kind) | image_url, scene_ver, visit_ver |
| explorer_region | 읽기 모델(진행) | (explorer_id, region_code) | first_visited_at — 전체 랭킹·상위%·도감 뱃지용 |
| region_stats / rank_percentile | 집계 | — | 일 1회 배치 → Redis |
| outbox | 공통 | id PK | aggregate, event_type, payload JSON, published_at. FK 없음 |
| item_definition | 참조(운영 추가) | item_id PK | slot, tier, look, grant_rule JSON, valid_from/to — 3단계에서 DB로 |

- 애그리거트 경계를 넘는 FK는 두지 않는다(예: scene.slots → owned_item 금지).
- gender는 Scene이 바꾸는 값이므로 scene 테이블에 둔다(explorer 아님).
- collected_codes·slots·props는 조회 조건이 아니므로 JSON 컬럼. 필요해지면 읽기 모델로 펼친다.
- explorer_progress와 scene은 1:1이지만 변경 주체(이벤트 vs 사용자)와 낙관적 락 범위를 분리하려고 떨어뜨렸다.

## 5. 공유 지도·랭킹·XP 규칙

- 공유 지도는 협력이 아니라 **경쟁**. 같은 지역을 멤버가 각자 체크인할 수 있고, 지역 색은 선점자(최초 체크인 멤버) 색.
- **랭킹 두 층, 다른 집계**: 지도 안 랭킹은 visit을 (map_id, checked_in_by)로 센다. 전체·친구 랭킹은 탐험가별 **중복 제거한 지역 수**(explorer_region) — 여러 지도에서 같은 지역을 찍어도 1. 탈퇴로 지도 visit이 삭제돼도 explorer_region은 남아 전체 랭킹은 줄지 않는다.
- XP: 기본 XP는 탐험가당 지역당 한 번(`ref_id = region:{explorerId}:{code}`), 선점 보너스 +10은 지도마다·수령자마다(`claim:{mapId}:{code}:{explorerId}` — 수령자를 포함해야 선점 이전 시 새 선점자의 ref_id가 달라진다). 선점 이전 시 새 선점자에게도 +10(`ClaimTransferred`). 떠난 사람 보너스는 회수 안 함.
- 친구 랭킹은 요청 시점 조인 계산(친구 수 적음). 시·도별/전국 정복률은 Territory 로드 후 메모리 계산(250건). 지역별 방문자 비율·상위 N%는 일 1회 배치 → Redis.

## 6. 단계별 진행 순서

| 단계 | 범위 | 산출물 | 목표일 |
|------|------|--------|--------|
| 0 | 뼈대 | 멀티모듈 스캐폴드 (scaffold-multimodule 스킬) | — |
| 1 | 카탈로그 + 탐험 | Flyway V1(explorer, **expedition_map, map_member**(개인 지도 자동 생성에 필요한 최소), visit, outbox) + app-api에 flyway 의존성 추가·local도 Flyway 전환, `POST /visits`, `DELETE /visits/{code}`, `GET /territory`, CheckInPreview, 지역 250개 JSON·GeoJSON | 2026-10-13 |
| 2 | 진행 | V2(explorer_progress, xp_ledger, badge_earned, set_progress, quest_progress), 이벤트 핸들러 3개, 재계산 배치, XP 공식·뱃지 12·세트 9·퀘스트 정의 | 2026-10-27 |
| 3 | 꾸미기 | V3(owned_item, scene, item_definition), `PUT /scene`, 자동 착용 핸들러, 아이템 정의 DB화 + `POST /admin/items`, 지도 설정·초대·탈퇴 유예 전체 기능(MapSettings, disputed, hidden_at) | 2026-11-10 |
| 4 | 공유 | 공개 프로필 `/u/{handle}`, OG 카드 렌더러(lazy), 스냅샷 저장·무효화 | 2026-11-24 |
| 5 | 소셜 | V4(friendship, feed_entry), 피드·랭킹 프로젝터, VS 비교 쿼리, 상위 % 배치 | 2026-12-08 |

1단계 이후: 익명 탐험가 → 계정 연결 병합 커맨드(`claimExplorer`, explorer 레벨). Kafka는 5단계까지 불필요.

## 7. 리스크 대응으로 확정된 규칙

- 소급 금지는 스트릭·월간 퀘스트에만. 영토·아이템·세트·리캡은 과거 날짜 인정(초기 유입 = "예전에 간 곳 채우기").
- 치팅 대응: 사진 필수 옵션, 하루 상한 5, disputed 플래그, 전체 랭킹은 참고용(상위 % 표시)으로 약하게.
- 콘텐츠: 계절 한정 세트, 재방문 도장(같은 지역 2회차부터 카운트), 세계 확장은 시즌 2.
- 아이템 희석 방지: 일반 아이템은 키링 슬롯(3개)으로, 진열은 희귀·전설·세트·이슈 중심, favorite 플래그. 합성·강화 없음.
- 프라이버시: 메모·사진 기본 비공개, 공개 프로필은 색칠·집계만, 날짜는 월 단위 반올림, 공개 범위 설정(전체·친구·비공개).
- 행정구역 개편: Region에 version·replacedBy·retiredAt. 폐지 코드는 숨김 처리, 세트는 대체 코드로 재평가.
- 상호·상표 금지: 아이템명은 일반명사화("대전 튀김소보로").
- 이슈 아이템 지급 규칙은 3종만(기간 내 체크인 / 특정 시·도 / 세트 완성). 규칙 엔진 안 만든다.
