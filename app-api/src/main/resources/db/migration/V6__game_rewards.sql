-- 8단계(게임 요소 1순위) — 보호권 장부·미스터리 지역 선택 기록 + 시·도 정복·연속 탐험 마일스톤 한정 아이템(이관 데이터).
-- V1~V5 는 커밋 이후라 고치지 않는다. MySQL 문법 기준, 로컬 H2(MODE=MySQL)에서도 그대로 돈다.
-- 애그리거트 경계를 넘는 FK 는 두지 않는다.

-- 보호권 장부(진행 ExplorerProgress 의 자식, xp_ledger 와 같은 방식). 보유 수 = amount 합계.
--   ref_id     = 멱등 키(UNIQUE): 월간 퀘스트 완주 freeze:{e}:quests:{yyyy-MM}, 마일스톤 freeze:{e}:milestone:{n}, 사용 freeze:{e}:use:{yyyy-MM}
--   amount     = 받은 줄 양수(보유 상한에 막히면 0 — 받을 일이 있었음을 남겨 재전달·재계산이 다시 주지 않게), 쓴 줄 음수(빈 달 수)
--   used_month = 쓴 줄이면 연속을 이은 달(yyyy-MM), 받은 줄은 NULL
-- 재계산은 이 탐험가의 행을 지우고 장부의 보상(마일스톤 XP·월간 퀘스트 XP)과 체크인을 시간 순으로 섞어 다시 넣는다.
CREATE TABLE streak_freeze (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    explorer_id VARCHAR(36)  NOT NULL,
    ref_id      VARCHAR(160) NOT NULL,
    reason      VARCHAR(20)  NOT NULL,
    amount      INT          NOT NULL,
    used_month  VARCHAR(7)   NULL,
    created_at  DATETIME(6)  NOT NULL,
    CONSTRAINT pk_streak_freeze PRIMARY KEY (id),
    CONSTRAINT uq_streak_freeze_ref UNIQUE (ref_id)
);
CREATE INDEX idx_streak_freeze_explorer ON streak_freeze (explorer_id, id);

-- 이번 주 미스터리 지역 선택 기록(카탈로그 소유 — 전체 사용자 공통 참조 데이터). 한 주 한 행, 넣은 뒤 바꾸지 않는다.
--   week_start = 그 주 월요일(Asia/Seoul) — PK 가 "같은 주는 누구에게나 같은 지역"을 지킨다(동시에 고르면 한쪽이 PK 충돌 후 기록을 읽음)
--   지난 주는 나중에 고르지 않는다(소급 없음). 탐험가 데이터가 아니라 dev 초기화(DELETE /dev/reset)도 지우지 않는다.
CREATE TABLE mystery_week (
    week_start  DATE        NOT NULL,
    region_code VARCHAR(10) NOT NULL,
    selected_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_mystery_week PRIMARY KEY (week_start)
);

-- 시·도 정복 대표 장식 17종(PROVINCE_COMPLETE, grant_ref = 시·도 코드)과 연속 탐험 마일스톤 한정 아이템 4종(STREAK_MILESTONE,
-- grant_ref = 개월 수). EVENT 출처·회수 없음. conquest:·streak: 접두어는 이관 전용(운영 추가 금지 — dev 초기화가 운영 추가분만 지울 때 남는다).
INSERT INTO item_definition (item_id, name, emoji, slot, tier, theme, look, color_primary, color_secondary, grant_rule, grant_ref,
                             valid_from, valid_to, created_at) VALUES
