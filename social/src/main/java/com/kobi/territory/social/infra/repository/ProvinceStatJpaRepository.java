package com.kobi.territory.social.infra.repository;

import com.kobi.territory.social.infra.entity.ProvinceStatJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProvinceStatJpaRepository extends JpaRepository<ProvinceStatJpaEntity, String> {}
