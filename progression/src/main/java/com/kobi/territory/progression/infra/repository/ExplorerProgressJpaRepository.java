package com.kobi.territory.progression.infra.repository;

import com.kobi.territory.progression.infra.entity.ExplorerProgressJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ExplorerProgressJpaRepository extends JpaRepository<ExplorerProgressJpaEntity, String> {

    /**
     * 루트 행 배타 잠금(SELECT … FOR UPDATE — 잠금이 풀릴 때까지 대기). 재계산과 진행 이벤트 처리가 같은 잠금으로 시작해
     * 잠금 순서(루트 → 자식)가 같다(구조 QA S2-2). PESSIMISTIC_FORCE_INCREMENT 는 MySQL 에서 FOR UPDATE NOWAIT 가 되어
     * 기다리지 않고 바로 실패하므로 쓰지 않고, version 증가는 저장 시점에 따로 건다(saveRoot).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select progress from ExplorerProgressJpaEntity progress where progress.explorerId = :explorerId")
    Optional<ExplorerProgressJpaEntity> lockById(@Param("explorerId") String explorerId);
}
