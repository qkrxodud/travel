package com.kobi.territory.sharing.api.query;

import java.util.Collection;
import java.util.Set;

/**
 * 공유 공개 Query: 탐험가의 공개 프로필이 누구에게 열려 있는지(공개 범위 PrivacySettings). 다른 컨텍스트는 공유를 참조하지 않으므로
 * (의존 매트릭스) 조립 모듈(app-api)이 각자의 포트로 이어 준다 — 탐험 ProfileJoinGate(프로필 링크 합류, 4단계 QA P3-5), 소셜
 * ProfileAudience(피드·영토 비교, 5단계).
 */
public interface ProfileVisibilityQuery {

    /** 공개 범위가 PUBLIC 인지(설정이 없으면 기본 PRIVATE 라 false). */
    boolean visibleToPublic(String explorerId);

    /**
     * viewer 에게 보이는지(5단계): PUBLIC 은 누구나, FRIENDS 는 서로 팔로우한 친구만, PRIVATE 는 아무도.
     *
     * @param viewerIdOrNull 보는 사람(익명 방문자면 null)
     */
    boolean visibleTo(String ownerId, String viewerIdOrNull);

    /** ownerIds 중 viewer 에게 프로필이 보이는 탐험가(5단계 — 피드). */
    Set<String> visibleAmong(Collection<String> ownerIds, String viewerId);
}
