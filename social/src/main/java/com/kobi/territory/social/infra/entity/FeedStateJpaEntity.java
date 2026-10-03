package com.kobi.territory.social.infra.entity;

import com.kobi.territory.social.domain.feed.FeedGeneration;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** feed_state — 친구 소식 지금 세대(한 행, id = 1). 재구성이 끝나면 바뀐다. */
@Entity
@Table(name = "feed_state")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeedStateJpaEntity {

    public static final int SINGLETON = 1;

    @Id
    private int id;

    @Column(name = "live_generation", nullable = false)
    private int liveGeneration;

    public FeedGeneration toDomain() {
        return new FeedGeneration(liveGeneration);
    }

    public void apply(FeedGeneration live) {
        this.liveGeneration = live.value();
    }
}
