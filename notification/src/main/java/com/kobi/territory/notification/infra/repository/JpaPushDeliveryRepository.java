package com.kobi.territory.notification.infra.repository;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.notification.domain.delivery.DeliveryKey;
import com.kobi.territory.notification.domain.delivery.PushDelivery;
import com.kobi.territory.notification.domain.delivery.PushDeliveryRepository;
import com.kobi.territory.notification.infra.entity.PushDeliveryJpaEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

/** 발송 기록 저장소 어댑터. 행 ↔ 도메인 변환은 엔티티가 한다. 고칠 때는 읽은 버전과 같은지 보고(낙관적 잠금) 바로 내보낸다(충돌을 이 자리에서). */
@Repository
class JpaPushDeliveryRepository implements PushDeliveryRepository {

    private final PushDeliveryJpaRepository rows;

    JpaPushDeliveryRepository(PushDeliveryJpaRepository rows) {
        this.rows = rows;
    }

    @Override
    public List<PushDelivery> onDay(ExplorerId explorerId, LocalDate day) {
        return rows.onDay(explorerId.value(), day).stream().map(PushDeliveryJpaEntity::toDomain).toList();
    }

    @Override
    public boolean exists(DeliveryKey key) {
        return rows.existsByExplorerIdAndKindAndPeriod(key.explorerId().value(), key.kind().code(), key.period());
    }

    @Override
    public void add(PushDelivery delivery) {
        rows.saveAndFlush(PushDeliveryJpaEntity.from(delivery));
    }

    @Override
    public void update(PushDelivery delivery) {
        long id = delivery.id().orElseThrow(() -> new IllegalArgumentException("저장된 적 없는 발송 기록"));
        PushDeliveryJpaEntity row = rows.findById(id).orElseThrow(() -> new IllegalStateException("발송 기록이 없다: " + id));
        if (row.version() != delivery.version()) throw new ObjectOptimisticLockingFailureException(PushDeliveryJpaEntity.class, id);
        row.apply(delivery);
        rows.flush();
    }

    @Override
    public Optional<PushDelivery> find(long id) {
        return rows.findById(id).map(PushDeliveryJpaEntity::toDomain);
    }

    @Override
    public List<Long> due(Instant now, Instant staleBefore, int limit) {
        return rows.due(now, staleBefore, PageRequest.of(0, limit));
    }

    @Override
    public List<PushDelivery> recentOf(ExplorerId explorerId, int limit) {
        return rows.recentOf(explorerId.value(), PageRequest.of(0, limit)).stream().map(PushDeliveryJpaEntity::toDomain).toList();
    }
}
