package com.kobi.territory.social.infra.repository;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.social.domain.friendship.Friendship;
import com.kobi.territory.social.domain.friendship.FriendshipAlreadyExists;
import com.kobi.territory.social.domain.friendship.FriendshipRepository;
import com.kobi.territory.social.infra.entity.FriendshipJpaEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * Friendship 저장소 어댑터. 추가는 persist + flush — 같은 쌍이 동시에 들어오면(도메인 검사 뒤 경합) PK 위반을 FriendshipAlreadyExists 로
 * 번역한다(merge 로 조용히 덮어쓰지 않게).
 */
@Repository
class JpaFriendshipRepository implements FriendshipRepository {

    private final FriendshipJpaRepository rows;
    private final EntityManager entityManager;

    JpaFriendshipRepository(FriendshipJpaRepository rows, EntityManager entityManager) {
        this.rows = rows;
        this.entityManager = entityManager;
    }

    @Override
    public List<Friendship> outgoing(ExplorerId follower) {
        return rows.findByFollowerId(follower.value()).stream().map(FriendshipJpaEntity::toDomain).toList();
    }

    @Override
    public List<Friendship> incoming(ExplorerId followee) {
        return rows.findByFolloweeId(followee.value()).stream().map(FriendshipJpaEntity::toDomain).toList();
    }

    @Override
    public List<Friendship> between(ExplorerId one, ExplorerId other) {
        return rows.between(one.value(), other.value()).stream().map(FriendshipJpaEntity::toDomain).toList();
    }

    @Override
    public void add(Friendship friendship) {
        try {
            entityManager.persist(FriendshipJpaEntity.from(friendship));
            entityManager.flush();
        } catch (PersistenceException duplicate) {
            throw new FriendshipAlreadyExists(friendship, duplicate);
        }
    }

    @Override
    public void remove(Friendship friendship) {
        rows.deleteById(FriendshipJpaEntity.keyOf(friendship));
    }
}
