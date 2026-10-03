package com.kobi.territory.sharing.infra.repository;

import com.kobi.territory.sharing.infra.entity.PrivacySettingsJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

interface PrivacySettingsJpaRepository extends JpaRepository<PrivacySettingsJpaEntity, String> {}
