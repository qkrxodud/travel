package com.kobi.territory.exploration.infra;

import org.springframework.data.jpa.repository.JpaRepository;

interface ExplorerJpaRepository extends JpaRepository<ExplorerJpaEntity, String> {}
