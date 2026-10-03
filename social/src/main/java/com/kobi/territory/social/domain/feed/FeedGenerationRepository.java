package com.kobi.territory.social.domain.feed;

/** 친구 소식 세대 저장소(feed_state 한 행). */
public interface FeedGenerationRepository {

    /** 지금 조회·실시간 투영 대상 세대. */
    FeedGeneration live();

    /** 지금 세대를 next 로 바꾼다(재구성 완료). */
    void switchLive(FeedGeneration next);
}
