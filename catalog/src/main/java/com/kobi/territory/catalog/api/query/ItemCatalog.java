package com.kobi.territory.catalog.api.query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 아이템 정의 공개 Query(3단계, DB item_definition 기준). 꾸미기 컨텍스트가 지급할 아이템과 슬롯·희귀도를 묻는다.
 * 지급 규칙 판정(어떤 체크인·세트 완성이 무엇을 주는지)은 카탈로그가 한다 — 규칙의 주인이 정의를 가진 쪽이라서.
 */
public interface ItemCatalog {

    Optional<ItemView> item(String itemId);

    /** 주어진 id 중 정의가 있는 것(중복 제거, 요청 순서). */
    List<ItemView> items(Collection<String> itemIds);

    /**
     * 체크인 한 번으로 받는 아이템: 그 지역 특산물(REGION_VISIT) + 유효 기간 안이고 정의가 생긴 뒤인 이슈 아이템
     * (PERIOD_CHECK_IN·PROVINCE_CHECK_IN — 소급 지급 없음, Q-R2-1).
     *
     * @param processedAt 체크인 처리 시각(서버 시계) — 사용자가 적은 방문일이 아니다. 기간은 서버 시간대의 날짜로 본다
     */
    List<ItemView> grantedByCheckIn(String regionCode, String provinceCode, Instant processedAt);

    /** 도감 테마(세트) 완성으로 받는 아이템(세트 배경 set:{setId} 등). 기간은 <b>완성 시각</b>의 날짜로 본다(Q-R2-1). */
    List<ItemView> grantedByThemeCompletion(String setId, Instant completedAt);

    /**
     * 초대로 공유 지도에 처음 합류했을 때 한쪽이 받는 한정 아이템(INVITATION 규칙, 4단계). 기간은 합류 시각의 날짜로 본다.
     *
     * @param side HOST(초대한 탐험가) | GUEST(초대받아 합류한 탐험가)
     */
    List<ItemView> grantedByInvitation(String side, Instant joinedAt);

    /** 시·도 정복(8단계, PROVINCE_COMPLETE 규칙)으로 받는 아이템. 기간은 정복 시각의 날짜로 본다. 회수 없음. */
    List<ItemView> grantedByProvinceConquest(String provinceCode, Instant conqueredAt);

    /** 연속 탐험 마일스톤(8단계, STREAK_MILESTONE 규칙)으로 받는 아이템. 기간은 도달 시각의 날짜로 본다. 회수 없음. */
    List<ItemView> grantedByStreakMilestone(int months, Instant reachedAt);

    /** 계절 한정 테마 회차 완성(9단계, SEASON_COMPLETE 규칙 — 회차별 계절 배경)으로 받는 아이템. 기간은 완성 시각의 날짜로 본다. 회수 없음. */
    List<ItemView> grantedBySeasonCompletion(String roundId, Instant completedAt);
}
