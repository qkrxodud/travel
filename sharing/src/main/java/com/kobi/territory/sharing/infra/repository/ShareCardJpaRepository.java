package com.kobi.territory.sharing.infra.repository;

import com.kobi.territory.sharing.infra.entity.ShareCardJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

interface ShareCardJpaRepository extends JpaRepository<ShareCardJpaEntity, ShareCardJpaEntity.Key> {}
