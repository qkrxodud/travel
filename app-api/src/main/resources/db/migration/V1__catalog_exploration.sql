-- 1단계(카탈로그 + 탐험). 카탈로그 참조 데이터는 리소스 JSON(테이블 없음).
-- MySQL 문법 기준이며 로컬 H2(MODE=MySQL)에서도 그대로 돈다.
-- 애그리거트 경계를 넘는 FK는 두지 않는다(explorer·expedition_map 루트로만 향함). outbox는 FK 없음.

-- 탐험가(계정 루트). 1~3단계 익명 탐험가는 handle NULL. 4단계 계정 연결(claimExplorer)은 컬럼 추가로 확장.
CREATE TABLE explorer (
    id         VARCHAR(36) NOT NULL,
    handle     VARCHAR(30) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_explorer PRIMARY KEY (id),
    CONSTRAINT uq_explorer_handle UNIQUE (handle)
);

-- 지도(ExpeditionMap). 가입 시 개인 지도(kind=PERSONAL, 멤버 1명) 자동 생성.
-- MapSettings: photo_required, daily_check_in_cap(기본값 = territory.check-in.daily-cap), visibility.
CREATE TABLE expedition_map (
    id                 VARCHAR(36) NOT NULL,
    name               VARCHAR(40) NOT NULL,
    country_code       VARCHAR(2)  NOT NULL,
    invite_code        VARCHAR(8)  NOT NULL,
    owner_id           VARCHAR(36) NOT NULL,
    kind               VARCHAR(16) NOT NULL,
    photo_required     BOOLEAN     NOT NULL DEFAULT FALSE,
    daily_check_in_cap INT         NOT NULL,
    visibility         VARCHAR(16) NOT NULL,
    created_at         DATETIME(6) NOT NULL,
    CONSTRAINT pk_expedition_map PRIMARY KEY (id),
    CONSTRAINT uq_expedition_map_invite_code UNIQUE (invite_code),
    CONSTRAINT fk_expedition_map_owner FOREIGN KEY (owner_id) REFERENCES explorer (id)
);
CREATE INDEX idx_expedition_map_owner_kind ON expedition_map (owner_id, kind);

-- 지도 멤버(≤4, OWNER 1명). joined_at = 온보딩 예외(가입 후 N시간 상한 미적용) 기준.
CREATE TABLE map_member (
    map_id      VARCHAR(36) NOT NULL,
    explorer_id VARCHAR(36) NOT NULL,
    role        VARCHAR(16) NOT NULL,
    joined_at   DATETIME(6) NOT NULL,
    CONSTRAINT pk_map_member PRIMARY KEY (map_id, explorer_id),
    CONSTRAINT fk_map_member_map FOREIGN KEY (map_id) REFERENCES expedition_map (id),
    CONSTRAINT fk_map_member_explorer FOREIGN KEY (explorer_id) REFERENCES explorer (id)
);
CREATE INDEX idx_map_member_explorer ON map_member (explorer_id);

-- 영토(Territory) 루트 행. 방문은 visit 에 있고, 이 행은 체크인·수정·취소를 지도 단위로 직렬화하는 잠금 대상이다
-- (SELECT ... FOR UPDATE). expedition_map 행과 분리해 지도 가입·탈퇴·설정 변경(3단계)이 체크인과 경합하지 않게 한다(§2-9).
-- 지도 생성 시 함께 만든다(빈 Territory).
CREATE TABLE territory (
    map_id     VARCHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_territory PRIMARY KEY (map_id),
    CONSTRAINT fk_territory_map FOREIGN KEY (map_id) REFERENCES expedition_map (id)
);

-- 방문(Territory). (지도, 지역, 멤버)당 1행. 취소는 물리 삭제 + VisitCancelled.
-- visited_at = 처리 시각(서버 시계, 하루 상한 집계 기준), visit_date = 사용자가 적은 방문일.
-- disputed·hidden_at 은 3단계(V3)에서 추가.
CREATE TABLE visit (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    map_id        VARCHAR(36)  NOT NULL,
    region_code   VARCHAR(10)  NOT NULL,
    checked_in_by VARCHAR(36)  NOT NULL,
    verification  VARCHAR(8)   NOT NULL,
    visit_date    DATE         NOT NULL,
    memo          VARCHAR(160) NOT NULL DEFAULT '',
    photo_url     VARCHAR(500) NULL,
    visited_at    DATETIME(6)  NOT NULL,
    CONSTRAINT pk_visit PRIMARY KEY (id),
    CONSTRAINT uq_visit_map_region_member UNIQUE (map_id, region_code, checked_in_by),
    CONSTRAINT fk_visit_map FOREIGN KEY (map_id) REFERENCES expedition_map (id),
    CONSTRAINT fk_visit_explorer FOREIGN KEY (checked_in_by) REFERENCES explorer (id)
);
CREATE INDEX idx_visit_member_visited_at ON visit (map_id, checked_in_by, visited_at);

-- 트랜잭셔널 outbox. 애그리거트 저장과 같은 트랜잭션에 적재, @Scheduled 릴레이가 발행 후 published_at 기록.
-- payload 는 JSON 문자열(공개 이벤트 record). 이벤트가 작아 VARCHAR로 둔다(MySQL 전환 시 JSON 타입 검토).
CREATE TABLE outbox (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    aggregate    VARCHAR(40)   NOT NULL,
    aggregate_id VARCHAR(64)   NOT NULL,
    event_type   VARCHAR(200)  NOT NULL,
    payload      VARCHAR(4000) NOT NULL,
    created_at   DATETIME(6)   NOT NULL,
    published_at DATETIME(6)   NULL,
    CONSTRAINT pk_outbox PRIMARY KEY (id)
);
CREATE INDEX idx_outbox_unpublished ON outbox (published_at, id);
