package com.kobi.territory.social.domain.friendship;

import com.kobi.territory.common.model.ExplorerId;
import java.util.List;

/** Friendship 저장소(friendship 테이블). 애그리거트 = 관계 한 건이라 추가·삭제 단위로 둔다. */
public interface FriendshipRepository {

    /** follower 가 팔로우하는 관계(나가는 쪽). */
    List<Friendship> outgoing(ExplorerId follower);

    /** followee 를 팔로우하는 관계(들어오는 쪽). */
    List<Friendship> incoming(ExplorerId followee);

    /** 두 탐험가 사이의 관계(양방향, 0~2건) — 맞팔로우 판정. */
    List<Friendship> between(ExplorerId one, ExplorerId other);

    /** 새 관계 저장 — 같은 쌍이 이미 있으면(동시 팔로우 경합) PK 위반을 {@link FriendshipAlreadyExists} 로 번역한다. */
    void add(Friendship friendship);

    void remove(Friendship friendship);
}
