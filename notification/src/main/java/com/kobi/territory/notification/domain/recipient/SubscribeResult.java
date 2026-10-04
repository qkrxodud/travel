package com.kobi.territory.notification.domain.recipient;

import com.kobi.territory.notification.domain.push.PushEndpoint;
import java.util.List;

/**
 * 구독 결과.
 *
 * @param added       새 기기면 true, 이미 있던 주소의 갱신이면 false
 * @param evicted     기기 수 상한 때문에 뺀 오래된 기기
 * @param deviceCount 지금 기기 수
 */
public record SubscribeResult(boolean added, List<PushEndpoint> evicted, int deviceCount) {

    public SubscribeResult {
        evicted = List.copyOf(evicted);
    }
}
