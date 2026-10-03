package com.kobi.territory.exploration.infra.repository;

import com.kobi.territory.exploration.infra.entity.AccountJpaEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface AccountJpaRepository extends JpaRepository<AccountJpaEntity, String> {

    Optional<AccountJpaEntity> findByProviderAndSubject(String provider, String subject);
}
