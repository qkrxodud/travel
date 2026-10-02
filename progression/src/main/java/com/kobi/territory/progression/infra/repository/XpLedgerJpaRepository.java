package com.kobi.territory.progression.infra.repository;

import com.kobi.territory.progression.infra.entity.XpLedgerJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface XpLedgerJpaRepository extends JpaRepository<XpLedgerJpaEntity, Long> {

    List<XpLedgerJpaEntity> findByExplorerIdOrderByIdAsc(String explorerId);
}
