package com.kobi.territory.social.infra.repository;

import com.kobi.territory.social.infra.entity.RankPercentileJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

interface RankPercentileJpaRepository extends JpaRepository<RankPercentileJpaEntity, String> {}
