package com.kobi.territory.social.infra.entity;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.social.domain.friendship.Friendship;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** friendship — 팔로우 관계 한 건(Friendship). PK(from_id, to_id) = 중복 불가. */
@Entity
@Table(name = "friendship")
@IdClass(FriendshipJpaEntity.Key.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FriendshipJpaEntity {

    @Id
    @Column(name = "from_id", length = 36)
    private String followerId;

    @Id
    @Column(name = "to_id", length = 36)
    private String followeeId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static FriendshipJpaEntity from(Friendship friendship) {
        FriendshipJpaEntity entity = new FriendshipJpaEntity();
        entity.followerId = friendship.follower().value();
        entity.followeeId = friendship.followee().value();
        entity.createdAt = friendship.since();
        return entity;
    }

    public static Key keyOf(Friendship friendship) {
        return new Key(friendship.follower().value(), friendship.followee().value());
    }

    public Friendship toDomain() {
        return Friendship.restore(ExplorerId.of(followerId), ExplorerId.of(followeeId), createdAt);
    }

    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private String followerId;
        private String followeeId;
    }
}
