package com.kobi.territory.recalc;

import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RecalculationRequestRepository extends JpaRepository<RecalculationRequestEntity, String> {

    List<RecalculationRequestEntity> findByOrderByRequestedAtAsc(Limit limit);

    /** 처리한 뒤 지운다 — 그사이 다시 예약됐으면(generation 이 바뀜) 남긴다. */
    @Modifying
    @Query("delete from RecalculationRequestEntity request where request.explorerId = :explorerId and request.generation = :seen")
    int deleteIfUnchanged(@Param("explorerId") String explorerId, @Param("seen") int seen);

    @Modifying
    @Query("update RecalculationRequestEntity request set request.attempts = request.attempts + 1 where request.explorerId = :explorerId")
    int countAttempt(@Param("explorerId") String explorerId);
}
