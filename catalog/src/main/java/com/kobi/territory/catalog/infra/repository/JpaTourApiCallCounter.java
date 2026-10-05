package com.kobi.territory.catalog.infra.repository;

import com.kobi.territory.catalog.infra.client.TourApiCallCounter;
import com.kobi.territory.catalog.infra.entity.TourApiUsageJpaEntity;
import java.time.LocalDate;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 하루 호출 수 어댑터. 세기는 따로 커밋한다(호출은 이미 나갔으니 뒤 저장이 실패해도 센 것은 남는다). 그날 첫 호출이면 행을 만들고(동시에 만들면 한쪽은
 * 유일성 위반 — 무시) 다시 조건부로 늘린다.
 */
@Repository
class JpaTourApiCallCounter implements TourApiCallCounter {

    private final TourApiUsageJpaRepository rows;
    private final TransactionTemplate separately;

    JpaTourApiCallCounter(TourApiUsageJpaRepository rows, PlatformTransactionManager transactionManager) {
        this.rows = rows;
        this.separately = new TransactionTemplate(transactionManager);
        this.separately.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        // 틈 잠금(gap lock) 없이 — 없는 날짜 행을 동시에 늘리고 만들 때 교착이 생기지 않게(잠금·격리 규칙)
        this.separately.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public boolean tryAcquire(LocalDate day, int dailyLimit) {
        if (dailyLimit <= 0) return false;
        if (increment(day, dailyLimit)) return true;
        try {
            separately.executeWithoutResult(status -> rows.insertDay(day));
        } catch (DataAccessException alreadyThere) {
            // 그날 행이 이미 있다(상한에 닿았거나 다른 요청이 먼저 만들었다) — 아래 조건부 증가가 판단한다
        }
        return increment(day, dailyLimit);
    }

    @Override
    public int usedOn(LocalDate day) {
        return rows.findById(day).map(TourApiUsageJpaEntity::calls).orElse(0);
    }

    private boolean increment(LocalDate day, int dailyLimit) {
        Integer updated = separately.execute(status -> rows.increment(day, dailyLimit));
        return updated != null && updated == 1;
    }
}
