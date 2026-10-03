package com.kobi.territory.outbox;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {

    List<OutboxEventEntity> findByPublishedAtIsNullOrderByIdAsc(Limit limit);

    /** 커서 페이지 — 멈춘(FAILED) 행이 쌓여도 그 뒤 행까지 한 주기에 훑는다. */
    List<OutboxEventEntity> findByPublishedAtIsNullAndIdGreaterThanOrderByIdAsc(Long afterId, Limit limit);

    /** 릴레이 커서 페이지의 id 만(정지 구독자 몫만 남은 행을 본문 없이 건너뛰려고 — 5단계 QA r2 P3-C). */
    @Query("select e.id from OutboxEventEntity e where e.publishedAt is null and e.id > :afterId order by e.id")
    List<Long> findUnpublishedIdsAfter(@Param("afterId") Long afterId, Limit limit);

    List<OutboxEventEntity> findByIdInOrderByIdAsc(Collection<Long> ids);

    /** 재생용 커서 페이지(발행 여부와 무관 — 5단계 읽기 모델 재구성). */
    List<OutboxEventEntity> findByIdGreaterThanOrderByIdAsc(Long afterId, Limit limit);

    List<OutboxEventEntity> findByAggregateIdOrderByIdAsc(String aggregateId);

    List<OutboxEventEntity> findByAggregateIdInAndPublishedAtIsNull(Collection<String> aggregateIds);

    boolean existsByPublishedAtIsNull();
}
