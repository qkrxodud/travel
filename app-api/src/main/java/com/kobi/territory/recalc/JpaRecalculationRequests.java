package com.kobi.territory.recalc;

import com.kobi.territory.common.event.RecalculationRequests;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** RecalculationRequests(common 포트) 구현 — 호출자 트랜잭션 안에서 예약 행을 넣거나 새로 고친다(MANDATORY). */
@Component
class JpaRecalculationRequests implements RecalculationRequests {

    private final RecalculationRequestRepository repository;

    JpaRecalculationRequests(RecalculationRequestRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void request(String explorerId, String reason, Instant requestedAt) {
        repository.findById(explorerId).ifPresentOrElse(existing -> existing.renew(reason, requestedAt),
            () -> repository.save(RecalculationRequestEntity.of(explorerId, reason, requestedAt)));
    }
}
