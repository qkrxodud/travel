-- 13s단계: 계절 회차별 확정 지역 목록과 근거(한국관광공사 TourAPI 축제) + TourAPI 응답 원문 캐시. 시각은 UTC DATETIME(6), 날짜는 서울 날짜.
-- 행이 없는 회차는 계절 정의(seasons.json)의 기본 목록(AI 추정)을 쓴다 — 이 마이그레이션 전과 같은 동작. 회차가 열리면 그 회차 행은 더 바뀌지 않는다.

-- 회차 하나(SeasonLineup 루트). 후보(마지막으로 모은 것)·확정본(회차에 쓰는 것)의 수집 시각·경고, 확정한 쪽, 마지막 수집 시도.
CREATE TABLE season_lineup (
    round_id               VARCHAR(20)   NOT NULL,           -- spring-2027
    season_id              VARCHAR(12)   NOT NULL,
    round_year             INT           NOT NULL,
    first_day              DATE          NOT NULL,
    last_day               DATE          NOT NULL,
    starts_at              DATETIME(6)   NOT NULL,           -- 이 순간부터 목록 고정
    ends_at                DATETIME(6)   NOT NULL,
    candidate_collected_at DATETIME(6)   NULL,
    candidate_warnings     VARCHAR(2000) NULL,               -- 줄바꿈 구분
    confirmed_collected_at DATETIME(6)   NULL,
    confirmed_warnings     VARCHAR(2000) NULL,
    confirmed_by           VARCHAR(10)   NULL,               -- AUTO | ADMIN
    confirmed_at           DATETIME(6)   NULL,
    attempt_at             DATETIME(6)   NULL,
    attempt_outcome        VARCHAR(20)   NULL,               -- COLLECTED | PARTIAL | NOT_CONFIGURED | KEY_REJECTED | QUOTA_EXCEEDED | BAD_RESPONSE | UNREACHABLE
    attempt_warnings       VARCHAR(2000) NULL,
    version                BIGINT        NOT NULL,
    CONSTRAINT pk_season_lineup PRIMARY KEY (round_id)
);

-- 회차 지역 한 줄 — stage 별(후보·확정본) 순위 순. evidence = 근거 축제 JSON 배열(contentId·title·startDate·endDate·fetchedAt), AI 추정은 "[]".
CREATE TABLE season_lineup_region (
    round_id    VARCHAR(20)   NOT NULL,
    stage       VARCHAR(10)   NOT NULL,                      -- CANDIDATE | CONFIRMED
    position_no INT           NOT NULL,                      -- 순위(0부터)
    region_code VARCHAR(10)   NOT NULL,
    provenance  VARCHAR(16)   NOT NULL,                      -- tourapi | ai-estimate
    evidence    VARCHAR(4000) NOT NULL,
    CONSTRAINT pk_season_lineup_region PRIMARY KEY (round_id, stage, position_no)
);

-- TourAPI 응답 원문(정상 응답만) — 같은 요청을 캐시 시간 안에 다시 하지 않게(일일 한도 보호), 회차 근거의 원문 기록. 서비스 키는 넣지 않는다.
CREATE TABLE tourapi_response (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    request_key VARCHAR(300) NOT NULL,                       -- searchFestival2?eventStartDate=…&eventEndDate=…&arrange=A&numOfRows=…&pageNo=…
    fetched_at  DATETIME(6)  NOT NULL,
    body        MEDIUMTEXT   NOT NULL,
    CONSTRAINT pk_tourapi_response PRIMARY KEY (id)
);
CREATE INDEX ix_tourapi_response_request ON tourapi_response (request_key, fetched_at);

-- TourAPI 하루 호출 수(서울 날짜) — 기관 일일 한도(1,000건 미만)를 넘지 않게 이 서비스가 먼저 멈춘다. 재기동해도 이어진다.
-- 늘리기는 조건부 UPDATE 한 문장(calls < 상한)이라 동시에 불러도 상한을 넘지 않는다.
CREATE TABLE tourapi_usage (
    usage_date DATE NOT NULL,
    calls      INT  NOT NULL,
    CONSTRAINT pk_tourapi_usage PRIMARY KEY (usage_date)
);

