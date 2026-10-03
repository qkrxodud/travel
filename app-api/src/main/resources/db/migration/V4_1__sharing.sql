-- 4단계(공유) — 파트 B. 자랑 카드(ShareCard)·공개 범위(PrivacySettings) + 초대 보상(꾸미기 Inventory 자식).
-- 계정·병합(파트 A)은 V4__account.sql. V3·V3_1 은 커밋 이후라 고치지 않는다. MySQL 문법 기준, 로컬 H2(MODE=MySQL)에서도 그대로 돈다.
-- 애그리거트 경계를 넘는 FK 는 두지 않는다. 공유 테이블은 explorer FK 도 두지 않는다 — 카드·설정은 캐시·선택값이라
-- 탐험가 정리(병합 후 비활성 등) 순서에 묶이지 않게.

-- 자랑 카드(§2-8 ShareCard). PK(explorer_id, map_id, kind)(§4, 지도 = 개인 지도). VS 카드는 저장하지 않는다(메모리 캐시).
--   summary_hash    = 그릴 때의 공개 요약(Showcase + 종류 + 연도) SHA-256 — §4 초안의 scene_ver·visit_ver 대체(QA P2-1: 이벤트를
--                     놓쳐도 실제 그리는 값이 바뀌면 낡음으로 잡힌다)
--   rendered_handle = 카드에 찍힌 handle(익명이면 NULL) — 바뀌면 최소 TTL 을 건너뛰고 다시 그린다(QA P2-2)
--   image_key       = 카드 이미지 저장소 키(기준 해시 포함 — 같은 기준의 이미지만 기록, QA P3-4). 캐시라 잃어도 다시 그린다
--   rendered_at     = 마지막 렌더 시각(최소 TTL territory.share-card.cache-ttl-minutes 판단)
CREATE TABLE share_card (
    explorer_id     VARCHAR(36)  NOT NULL,
    map_id          VARCHAR(36)  NOT NULL,
    kind            VARCHAR(12)  NOT NULL,
    summary_hash    VARCHAR(64)  NOT NULL,
    rendered_handle VARCHAR(30)  NULL,
    image_key       VARCHAR(200) NOT NULL,
    rendered_at     DATETIME(6)  NOT NULL,
    version         BIGINT       NOT NULL,
    CONSTRAINT pk_share_card PRIMARY KEY (explorer_id, map_id, kind)
);

-- 공개 범위(§7 — 전체·친구·비공개). 행이 없으면 PRIVATE(사용자 결정 Q1 — 프로필 탭에서 "공개하기"를 켜야 열린다). FRIENDS 는 5단계 친구 기능 전까지 PRIVATE 처럼 동작(공개 프로필 404).
CREATE TABLE privacy_settings (
    explorer_id VARCHAR(36) NOT NULL,
    visibility  VARCHAR(8)  NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    version     BIGINT      NOT NULL,
    CONSTRAINT pk_privacy_settings PRIMARY KEY (explorer_id)
);

-- 초대 보상 기록(꾸미기 Inventory 의 자식 — 초대받은 쪽 Inventory 가 판단). PK(초대받은 쪽, 초대한 쪽) = 같은 쌍 1회.
-- 행은 지우지 않는다(보상은 회수 없음, 재계산도 유지). 초대한 쪽 보상은 InviteRewardOwed 이벤트로 그 사람 Inventory 가 받는다.
CREATE TABLE invite_reward (
    invitee_id  VARCHAR(36) NOT NULL,
    inviter_id  VARCHAR(36) NOT NULL,
    map_id      VARCHAR(36) NOT NULL,
    rewarded_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_invite_reward PRIMARY KEY (invitee_id, inviter_id)
);

-- 초대 보상 한정 아이템(EVENT 출처, 회수 없음). grant_rule = INVITATION, grant_ref = HOST(초대한 쪽) | GUEST(초대받은 쪽).
-- invite: 접두어는 이관 전용(운영 추가 금지 — dev 초기화가 운영 추가분만 지울 때 남는다). 기간을 닫으려면 valid_to 를 정한다.
INSERT INTO item_definition (item_id, name, emoji, slot, tier, theme, look, color_primary, color_secondary, grant_rule, grant_ref,
                             valid_from, valid_to, created_at) VALUES
('invite:host-flag', '길잡이 깃발', '🚩', 'HAND', 'RARE', NULL, 'flag', '#e63946', '#f4c542', 'INVITATION', 'HOST',
 '2026-10-01', NULL, '2026-10-03 00:00:00'),
('invite:guest-ticket', '동행 티켓 키링', '🎫', 'BADGE', 'RARE', NULL, 'keyring', '#2fc3ad', '#f7f3ea', 'INVITATION', 'GUEST',
 '2026-10-01', NULL, '2026-10-03 00:00:00');
