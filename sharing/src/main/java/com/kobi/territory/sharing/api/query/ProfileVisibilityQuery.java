package com.kobi.territory.sharing.api.query;

/**
 * 공유 공개 Query(4단계 QA P3-5): 이 탐험가의 공개 프로필이 누구에게나 열려 있는지. 프로필이 비공개면 프로필 링크 합류도 닫는다
 * — 탐험(exploration)은 공유를 참조할 수 없으므로 조립 모듈(app-api)이 탐험의 포트(ProfileJoinGate)로 이어 준다.
 */
public interface ProfileVisibilityQuery {

    /** 공개 범위가 PUBLIC 인지(FRIENDS 는 5단계 전까지 false, 설정이 없으면 기본 PRIVATE 라 false). */
    boolean visibleToPublic(String explorerId);
}
