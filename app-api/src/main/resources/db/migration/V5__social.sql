-- 5단계(소셜) — 친구(Friendship) + 읽기 모델(친구 소식) + 일 1회 집계 스냅숏(상위 %·지역별 방문자 비율·시·도 평균).
-- V1~V4_1 은 커밋 이후라 고치지 않는다. MySQL 문법 기준, 로컬 H2(MODE=MySQL)에서도 그대로 돈다.
-- 애그리거트 경계를 넘는 FK 는 두지 않는다(friendship → explorer 도 두지 않는다 — 탐험가 정리 순서에 묶이지 않게, 대상 확인은 handle 조회로).

-- 팔로우 관계 한 건 = 애그리거트 하나(§2-7). PK(from_id, to_id) = 중복 불가. "친구" = 서로 팔로우(맞팔로우).
-- to_id 인덱스: 나를 팔로우하는 사람(팔로워·맞팔 판정).
CREATE TABLE friendship (
    from_id    VARCHAR(36) NOT NULL,
    to_id      VARCHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_friendship PRIMARY KEY (from_id, to_id)
);
CREATE INDEX idx_friendship_to ON friendship (to_id);

-- 친구 소식(읽기 모델 — 공개 이벤트 RegionVisited·SetCompleted·LevelUp·BadgeEarned 의 투영, 구독자 social.feed).
-- 이벤트로 다시 만들 수 있다: 다음 세대(generation)에 outbox 를 처음부터 재생한 뒤 지금 세대를 바꾼다(운영 POST /admin/rebuild/feed,
-- local POST /dev/rebuild/feed) — 재구성 중에도 피드가 비지 않고, 실패하면 쌓던 세대만 버린다.
--   generation   = 세대(조회·실시간 투영은 feed_state.live_generation 만)
--   ref_id       = 원본 이벤트의 멱등 키((generation, ref_id) UNIQUE — 같은 이벤트가 두 번 와도 한 행)
--   payload      = 소식 내용 JSON(지역·희귀도 | 테마 | 레벨 | 뱃지) — 메모·사진·방문일은 없다(§7)
--   region_code  = 체크인 소식의 지역(탈퇴 숨김·복구가 고를 때 — payload 밖에도 둔다)
--   occurred_at  = 이벤트 시각(체크인 = 처리 시각). 화면엔 상대 시각(일 단위)만 나간다
--   visit_generation = 체크인 회차(0 = 모름 — 다른 종류·예전 이벤트·병합으로 옮긴 소식)
--   retracted_at = 체크인 취소로 거둠((주인, 지도, 지역) + 취소 이전·회차 이하만 — 병합으로 옮겨진 소식도 지금 주인이 거두고, 취소 뒤 재체크인은 남는다), hidden_at = 탈퇴 유예 숨김(재가입이면 NULL 로) — 행은 남겨 재전달로 되살아나지 않게
CREATE TABLE feed_entry (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    generation   INT           NOT NULL,
    ref_id       VARCHAR(160)  NOT NULL,
    actor_id     VARCHAR(36)   NOT NULL,
    map_id       VARCHAR(36)   NULL,
    kind         VARCHAR(20)   NOT NULL,
    region_code  VARCHAR(10)   NULL,
    payload      VARCHAR(1000) NOT NULL,
    visit_generation INT       NOT NULL,
    occurred_at  DATETIME(6)   NOT NULL,
    retracted_at DATETIME(6)   NULL,
    hidden_at    DATETIME(6)   NULL,
    CONSTRAINT pk_feed_entry PRIMARY KEY (id),
    CONSTRAINT uq_feed_entry_ref UNIQUE (generation, ref_id)
);
CREATE INDEX idx_feed_entry_actor_occurred ON feed_entry (generation, actor_id, occurred_at);
CREATE INDEX idx_feed_entry_actor_map ON feed_entry (generation, actor_id, map_id);

-- 친구 소식 지금 세대(한 행). 재구성이 다음 세대를 다 쌓으면 바꾼다.
CREATE TABLE feed_state (
    id              INT NOT NULL,
    live_generation INT NOT NULL,
    CONSTRAINT pk_feed_state PRIMARY KEY (id)
);
INSERT INTO feed_state (id, live_generation) VALUES (1, 1);

-- 일 1회 배치 스냅숏(§5 — 설계의 Redis 대신 DB + 애플리케이션 캐시, Redis 는 트래픽 이후). explorer_region 에서 다시 계산할 수 있다.
-- 모집단 = 활성 지역이 1곳 이상인 활성 탐험가(병합 비활성·0곳 제외, 리더 결정 5) — 세 테이블이 같은 모집단을 쓴다.
-- 전체 유저 중 상위 %(전체 랭킹은 순위표 없이 이것만 — §7 참고용). rank 는 MySQL 예약어라 rank_position. population = 모집단 수.
CREATE TABLE rank_percentile (
    explorer_id   VARCHAR(36) NOT NULL,
    region_count  INT         NOT NULL,
    rank_position INT         NOT NULL,
    population    INT         NOT NULL,
    top_percent   INT         NOT NULL,
    computed_at   DATETIME(6) NOT NULL,
    CONSTRAINT pk_rank_percentile PRIMARY KEY (explorer_id)
);

-- 지역별 방문자 비율 = visitor_count / population(활성 탐험가만 셈 — 병합 비활성 탐험가의 explorer_region 제외, 여러 지도에서 같은 지역 = 1).
CREATE TABLE region_stats (
    region_code   VARCHAR(10) NOT NULL,
    visitor_count INT         NOT NULL,
    population    INT         NOT NULL,
    computed_at   DATETIME(6) NOT NULL,
    CONSTRAINT pk_region_stats PRIMARY KEY (region_code)
);

-- 시·도 평균 유저(콜드 스타트 비교 §7 — 친구 0명이면 "내 지역 평균 유저"). 주 활동 시·도(가장 많이 칠한 시·도)별 탐험가 수·지역 수 합,
-- province_code 'ALL' = 전국(지역 1곳 이상인 활성 탐험가 전체). 평균은 합 / 수(부동소수 컬럼 없음).
CREATE TABLE province_stats (
    province_code    VARCHAR(5)  NOT NULL,
    explorer_count   INT         NOT NULL,
    region_count_sum BIGINT      NOT NULL,
    computed_at      DATETIME(6) NOT NULL,
    CONSTRAINT pk_province_stats PRIMARY KEY (province_code)
);
