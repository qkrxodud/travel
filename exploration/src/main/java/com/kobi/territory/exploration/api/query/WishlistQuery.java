package com.kobi.territory.exploration.api.query;

import java.time.Instant;
import java.util.List;

/** 가고 싶은 곳 공개 Query(9단계). 진행 재계산이 다녀온 곳 보상(XP·뱃지)을 다시 맞출 때 쓴다 — 위시리스트 자체는 비공개(화면 API 만). */
public interface WishlistQuery {

    /** 이 탐험가가 다녀온 가고 싶은 곳(다녀온 순). 없으면 빈 목록. */
    List<FulfilledWishView> fulfilledOf(String explorerId);

    record FulfilledWishView(String regionCode, Instant fulfilledAt) {}
}
