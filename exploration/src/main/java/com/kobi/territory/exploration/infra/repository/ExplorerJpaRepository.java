package com.kobi.territory.exploration.infra.repository;

import com.kobi.territory.exploration.infra.entity.ExplorerJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

interface ExplorerJpaRepository extends JpaRepository<ExplorerJpaEntity, String> {}
