-- 4단계(파트 A): 구글 로그인 계정 · 익명 탐험가 병합(claimExplorer) · 재계산 예약.
-- 파트 B(공유)는 V4_1__sharing.sql. 5단계 소셜은 V5. MySQL 문법 기준이며 로컬 H2(MODE=MySQL)에서도 그대로 돈다(ALTER 는 한 문장에 하나씩).
-- V3·V3_1 은 3단계 커밋(1cfca7c) 이후라 고치지 않는다(P3-R3-7).

-- 탐험가 상태: ACTIVE | MERGED(익명 탐험가가 로그인으로 계정 탐험가에 병합돼 비활성 — 토큰 무효, 되돌리지 않음).
ALTER TABLE explorer ADD COLUMN status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE explorer ADD COLUMN merged_into VARCHAR(36) NULL;
ALTER TABLE explorer ADD COLUMN merged_at DATETIME(6) NULL;

-- 로그인 계정(Explorer 애그리거트의 자식, 탐험가와 1:1). (provider, subject) = 구글 OIDC sub 가 유일 키.
-- 연결 후 바뀌지 않는다(행 갱신 없음). email 은 handle 자동 발급·표시용.
CREATE TABLE account (
    explorer_id VARCHAR(36)  NOT NULL,
    provider    VARCHAR(20)  NOT NULL,
    subject     VARCHAR(255) NOT NULL,
    email       VARCHAR(320) NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    CONSTRAINT pk_account PRIMARY KEY (explorer_id),
    CONSTRAINT uq_account_identity UNIQUE (provider, subject),
    CONSTRAINT fk_account_explorer FOREIGN KEY (explorer_id) REFERENCES explorer (id)
);

-- 재계산 예약(app-api 조립 모듈 소유, FK 없음 — outbox 와 같은 공통 인프라). 병합처럼 이벤트 없이 영토가 바뀐 탐험가를 적어 두면
-- 배치(RecalculationRequestJob)가 보류 규칙(미전달 이벤트 없음)을 만족할 때 진행·인벤토리를 다시 만들고 행을 지운다.
-- generation = 다시 예약될 때마다 +1 — 배치는 읽은 generation 그대로일 때만 지운다(처리 중 새 예약을 잃지 않게).
CREATE TABLE recalculation_request (
    explorer_id  VARCHAR(36) NOT NULL,
    reason       VARCHAR(40) NOT NULL,
    requested_at DATETIME(6) NOT NULL,
    generation   INT         NOT NULL DEFAULT 0,
    attempts     INT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_recalculation_request PRIMARY KEY (explorer_id)
);

-- 놓은 handle 의 예약(Explorer 애그리거트의 자식, QA P3-10): handle 을 바꾸면 옛 handle 을 reserved_until 까지 다른 탐험가가
-- 가져갈 수 없다(공유된 /u/옛handle 링크 탈취 방지). 기간은 territory.account.handle-reservation-days(기본 30일).
CREATE TABLE handle_reservation (
    explorer_id    VARCHAR(36) NOT NULL,
    handle         VARCHAR(30) NOT NULL,
    reserved_until DATETIME(6) NOT NULL,
    CONSTRAINT pk_handle_reservation PRIMARY KEY (explorer_id, handle),
    CONSTRAINT fk_handle_reservation_explorer FOREIGN KEY (explorer_id) REFERENCES explorer (id)
);
CREATE INDEX idx_handle_reservation_handle ON handle_reservation (handle);
