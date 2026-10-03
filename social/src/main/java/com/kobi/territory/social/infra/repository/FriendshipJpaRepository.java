package com.kobi.territory.social.infra.repository;

import com.kobi.territory.social.infra.entity.FriendshipJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface FriendshipJpaRepository extends JpaRepository<FriendshipJpaEntity, FriendshipJpaEntity.Key> {

    List<FriendshipJpaEntity> findByFollowerId(String followerId);

    List<FriendshipJpaEntity> findByFolloweeId(String followeeId);

    @Query("select f from FriendshipJpaEntity f where (f.followerId = :one and f.followeeId = :other) "
        + "or (f.followerId = :other and f.followeeId = :one)")
    List<FriendshipJpaEntity> between(@Param("one") String one, @Param("other") String other);
}
