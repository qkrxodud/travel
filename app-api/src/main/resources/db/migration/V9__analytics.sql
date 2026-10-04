-- 10단계: 분석 이벤트(관찰자 — 게임 테이블을 참조하지 않는다, FK 없음).
-- 개인정보 없음: 탐험가는 서버 비밀값을 섞은 해시(HMAC-SHA256), 방문은 브라우저 무작위 ID, IP·User-Agent·handle·메모 원문 없음(기기 유형·나라만).
-- 시각은 UTC DATETIME(6), 날짜(event_day 등)는 서울 날짜.

-- 원본 이벤트(보관 90일 — 일 배치가 지운다)
CREATE TABLE analytics_event (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    name          VARCHAR(40)  NOT NULL,
    source        VARCHAR(8)   NOT NULL,           -- CLIENT | SERVER | REQUEST
    occurred_at   DATETIME(6)  NOT NULL,
    event_day     DATE         NOT NULL,
    actor_key     VARCHAR(72)  NULL,               -- 탐험가 해시 또는 'v:'+방문 ID. 누구인지 모르는 열람은 NULL
    explorer_hash CHAR(64)     NULL,
    visitor_id    VARCHAR(64)  NULL,
    device        VARCHAR(8)   NOT NULL,           -- MOBILE | TABLET | DESKTOP | BOT | UNKNOWN
    country       CHAR(2)      NULL,
    label         VARCHAR(64)  NULL,               -- 집계 갈래(탭 이름·오류 코드·합류 경로 등)
    props         VARCHAR(512) NOT NULL,           -- 검증된 필드 JSON
    dedup_key     CHAR(64)     NULL,               -- 서버 사실의 지문(재전달 멱등)
    PRIMARY KEY (id),
    CONSTRAINT uk_analytics_event_dedup UNIQUE (dedup_key)
);
-- 집계 질의가 표를 읽지 않고 인덱스만으로 끝나도록(커버링): 구간 활동 수(DAU/WAU/MAU) · 이름별 사용자·오류 코드 · 코호트의 그날 활동 ·
-- 방문을 탐험가로 다시 묶기. 보관 삭제는 event_day 로 고른다(첫 인덱스).
CREATE INDEX ix_analytics_event_day_actor ON analytics_event (event_day, actor_key, device);
CREATE INDEX ix_analytics_event_day_name ON analytics_event (event_day, name, label, actor_key, device);
CREATE INDEX ix_analytics_event_actor_day ON analytics_event (actor_key, event_day, device);
CREATE INDEX ix_analytics_event_visitor ON analytics_event (visitor_id, actor_key);

-- 방문(익명 방문 ID) — 처음 본 날·처음 들어온 길·처음 이어진 탐험가(한 번만 정해진다)
CREATE TABLE analytics_visitor (
    visitor_id     VARCHAR(64) NOT NULL,
    first_seen_at  DATETIME(6) NOT NULL,
    first_seen_day DATE        NOT NULL,
    entry          VARCHAR(8)  NULL,               -- direct | invite | profile | card | other
    explorer_hash  CHAR(64)    NULL,
    device         VARCHAR(8)  NOT NULL,
    PRIMARY KEY (visitor_id)
);
CREATE INDEX ix_analytics_visitor_first_day ON analytics_visitor (first_seen_day);
CREATE INDEX ix_analytics_visitor_explorer ON analytics_visitor (explorer_hash);

-- 탐험가 여정(탐험가 해시) — 가입일(리텐션 코호트)·첫 체크인·재방문 마감일(퍼널)·초대 합류(K 계수)
CREATE TABLE analytics_explorer (
    explorer_hash      CHAR(64) NOT NULL,
    created_day        DATE     NULL,              -- 분석을 켜기 전에 가입했으면 NULL
    first_check_in_day DATE     NULL,
    revisit_deadline   DATE     NULL,
    invited_join_day   DATE     NULL,
    invite_acquired    BOOLEAN  NOT NULL DEFAULT FALSE,
    PRIMARY KEY (explorer_hash)
);
CREATE INDEX ix_analytics_explorer_created ON analytics_explorer (created_day);

-- 하루 지표(집계 — 원본을 지워도 남는다)
CREATE TABLE analytics_daily (
    metric_day           DATE        NOT NULL,
    new_visitors         INT         NOT NULL,
    new_explorers        INT         NOT NULL,
    dau                  INT         NOT NULL,
    wau                  INT         NOT NULL,
    mau                  INT         NOT NULL,
    profile_views        INT         NOT NULL,
    card_views           INT         NOT NULL,
    bot_views            INT         NOT NULL,
    k_from               DATE        NOT NULL,
    k_to                 DATE        NOT NULL,
    k_invited_new        INT         NOT NULL,
    k_card_new           INT         NOT NULL,
    k_viral_new          INT         NOT NULL,
    k_active_explorers   INT         NOT NULL,
    feature_from         DATE        NOT NULL,
    feature_to           DATE        NOT NULL,
    feature_active_users INT         NOT NULL,
    computed_at          DATETIME(6) NOT NULL,
    PRIMARY KEY (metric_day)
);

-- 하루 지표의 갈래 값(FEATURE: 기능별 사용자 수, ERROR: 오류 코드별 횟수 — 그날로 끝나는 구간)
CREATE TABLE analytics_daily_breakdown (
    metric_day DATE        NOT NULL,
    kind       VARCHAR(8)  NOT NULL,
    name       VARCHAR(64) NOT NULL,
    ordinal    INT         NOT NULL,
    amount     INT         NOT NULL,
    PRIMARY KEY (metric_day, kind, name)
);

-- 코호트(그날 처음 본 방문의 퍼널 + 그날 가입한 탐험가의 D1/D7/D30 — 아직 셀 수 없으면 NULL)
CREATE TABLE analytics_cohort (
    cohort_day            DATE        NOT NULL,
    funnel_first_screen   INT         NOT NULL,
    funnel_first_check_in INT         NOT NULL,
    funnel_revisited      INT         NOT NULL,
    funnel_settled        BOOLEAN     NOT NULL,
    new_explorers         INT         NOT NULL,
    retained_d1           INT         NULL,
    retained_d7           INT         NULL,
    retained_d30          INT         NULL,
    computed_at           DATETIME(6) NOT NULL,
    PRIMARY KEY (cohort_day)
);
