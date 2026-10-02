package com.kobi.territory.exploration.infra.repository;

import com.kobi.territory.exploration.infra.entity.ExplorerJpaEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface ExplorerJpaRepository extends JpaRepository<ExplorerJpaEntity, String> {

    Optional<ExplorerJpaEntity> findByAccessTokenHash(String accessTokenHash);
}
