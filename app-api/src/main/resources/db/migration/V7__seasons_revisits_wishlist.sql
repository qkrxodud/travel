-- 9단계(게임 요소 2순위) — 계절 한정 테마·재방문 도장·가고 싶은 곳(위시리스트) + 계절 회차 배경(이관 데이터).
-- V1~V6 은 커밋 이후라 고치지 않는다. MySQL 문법 기준, 로컬 H2(MODE=MySQL)에서도 그대로 돈다. 애그리거트 경계를 넘는 FK 는 두지 않는다.

-- 계절 한정 테마 회차 진행(진행 CollectionBook 의 자식 — set_progress 와 같은 지도 단위).
--   round_id             = {계절}-{연도}(예 autumn-2026)
--   marks                = 회차 기간 안에 처리돼 센 방문 "KR-xxxxx|explorerId" 쉼표 구분(기간 밖에 칠한 방문은 없다 — 소급 없음)
--   completed_at         = 처음 완성된 시각(취소해도 유지), completed_member_ids = 완성 시점 멤버(+XP·칭호·배경 수령자)
-- 회차가 끝나도 지우지 않는다(닫힌 기록 — 미완성 진행도 남는다). 재계산은 닫히지 않은 회차만 다시 센다.
CREATE TABLE season_progress (
    map_id               VARCHAR(36)   NOT NULL,
    round_id             VARCHAR(20)   NOT NULL,
    marks                VARCHAR(2000) NOT NULL,
    completed_at         DATETIME(6)   NULL,
    completed_member_ids VARCHAR(400)  NULL,
    version              BIGINT        NOT NULL,
    CONSTRAINT pk_season_progress PRIMARY KEY (map_id, round_id)
);

-- 재방문 도장(탐험 StampBook — 탐험가 단위). 지역·연도당 하나(PK), 지우지 않는다(취소 없음). 연도 컬럼은 예약어를 피해 stamp_year.
CREATE TABLE revisit_stamp (
    explorer_id VARCHAR(36) NOT NULL,
    region_code VARCHAR(10) NOT NULL,
    stamp_year  INT         NOT NULL,
    stamped_at  DATETIME(6) NOT NULL,
    CONSTRAINT pk_revisit_stamp PRIMARY KEY (explorer_id, region_code, stamp_year)
);
CREATE INDEX idx_revisit_stamp_day ON revisit_stamp (explorer_id, stamped_at);

-- 가고 싶은 곳(탐험 Wishlist — 탐험가 단위 계획, 비공개). wishlist = 루트 행(핀 꽂기·다녀옴 처리의 직렬화 잠금 대상, 처음 꽂을 때 생긴다),
-- wish_pin = 핀(지역당 하나). fulfilled_at 이 있으면 다녀옴(핀을 꽂은 뒤 그 지역을 칠함).
CREATE TABLE wishlist (
    explorer_id VARCHAR(36) NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    CONSTRAINT pk_wishlist PRIMARY KEY (explorer_id)
);

CREATE TABLE wish_pin (
    explorer_id  VARCHAR(36) NOT NULL,
    region_code  VARCHAR(10) NOT NULL,
    pinned_at    DATETIME(6) NOT NULL,
    fulfilled_at DATETIME(6) NULL,
    CONSTRAINT pk_wish_pin PRIMARY KEY (explorer_id, region_code)
);

-- 재방문 도장을 받은 지역(꾸미기 Inventory 의 자식) — 그 지역 특산물을 2회차 색 변형으로 그린다(아이템 정의를 늘리지 않는다). 지우지 않는다.
CREATE TABLE inventory_revisit (
    explorer_id VARCHAR(36) NOT NULL,
    region_code VARCHAR(10) NOT NULL,
    marked_at   DATETIME(6) NOT NULL,
    CONSTRAINT pk_inventory_revisit PRIMARY KEY (explorer_id, region_code)
);

-- 계절 회차 배경(SEASON_COMPLETE, grant_ref = 회차 id). 회차마다 연도를 붙인 배경 — autumn-2026 부터 2030 년까지 미리 넣는다
-- (그 뒤 회차는 운영이 POST /admin/items 로 SEASON_COMPLETE 규칙 아이템을 더한다). season: 접두어는 이관 전용(dev 초기화가 남긴다).
INSERT INTO item_definition (item_id, name, emoji, slot, tier, theme, look, color_primary, color_secondary, grant_rule, grant_ref,
                             valid_from, valid_to, created_at) VALUES
('season:autumn-2026', '2026 단풍 명소 배경', '🍁', 'BG', 'LEGEND', 'autumn', NULL, NULL, NULL, 'SEASON_COMPLETE', 'autumn-2026', NULL, NULL, '2026-10-04 00:00:00'),
('season:spring-2027', '2027 벚꽃 명소 배경', '🌸', 'BG', 'LEGEND', 'blossom', NULL, NULL, NULL, 'SEASON_COMPLETE', 'spring-2027', NULL, NULL, '2026-10-04 00:00:00'),
('season:autumn-2027', '2027 단풍 명소 배경', '🍁', 'BG', 'LEGEND', 'autumn', NULL, NULL, NULL, 'SEASON_COMPLETE', 'autumn-2027', NULL, NULL, '2026-10-04 00:00:00'),
('season:spring-2028', '2028 벚꽃 명소 배경', '🌸', 'BG', 'LEGEND', 'blossom', NULL, NULL, NULL, 'SEASON_COMPLETE', 'spring-2028', NULL, NULL, '2026-10-04 00:00:00'),
('season:autumn-2028', '2028 단풍 명소 배경', '🍁', 'BG', 'LEGEND', 'autumn', NULL, NULL, NULL, 'SEASON_COMPLETE', 'autumn-2028', NULL, NULL, '2026-10-04 00:00:00'),
('season:spring-2029', '2029 벚꽃 명소 배경', '🌸', 'BG', 'LEGEND', 'blossom', NULL, NULL, NULL, 'SEASON_COMPLETE', 'spring-2029', NULL, NULL, '2026-10-04 00:00:00'),
('season:autumn-2029', '2029 단풍 명소 배경', '🍁', 'BG', 'LEGEND', 'autumn', NULL, NULL, NULL, 'SEASON_COMPLETE', 'autumn-2029', NULL, NULL, '2026-10-04 00:00:00'),
('season:spring-2030', '2030 벚꽃 명소 배경', '🌸', 'BG', 'LEGEND', 'blossom', NULL, NULL, NULL, 'SEASON_COMPLETE', 'spring-2030', NULL, NULL, '2026-10-04 00:00:00'),
('season:autumn-2030', '2030 단풍 명소 배경', '🍁', 'BG', 'LEGEND', 'autumn', NULL, NULL, NULL, 'SEASON_COMPLETE', 'autumn-2030', NULL, NULL, '2026-10-04 00:00:00');
