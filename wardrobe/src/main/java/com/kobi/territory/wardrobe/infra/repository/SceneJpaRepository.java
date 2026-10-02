package com.kobi.territory.wardrobe.infra.repository;

import com.kobi.territory.wardrobe.infra.entity.SceneJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

interface SceneJpaRepository extends JpaRepository<SceneJpaEntity, String> {
}
