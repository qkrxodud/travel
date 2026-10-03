package com.kobi.territory.common.event;

import java.util.Collection;

/**
 * outbox 에 아직 전달되지 않은 이벤트가 남아 있는지 묻는 포트(구현은 app-api outbox).
 * 재계산 배치가 "릴레이가 아직 처리하지 않은 이벤트가 있는 탐험가는 건너뛴다(보류)"는 규칙에 쓴다(구조 QA S3-3).
 */
public interface EventBacklog {

    /**
     * aggregateIds(예: 탐험가 id·지도 id)의 이벤트 중, id 가 subscriberPrefix 로 시작하는 구독자가 받는 것인데 그 구독자에게
     * 아직 DELIVERED 가 아닌 것(대기·재시도·FAILED)이 있는지.
     */
    boolean hasUndelivered(Collection<String> aggregateIds, String subscriberPrefix);

    /** 접두사 여러 개 중 하나라도 미전달이 있는지(재계산 보류 — 자기 구독자 + 탐험 영토 구독자, P3-R3-1). */
    default boolean hasUndelivered(Collection<String> aggregateIds, Collection<String> subscriberPrefixes) {
        return subscriberPrefixes.stream().anyMatch(prefix -> hasUndelivered(aggregateIds, prefix));
    }

    /** aggregateIds 의 이벤트 중 어느 구독자에게든 아직 전달되지 않은 것이 있는지(접두사 없음 = 모든 구독자). */
    default boolean hasPending(Collection<String> aggregateIds) {
        return hasUndelivered(aggregateIds, "");
    }

    /** 미발행 이벤트가 하나라도 있는지. */
    boolean hasAnyPending();
}
