package com.kobi.territory.catalog.infra.repository;

import com.kobi.territory.catalog.infra.entity.ItemDefinitionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

interface ItemDefinitionJpaRepository extends JpaRepository<ItemDefinitionJpaEntity, String> {
}
