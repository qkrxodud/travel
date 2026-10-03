package com.kobi.territory.social.infra.repository;

import com.kobi.territory.social.infra.entity.RegionStatJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

interface RegionStatJpaRepository extends JpaRepository<RegionStatJpaEntity, String> {}