('conquest:KR-11', '서울 정복 남산 기념비', '🗼', 'PROP', 'LEGEND', NULL, 'statue', '#e63946', '#f4c542', 'PROVINCE_COMPLETE', 'KR-11', NULL, NULL, '2026-10-04 00:00:00'),
('conquest:KR-31', '경기 정복 수원 성곽 탑', '🏯', 'PROP', 'LEGEND', NULL, 'statue', '#8d5524', '#f4c542', 'PROVINCE_COMPLETE', 'KR-31', NULL, NULL, '2026-10-04 00:00:00'),
('conquest:KR-23', '인천 정복 등대', '🗼', 'PROP', 'LEGEND', NULL, 'candle', '#2fc3ad', '#f7f3ea', 'PROVINCE_COMPLETE', 'KR-23', NULL, NULL, '2026-10-04 00:00:00'),
('conquest:KR-32', '강원 정복 설악 소나무', '🌲', 'PROP', 'LEGEND', NULL, 'tree', '#2e7d32', '#9ccc65', 'PROVINCE_COMPLETE', 'KR-32', NULL, NULL, '2026-10-04 00:00:00'),
('conquest:KR-33', '충북 정복 단양 석문', '🪨', 'PROP', 'LEGEND', NULL, 'statue', '#6b7280', '#9ccc65', 'PROVINCE_COMPLETE', 'KR-33', NULL, NULL, '2026-10-04 00:00:00'),
('conquest:KR-34', '충남 정복 백제 금동 향로', '🏺', 'PROP', 'LEGEND', NULL, 'vase', '#f4c542', '#8d5524', 'PROVINCE_COMPLETE', 'KR-34', NULL, NULL, '2026-10-04 00:00:00'),
('conquest:KR-25', '대전 정복 과학 탑', '🗼', 'PROP', 'LEGEND', NULL, 'statue', '#2b3542', '#2fc3ad', 'PROVINCE_COMPLETE', 'KR-25', NULL, NULL, '2026-10-04 00:00:00'),
('conquest:KR-29', '세종 정복 호수 정원 나무', '🌳', 'PROP', 'LEGEND', NULL, 'tree', '#2e7d32', '#2fc3ad', 'PROVINCE_COMPLETE', 'KR-29', NULL, NULL, '2026-10-04 00:00:00'),
('conquest:KR-35', '전북 정복 한옥 등롱', '🏮', 'PROP', 'LEGEND', NULL, 'candle', '#e63946', '#f7f3ea', 'PROVINCE_COMPLETE', 'KR-35', NULL, NULL, '2026-10-04 00:00:00'),
('conquest:KR-36', '전남 정복 차밭 대나무', '🎋', 'PROP', 'LEGEND', NULL, 'bamboo', '#2e7d32', '#9ccc65', 'PROVINCE_COMPLETE', 'KR-36', NULL, NULL, '2026-10-04 00:00:00'),
('conquest:KR-24', '광주 정복 무등 청자', '🏺', 'PROP', 'LEGEND', NULL, 'vase', '#2fc3ad', '#0b6b5f', 'PROVINCE_COMPLETE', 'KR-24', NULL, NULL, '2026-10-04 00:00:00'),
('conquest:KR-37', '경북 정복 석굴 불상', '🗿', 'PROP', 'LEGEND', NULL, 'statue', '#d6c7a1', '#8d5524', 'PROVINCE_COMPLETE', 'KR-37', NULL, NULL, '2026-10-04 00:00:00'),
('conquest:KR-38', '경남 정복 가야 토기', '🏺', 'PROP', 'LEGEND', NULL, 'vase', '#8d5524', '#d6c7a1', 'PROVINCE_COMPLETE', 'KR-38', NULL, NULL, '2026-10-04 00:00:00'),
('conquest:KR-22', '대구 정복 사과나무', '🌳', 'PROP', 'LEGEND', NULL, 'tree', '#2e7d32', '#e63946', 'PROVINCE_COMPLETE', 'KR-22', NULL, NULL, '2026-10-04 00:00:00'),
('conquest:KR-21', '부산 정복 바다 파라솔', '🏖️', 'PROP', 'LEGEND', NULL, 'parasol', '#2fc3ad', '#f7f3ea', 'PROVINCE_COMPLETE', 'KR-21', NULL, NULL, '2026-10-04 00:00:00'),
('conquest:KR-26', '울산 정복 고래 등대', '🐋', 'PROP', 'LEGEND', NULL, 'candle', '#2b3542', '#2fc3ad', 'PROVINCE_COMPLETE', 'KR-26', NULL, NULL, '2026-10-04 00:00:00'),
('conquest:KR-39', '제주 정복 돌하르방', '🗿', 'PROP', 'LEGEND', NULL, 'statue', '#4b5563', '#6b7280', 'PROVINCE_COMPLETE', 'KR-39', NULL, NULL, '2026-10-04 00:00:00'),
('streak:3', '3개월 발자국 키링', '👣', 'BADGE', 'RARE', NULL, 'keyring', '#f4c542', '#8d5524', 'STREAK_MILESTONE', '3', NULL, NULL, '2026-10-04 00:00:00'),
('streak:6', '반년의 깃발', '🚩', 'HAND', 'RARE', NULL, 'flag', '#2fc3ad', '#f4c542', 'STREAK_MILESTONE', '6', NULL, NULL, '2026-10-04 00:00:00'),
('streak:12', '한 해의 별 핀', '⭐', 'HAT', 'LEGEND', NULL, 'star', '#f4c542', '#ffe9a8', 'STREAK_MILESTONE', '12', NULL, NULL, '2026-10-04 00:00:00'),
('streak:24', '두 해의 금관', '👑', 'HAT', 'LEGEND', NULL, 'crown', '#f4c542', '#e63946', 'STREAK_MILESTONE', '24', NULL, NULL, '2026-10-04 00:00:00');
