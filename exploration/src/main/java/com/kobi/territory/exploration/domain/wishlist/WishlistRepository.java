package com.kobi.territory.exploration.domain.wishlist;

import com.kobi.territory.common.model.ExplorerId;
import java.time.Instant;
import java.util.Optional;

/**
 * 가고 싶은 곳 저장소(루트 행 wishlist + 자식 wish_pin). 루트 행이 직렬화 잠금 대상이다 — 핀 꽂기(상한)와 다녀옴 처리가 같은 루트를 잠근다.
 */
public interface WishlistRepository {

    /**
     * 루트 행이 없으면 만든다(이미 있으면 그대로 — 동시에 만들어도 하나). 호출자는 따로 커밋되는 트랜잭션에서 부른다(실패한 넣기가 본
     * 트랜잭션을 오염시키지 않게).
     */
    void ensure(ExplorerId explorerId, Instant at);

    /** 루트 행을 배타 잠금하고 불러온다. 루트가 없으면(핀을 꽂은 적 없음) 빈 값. */
    Optional<Wishlist> findLocked(ExplorerId explorerId);

    /** 읽기 전용 조회. 없으면 빈 목록. */
    Wishlist load(ExplorerId explorerId);

    /** 바뀐 핀을 넣거나 고치고 뺀 핀을 지운다. */
    void save(Wishlist wishlist);
}
