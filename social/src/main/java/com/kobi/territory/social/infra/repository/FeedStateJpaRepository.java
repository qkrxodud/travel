package com.kobi.territory.social.infra.repository;

import com.kobi.territory.social.infra.entity.FeedStateJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

interface FeedStateJpaRepository extends JpaRepository<FeedStateJpaEntity, Integer> {}
