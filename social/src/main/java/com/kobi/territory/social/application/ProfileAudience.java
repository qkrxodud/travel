package com.kobi.territory.social.application;

import java.util.Collection;
import java.util.Set;

/**
 * 공개 범위 포트(5단계): 누구의 프로필(과 활동)이 누구에게 보이는지. 공개 범위의 주인은 공유 컨텍스트인데 공유도 소셜의 친구 관계를
 * 물어야 해서(FRIENDS) 서로 직접 참조하면 순환이 된다 — 소셜은 포트만 두고 조립 모듈(app-api)이 공유의 공개 Query
 * (ProfileVisibilityQuery)로 구현한다(4단계 ProfileJoinGate 방식). 규칙: PUBLIC 누구나, FRIENDS 맞팔로우만, PRIVATE 아무도.
 */
public interface ProfileAudience {

    boolean visibleTo(String ownerId, String viewerId);

    /** ownerIds 중 viewer 에게 보이는 탐험가(친구 소식 — PRIVATE 탐험가의 활동은 안 보이고, FRIENDS 는 맞팔로우에게만). */
    Set<String> visibleAmong(Collection<String> ownerIds, String viewerId);
}