-- 13s단계 보강: 이미 열렸거나 지난 회차(이 마이그레이션 전에 열린 spring-2026·autumn-2026)를 그때 쓰던 기본 목록(seasons.json, AI 추정)으로 고정한다
-- (confirmed_by = OPENING). 이후 seasons.json 의 기본 목록을 고쳐도 진행 중·지난 회차는 바뀌지 않는다. 앞으로 열리는 회차는 앱이 첫 조회·기동 때 같은 방식으로 고정한다.
INSERT INTO season_lineup (round_id, season_id, round_year, first_day, last_day, starts_at, ends_at, confirmed_collected_at, confirmed_warnings, confirmed_by, confirmed_at, version)
VALUES ('spring-2026', 'spring', 2026, '2026-03-20', '2026-04-30', '2026-03-19 15:00:00', '2026-04-30 15:00:00', '2026-03-19 15:00:00', '확정 없이 회차가 열려 그때의 기본 목록(AI 추정)으로 고정했습니다', 'OPENING', '2026-03-19 15:00:00', 0);
INSERT INTO season_lineup_region (round_id, stage, position_no, region_code, provenance, evidence) VALUES
    ('spring-2026', 'CONFIRMED', 0, 'KR-38115', 'ai-estimate', '[]'),
    ('spring-2026', 'CONFIRMED', 1, 'KR-37020', 'ai-estimate', '[]'),
    ('spring-2026', 'CONFIRMED', 2, 'KR-11190', 'ai-estimate', '[]'),
    ('spring-2026', 'CONFIRMED', 3, 'KR-11240', 'ai-estimate', '[]'),
    ('spring-2026', 'CONFIRMED', 4, 'KR-38360', 'ai-estimate', '[]'),
    ('spring-2026', 'CONFIRMED', 5, 'KR-32030', 'ai-estimate', '[]'),
    ('spring-2026', 'CONFIRMED', 6, 'KR-33030', 'ai-estimate', '[]'),
    ('spring-2026', 'CONFIRMED', 7, 'KR-35020', 'ai-estimate', '[]'),
    ('spring-2026', 'CONFIRMED', 8, 'KR-39010', 'ai-estimate', '[]'),
    ('spring-2026', 'CONFIRMED', 9, 'KR-36330', 'ai-estimate', '[]');
INSERT INTO season_lineup (round_id, season_id, round_year, first_day, last_day, starts_at, ends_at, confirmed_collected_at, confirmed_warnings, confirmed_by, confirmed_at, version)
VALUES ('autumn-2026', 'autumn', 2026, '2026-10-01', '2026-11-30', '2026-09-30 15:00:00', '2026-11-30 15:00:00', '2026-09-30 15:00:00', '확정 없이 회차가 열려 그때의 기본 목록(AI 추정)으로 고정했습니다', 'OPENING', '2026-09-30 15:00:00', 0);
INSERT INTO season_lineup_region (round_id, stage, position_no, region_code, provenance, evidence) VALUES
    ('autumn-2026', 'CONFIRMED', 0, 'KR-32060', 'ai-estimate', '[]'),
    ('autumn-2026', 'CONFIRMED', 1, 'KR-35040', 'ai-estimate', '[]'),
    ('autumn-2026', 'CONFIRMED', 2, 'KR-32340', 'ai-estimate', '[]'),
    ('autumn-2026', 'CONFIRMED', 3, 'KR-37330', 'ai-estimate', '[]'),
    ('autumn-2026', 'CONFIRMED', 4, 'KR-36450', 'ai-estimate', '[]'),
    ('autumn-2026', 'CONFIRMED', 5, 'KR-33320', 'ai-estimate', '[]'),
    ('autumn-2026', 'CONFIRMED', 6, 'KR-38400', 'ai-estimate', '[]'),
    ('autumn-2026', 'CONFIRMED', 7, 'KR-11090', 'ai-estimate', '[]'),
    ('autumn-2026', 'CONFIRMED', 8, 'KR-31370', 'ai-estimate', '[]'),
    ('autumn-2026', 'CONFIRMED', 9, 'KR-35310', 'ai-estimate', '[]');
