package com.kobi.territory.catalog.infra.repository;

import com.kobi.territory.catalog.infra.entity.MysteryWeekJpaEntity;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

interface MysteryWeekJpaRepository extends JpaRepository<MysteryWeekJpaEntity, LocalDate> {
}
