-- 12단계: 웹 푸시 알림(알림 컨텍스트 — 게임 테이블을 참조하지 않는다, FK 없음) + 10단계 QA r2 P3-c(하루 지표의 부분 구간 표시).
-- 시각은 UTC DATETIME(6), 날짜(delivery_day)는 서울 날짜.

-- 알림 받는 사람(루트 — 구독·해지·설정·발송 계획을 탐험가 단위로 줄 세우는 잠금 대상). 종류별 켜고 끄기, 처음엔 모두 켜짐.
CREATE TABLE push_recipient (
    explorer_id     VARCHAR(36) NOT NULL,
    mystery_enabled BOOLEAN     NOT NULL,
    streak_enabled  BOOLEAN     NOT NULL,
    season_enabled  BOOLEAN     NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    PRIMARY KEY (explorer_id)
);

-- 기기(브라우저 구독 하나) — 열쇠는 구독 주소의 SHA-256(주소가 길어서). 한 브라우저 구독은 한 탐험가에게만.
CREATE TABLE push_device (
    endpoint_hash VARCHAR(64)   NOT NULL,
    explorer_id   VARCHAR(36)   NOT NULL,
    endpoint      VARCHAR(1024) NOT NULL,
    p256dh        VARCHAR(128)  NOT NULL,
    auth          VARCHAR(64)   NOT NULL,
    registered_at DATETIME(6)   NOT NULL,
    PRIMARY KEY (endpoint_hash)
);
CREATE INDEX ix_push_device_explorer ON push_device (explorer_id);

-- 발송 기록 — 멱등 열쇠(탐험가·종류·기간), 하루 최대 개수(탐험가·보낼 날), 발송기의 보낼 때가 된 기록 찾기(상태·다음 시도 시각).
CREATE TABLE push_delivery (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    explorer_id       VARCHAR(36)  NOT NULL,
    kind              VARCHAR(20)  NOT NULL,           -- mystery | streak | season
    period            VARCHAR(40)  NOT NULL,           -- 2026-10-05 | 2026-10 | autumn-2026
    delivery_day      DATE         NOT NULL,
    status            VARCHAR(12)  NOT NULL,           -- PENDING | SENDING | SENT | FAILED | EXPIRED | CANCELLED
    title             VARCHAR(80)  NOT NULL,
    body              VARCHAR(240) NOT NULL,
    url               VARCHAR(200) NOT NULL,
    tag               VARCHAR(64)  NOT NULL,
    due_at            DATETIME(6)  NOT NULL,
    immediate         BOOLEAN      NOT NULL,           -- local 즉시 발송(조용한 시간에도 보냄)
    next_attempt_at   DATETIME(6)  NOT NULL,
    attempts          INT          NOT NULL,
    claimed_at        DATETIME(6)  NULL,
    sent_at           DATETIME(6)  NULL,
    delivered_devices INT          NOT NULL,
    last_error        VARCHAR(200) NULL,
    created_at        DATETIME(6)  NOT NULL,
    version           BIGINT       NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_push_delivery_key UNIQUE (explorer_id, kind, period)
);
CREATE INDEX ix_push_delivery_day ON push_delivery (explorer_id, delivery_day);
CREATE INDEX ix_push_delivery_due ON push_delivery (status, next_attempt_at);

-- 10단계 QA r2 P3-c: 하루 지표를 계산할 때 그날로 끝나는 30일 구간(MAU·K 계수)의 앞부분 원본이 이미 보관 기간을 지나 지워졌으면 TRUE
-- (값이 작게 나온다 — 화면은 "신뢰도 낮음"으로 표시).
ALTER TABLE analytics_daily ADD COLUMN partial_window BOOLEAN NOT NULL DEFAULT FALSE;
