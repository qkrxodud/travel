package com.kobi.territory.notification.domain.recipient;

import com.kobi.territory.notification.domain.push.DeviceKeys;
import com.kobi.territory.notification.domain.push.PushEndpoint;
import java.time.Instant;
import java.util.Objects;

/** 기기 하나(브라우저 구독 하나) — 주소가 열쇠. registeredAt 은 마지막으로 구독을 보낸(등록·갱신) 시각. */
public record PushDevice(PushEndpoint endpoint, DeviceKeys keys, Instant registeredAt) {

    public PushDevice {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(keys, "keys");
        Objects.requireNonNull(registeredAt, "registeredAt");
    }
}
