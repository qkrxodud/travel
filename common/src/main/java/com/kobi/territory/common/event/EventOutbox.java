package com.kobi.territory.common.event;

/**
 * 트랜잭셔널 outbox 포트. 컨텍스트 간 이벤트는 이 포트로만 내보낸다.
 * <p>
 * 호출한 트랜잭션 안에서 outbox 테이블에 한 행을 적재한다(애그리거트 저장과 같은 커밋).
 * 실제 전달은 릴레이가 비동기로 하며 최소 1회 전달이므로 구독자는 멱등해야 한다.
 */
public interface EventOutbox {

    /**
     * @param aggregateType 이벤트를 낸 애그리거트 종류 (예: "Territory")
     * @param aggregateId   애그리거트 식별자 (예: mapId)
     * @param event         공개 이벤트 (각 컨텍스트 api 패키지의 record)
     */
    void append(String aggregateType, String aggregateId, DomainEvent event);
}
