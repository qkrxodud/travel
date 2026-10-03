package com.kobi.territory.exploration.application;

import com.kobi.territory.common.model.ExplorerId;

/**
 * 프로필 링크 합류의 문지기 포트(4단계 QA P3-5): 프로필 주인의 공개 프로필이 누구에게나 열려 있는지. 공개 범위의 주인은 공유
 * 컨텍스트인데 탐험은 공유를 참조할 수 없으므로(의존 매트릭스), 조립 모듈(app-api)이 공유의 공개 Query 로 구현한다.
 * 프로필이 비공개면 지도 공개 여부와 무관하게 프로필 합류는 404 PROFILE_MAP_NOT_FOUND, 초대 보상도 없다.
 */
public interface ProfileJoinGate {

    boolean profileOpen(ExplorerId profileOwner);
}
