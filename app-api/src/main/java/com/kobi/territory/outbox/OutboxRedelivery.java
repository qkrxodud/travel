package com.kobi.territory.outbox;

import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FAILED 전달 재전달(운영 진입점 — 관리 API 는 이후, local 은 POST /dev/outbox/redeliver).
 * FAILED 를 PENDING(시도 0)으로 되돌리면 릴레이가 다음 주기에 같은 순서 단위의 앞에서부터 다시 보낸다.
 */
@Service
public class OutboxRedelivery {

    private final OutboxDeliveryRepository deliveries;
    private final Clock clock;

    public OutboxRedelivery(OutboxDeliveryRepository deliveries, Clock clock) {
        this.deliveries = deliveries;
        this.clock = clock;
    }

    /**
     * @param eventId    null 이면 모든 이벤트
     * @param subscriber null 이면 모든 구독자
     * @return 되돌린 FAILED 전달 수
     */
    @Transactional
    public int redeliverFailed(Long eventId, String subscriber) {
        List<OutboxDeliveryEntity> failed = deliveries.findByStatus(OutboxDeliveryEntity.Status.FAILED).stream()
            .filter(delivery -> eventId == null || eventId.equals(delivery.getEventId()))
            .filter(delivery -> subscriber == null || subscriber.equals(delivery.getSubscriber()))
            .toList();
        failed.forEach(delivery -> delivery.redeliver(clock.instant()));
        return failed.size();
    }
}
