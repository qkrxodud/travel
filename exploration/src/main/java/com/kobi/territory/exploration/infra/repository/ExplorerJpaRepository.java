package com.kobi.territory.exploration.infra.repository;

import com.kobi.territory.exploration.infra.entity.ExplorerJpaEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ExplorerJpaRepository extends JpaRepository<ExplorerJpaEntity, String> {

    Optional<ExplorerJpaEntity> findByAccessTokenHash(String accessTokenHash);

    Optional<ExplorerJpaEntity> findByHandle(String handle);

    boolean existsByHandleAndIdNot(String handle, String id);

    @Query("select explorer.id from ExplorerJpaEntity explorer where explorer.status = 'ACTIVE' order by explorer.createdAt, explorer.id")
    List<String> findActiveIds();
}
