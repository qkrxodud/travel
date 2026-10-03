package com.kobi.territory.social.infra.repository;

import com.kobi.territory.social.domain.feed.FeedGeneration;
import com.kobi.territory.social.domain.feed.FeedGenerationRepository;
import com.kobi.territory.social.infra.entity.FeedStateJpaEntity;
import org.springframework.stereotype.Repository;

/** 친구 소식 세대 어댑터 — feed_state 한 행(V5 가 1 세대로 넣는다). */
@Repository
class JpaFeedGenerationRepository implements FeedGenerationRepository {

    private final FeedStateJpaRepository rows;

    JpaFeedGenerationRepository(FeedStateJpaRepository rows) {
        this.rows = rows;
    }

    @Override
    public FeedGeneration live() {
        return state().toDomain();
    }

    @Override
    public void switchLive(FeedGeneration next) {
        state().apply(next);
    }

    private FeedStateJpaEntity state() {
        return rows.findById(FeedStateJpaEntity.SINGLETON).orElseThrow(() -> new IllegalStateException("feed_state 행이 없다(V5)"));
    }
}
