package com.kobi.territory.catalog.infra.repository;

import com.kobi.territory.catalog.domain.mystery.MysteryWeek;
import com.kobi.territory.catalog.domain.mystery.MysteryWeekRepository;
import com.kobi.territory.catalog.infra.entity.MysteryWeekJpaEntity;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 미스터리 지역 선택 기록 저장소 어댑터(mystery_week). 행 ↔ 도메인 변환은 엔티티가 한다. */
@Repository
class JpaMysteryWeekRepository implements MysteryWeekRepository {

    private final MysteryWeekJpaRepository rows;
    private final EntityManager entityManager;

    JpaMysteryWeekRepository(MysteryWeekJpaRepository rows, EntityManager entityManager) {
        this.rows = rows;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<MysteryWeek> find(LocalDate weekStart) {
        return rows.findById(weekStart).map(MysteryWeekJpaEntity::toDomain);
    }

    /** 새 행으로만 넣는다(persist + flush — 같은 주를 동시에 고르면 PK 충돌로 실패, 덮어쓰지 않는다). */
    @Override
    public void add(MysteryWeek week) {
        entityManager.persist(MysteryWeekJpaEntity.from(week));
        entityManager.flush();
    }
}
