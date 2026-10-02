-- 2단계(진행). ExplorerProgress · Collection(지도 단위) · QuestBoard + 구독자별 outbox 전달 기록.
-- MySQL 문법 기준이며 로컬 H2(MODE=MySQL)에서도 그대로 돈다. JSON 성격 컬럼(집합)은 V1 outbox 와 같은 이유로
-- VARCHAR 쉼표 구분 문자열로 둔다(H2·MySQL validate 호환, 조회 조건이 아님).
-- 애그리거트 경계를 넘는 FK는 두지 않는다 — explorer_progress 만 계정 루트(explorer)를 가리킨다(§4 explorer_id PK/FK).

-- 진행 루트. xp·level 은 장부 합계·결정적 함수의 저장 값(조회용). title_id = 선택 칭호(NULL 이면 레벨 칭호 표시).
CREATE TABLE explorer_progress (
    explorer_id       VARCHAR(36) NOT NULL,
    xp                BIGINT      NOT NULL,
    level             INT         NOT NULL,
    title_id          VARCHAR(40) NULL,
    streak_months     INT         NOT NULL,
    streak_last_month VARCHAR(7)  NULL,
    version           BIGINT      NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    CONSTRAINT pk_explorer_progress PRIMARY KEY (explorer_id),
    CONSTRAINT fk_explorer_progress_explorer FOREIGN KEY (explorer_id) REFERENCES explorer (id)
);

-- XP 장부(감소는 음수 항목). ref_id UNIQUE 가 멱등 키:
--   region:{e}:{code}#{k} / region:{e}:{code}#{k}:revoke, province:{e}:{prov}, claim:{map}:{code}:{e},
--   set:{e}:{setId}, quest:{e}:{period}:{questId}
CREATE TABLE xp_ledger (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    explorer_id VARCHAR(36)  NOT NULL,
    source      VARCHAR(20)  NOT NULL,
    amount      INT          NOT NULL,
    ref_id      VARCHAR(160) NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    CONSTRAINT pk_xp_ledger PRIMARY KEY (id),
    CONSTRAINT uq_xp_ledger_ref UNIQUE (ref_id)
);
CREATE INDEX idx_xp_ledger_explorer ON xp_ledger (explorer_id, id);

-- 뱃지(추가만).
CREATE TABLE badge_earned (
    explorer_id VARCHAR(36) NOT NULL,
    badge_id    VARCHAR(20) NOT NULL,
    earned_at   DATETIME(6) NOT NULL,
    CONSTRAINT pk_badge_earned PRIMARY KEY (explorer_id, badge_id)
);

-- 칭호(추가만). §4 에 없는 테이블 — 칭호도 뱃지처럼 한 번 얻으면 회수하지 않아 기록이 필요하다(선택 칭호 검증).
CREATE TABLE title_earned (
    explorer_id VARCHAR(36) NOT NULL,
    title_id    VARCHAR(40) NOT NULL,
    earned_at   DATETIME(6) NOT NULL,
    CONSTRAINT pk_title_earned PRIMARY KEY (explorer_id, title_id)
);

-- 탐험가 단위 지역(읽기 모델 겸 기본 XP 회수 판단, D2). 여러 지도에서 같은 지역이어도 한 행.
-- active_map_count = 지금 방문이 살아 있는 지도 수(체크인 +1, 취소 −1, 0이면 기본 XP 회수; 탈퇴는 줄이지 않음).
-- active_map_ids 는 그 지도 id 집합 — 같은 이벤트가 두 번 와도 +1/−1 이 두 번 되지 않게(멱등) 둔다.
CREATE TABLE explorer_region (
    explorer_id      VARCHAR(36)   NOT NULL,
    region_code      VARCHAR(10)   NOT NULL,
    province_code    VARCHAR(5)    NOT NULL,
    rarity           VARCHAR(8)    NOT NULL,
    first_visited_at DATETIME(6)   NOT NULL,
    active_map_count INT           NOT NULL,
    active_map_ids   VARCHAR(2000) NOT NULL,
    CONSTRAINT pk_explorer_region PRIMARY KEY (explorer_id, region_code)
);
CREATE INDEX idx_explorer_region_active ON explorer_region (explorer_id, active_map_count);

-- 도감(지도 단위). collected_codes = 지금 지도에 칠해진 세트 지역, completed_at = 처음 완성 시각(취소해도 유지).
CREATE TABLE set_progress (
    map_id          VARCHAR(36)   NOT NULL,
    set_id          VARCHAR(20)   NOT NULL,
    collected_codes VARCHAR(1000) NOT NULL,
    completed_at    DATETIME(6)   NULL,
    CONSTRAINT pk_set_progress PRIMARY KEY (map_id, set_id)
);

-- 퀘스트 보드. quest_period = 'yyyy-MM'(월간) | 'ALL'(상시 도전). §4 의 year_month 는 MySQL 예약어(YEAR_MONTH)라 이름을 바꿨다.
-- tally = 센 지역 키("시·도|지역") 집합(같은 지역 중복 집계 방지), current_count = 센 지역 수(조회 편의),
-- claimed_at = 보상 받은 시각(1회), version = 동시 보상 받기 방지(낙관적 락).
CREATE TABLE quest_progress (
    explorer_id   VARCHAR(36)   NOT NULL,
    quest_period  VARCHAR(7)    NOT NULL,
    quest_id      VARCHAR(20)   NOT NULL,
    current_count INT           NOT NULL,
    tally         VARCHAR(2000) NOT NULL,
    claimed_at    DATETIME(6)   NULL,
    version       BIGINT        NOT NULL,
    CONSTRAINT pk_quest_progress PRIMARY KEY (explorer_id, quest_period, quest_id)
);

-- outbox 구독자별 전달 기록(D5, QA P1-1·P1-2). 구독자 = 소비 애그리거트 하나(예: progression.progress).
-- 구독자 처리와 DELIVERED 기록은 같은 트랜잭션. 순서 단위는 (outbox.aggregate_id, subscriber) — 앞 이벤트가 DELIVERED 가
-- 아니면 뒤 이벤트를 보내지 않는다(head-of-line). 낙관적 락 충돌은 conflicts 만 올리고 지수 백오프로 계속 재시도,
-- 그 밖의 실패는 attempts+1(백오프), 상한 도달 시 FAILED — 그 단위는 재전달(redeliver) 전까지 멈춘다.
CREATE TABLE outbox_delivery (
    event_id        BIGINT        NOT NULL,
    subscriber      VARCHAR(80)   NOT NULL,
    status          VARCHAR(12)   NOT NULL,
    attempts        INT           NOT NULL,
    conflicts       INT           NOT NULL,
    next_attempt_at DATETIME(6)   NULL,
    last_error      VARCHAR(1000) NULL,
    delivered_at    DATETIME(6)   NULL,
    updated_at      DATETIME(6)   NOT NULL,
    CONSTRAINT pk_outbox_delivery PRIMARY KEY (event_id, subscriber)
);
CREATE INDEX idx_outbox_delivery_status ON outbox_delivery (status, subscriber);
