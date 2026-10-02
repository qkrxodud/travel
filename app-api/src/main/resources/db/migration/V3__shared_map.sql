-- 3단계(파트 A): 공유 지도 · 인증 토큰 · 방문 회차 · 테마 완성 수령자 · outbox 충돌 시간 상한.
-- 파트 B(꾸미기)는 V3_1__wardrobe.sql. MySQL 문법 기준이며 로컬 H2(MODE=MySQL)에서도 그대로 돈다(ALTER 는 한 문장에 하나씩).

-- 탐험가 비밀 접근 토큰(결정 2) — SHA-256 hex 만 저장한다. 이전에 만든 탐험가는 NULL(토큰 인증 불가 → 다시 발급).
ALTER TABLE explorer ADD COLUMN access_token_hash VARCHAR(64) NULL;
CREATE UNIQUE INDEX uq_explorer_access_token ON explorer (access_token_hash);

-- 지도 커맨드 동시성(QA P1-1): 지도 행 version — 커맨드는 행을 잠글 때 강제 증가한다.
ALTER TABLE expedition_map ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- 지도 멤버 탈퇴 유예(§2-9): left_at 이 있으면 유예 중(멤버 아님). 유예가 끝나면 배치가 행을 지운다.
ALTER TABLE map_member ADD COLUMN left_at DATETIME(6) NULL;
CREATE INDEX idx_map_member_left_at ON map_member (left_at);

-- 방문(Territory):
--   generation    = 같은 (지도, 지역, 멤버)의 체크인 회차(결정 6). 기존 행은 1회차.
--   claim_rank_at = 선점 순서 기준(재가입 복구된 방문은 복구 시각 — 넘어간 선점이 돌아오지 않게). NULL 이면 visited_at.
--   hidden_at     = 탈퇴 유예로 숨김(지도·집계 제외), disputed = 지도장 이의(지도 내 랭킹 제외, 5단계).
ALTER TABLE visit ADD COLUMN generation INT NOT NULL DEFAULT 1;
ALTER TABLE visit ADD COLUMN claim_rank_at DATETIME(6) NULL;
ALTER TABLE visit ADD COLUMN hidden_at DATETIME(6) NULL;
ALTER TABLE visit ADD COLUMN disputed BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE visit SET claim_rank_at = visited_at WHERE claim_rank_at IS NULL;
CREATE INDEX idx_visit_member_region ON visit (checked_in_by, region_code);

-- 방문 회차 기록(Territory 의 자식). 방문은 취소하면 물리 삭제되므로 회차는 따로 남긴다.
CREATE TABLE visit_generation (
    map_id          VARCHAR(36) NOT NULL,
    region_code     VARCHAR(10) NOT NULL,
    explorer_id     VARCHAR(36) NOT NULL,
    last_generation INT         NOT NULL,
    CONSTRAINT pk_visit_generation PRIMARY KEY (map_id, region_code, explorer_id),
    CONSTRAINT fk_visit_generation_map FOREIGN KEY (map_id) REFERENCES expedition_map (id)
);
INSERT INTO visit_generation (map_id, region_code, explorer_id, last_generation)
    SELECT map_id, region_code, checked_in_by, generation FROM visit;

-- 탐험가 단위 지역의 지도별 마지막 방문 회차(결정 6): mark 양수 g = g회차 체크인, 음수 −g = g회차 취소.
-- 오래된 회차 이벤트(늦게 온 재전달)를 무시하는 근거. 지도마다 한 행이라 한 지역에 지도가 많아도 넘치지 않는다(QA P3-7).
CREATE TABLE explorer_region_mark (
    explorer_id VARCHAR(36) NOT NULL,
    region_code VARCHAR(10) NOT NULL,
    map_id      VARCHAR(36) NOT NULL,
    mark        INT         NOT NULL,
    CONSTRAINT pk_explorer_region_mark PRIMARY KEY (explorer_id, region_code, map_id)
);

-- 도감 테마 완성 수령자(결정 1): 완성 시점 지도 멤버(쉼표 구분). 2단계 완성 기록은 개인 지도뿐이라 그 지도의 멤버(1명)로 채운다.
ALTER TABLE set_progress ADD COLUMN completed_member_ids VARCHAR(400) NULL;
UPDATE set_progress SET completed_member_ids =
    (SELECT MIN(member.explorer_id) FROM map_member member WHERE member.map_id = set_progress.map_id)
    WHERE completed_at IS NOT NULL;

-- outbox: 낙관적 락 충돌 재시도 시간 상한(결정 5) — 연속 충돌의 첫 시각.
ALTER TABLE outbox_delivery ADD COLUMN first_conflict_at DATETIME(6) NULL;
-- 재계산 보류 판단(S3-3): 탐험가·지도 단위 미발행 이벤트 조회.
CREATE INDEX idx_outbox_aggregate_unpublished ON outbox (aggregate_id, published_at);
