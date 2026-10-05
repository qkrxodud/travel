package com.kobi.territory.catalog.infra.repository;

import com.kobi.territory.catalog.infra.entity.TourApiResponseJpaEntity;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface TourApiResponseJpaRepository extends JpaRepository<TourApiResponseJpaEntity, Long> {

    Optional<TourApiResponseJpaEntity> findFirstByRequestKeyAndFetchedAtGreaterThanEqualOrderByFetchedAtDesc(String requestKey,
                                                                                                            Instant notBefore);

    @Transactional
    @Modifying
    @Query("DELETE FROM TourApiResponseJpaEntity row WHERE row.fetchedAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
