package com.kobi.territory.exploration.infra.repository;

import com.kobi.territory.exploration.infra.entity.WishlistJpaEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface WishlistJpaRepository extends JpaRepository<WishlistJpaEntity, String> {

    /** 루트 행 배타 잠금(SELECT … FOR UPDATE, 대기). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select wishlist from WishlistJpaEntity wishlist where wishlist.explorerId = :explorerId")
    Optional<WishlistJpaEntity> lockById(@Param("explorerId") String explorerId);

    /** 없을 때만 루트 행을 넣는다(MySQL·H2 공통 문법). @return 넣은 행 수 */
    @Modifying
    @Query(value = "INSERT INTO wishlist (explorer_id, created_at) SELECT :explorerId, :createdAt FROM DUAL"
        + " WHERE NOT EXISTS (SELECT 1 FROM wishlist WHERE explorer_id = :explorerId)", nativeQuery = true)
    int insertIfAbsent(@Param("explorerId") String explorerId, @Param("createdAt") Instant createdAt);
}
