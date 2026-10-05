package com.kobi.territory.catalog.infra.repository;

import com.kobi.territory.catalog.infra.client.TourApiResponseStore;
import com.kobi.territory.catalog.infra.entity.TourApiResponseJpaEntity;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * TourAPI 응답 원문 캐시 어댑터. 원문은 따로 커밋한다(모으기 결과 저장이 실패해도 이미 쓴 호출을 다시 하지 않게). 새로 넣을 때 보관 기간
 * ({@link #RETENTION} — 회차 근거의 원문 기록으로 한 해 남짓)이 지난 원문을 지운다.
 */
@Repository
class JpaTourApiResponseStore implements TourApiResponseStore {

    static final Duration RETENTION = Duration.ofDays(400);

    private final TourApiResponseJpaRepository rows;

    JpaTourApiResponseStore(TourApiResponseJpaRepository rows) {
        this.rows = rows;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredResponse> latest(String requestKey, Instant notBefore) {
        return rows.findFirstByRequestKeyAndFetchedAtGreaterThanEqualOrderByFetchedAtDesc(requestKey, notBefore)
            .map(row -> new StoredResponse(row.fetchedAt(), row.body()));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(String requestKey, Instant fetchedAt, String body) {
        rows.deleteOlderThan(fetchedAt.minus(RETENTION));
        rows.save(TourApiResponseJpaEntity.of(requestKey, fetchedAt, body));
    }
}
