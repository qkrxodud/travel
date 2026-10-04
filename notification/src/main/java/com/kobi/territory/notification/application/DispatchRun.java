package com.kobi.territory.notification.application;

import com.kobi.territory.notification.domain.delivery.DeliveryStatus;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 발송기 한 번의 결과.
 *
 * @param outcomes    끝난 상태별 기록 수(SENT·FAILED·EXPIRED·CANCELLED, 다시 보낼 것은 PENDING)
 * @param notClaimed  잡지 못한 기록 수(다른 발송기가 먼저 잡음)
 * @param goneDevices 구독이 없어져 지운 기기 수
 */
public record DispatchRun(Map<DeliveryStatus, Integer> outcomes, int notClaimed, int goneDevices) {

    public DispatchRun {
        EnumMap<DeliveryStatus, Integer> copy = new EnumMap<>(DeliveryStatus.class);
        copy.putAll(outcomes);
        outcomes = Collections.unmodifiableMap(copy);
    }

    public int count(DeliveryStatus status) {
        return outcomes.getOrDefault(status, 0);
    }
}
