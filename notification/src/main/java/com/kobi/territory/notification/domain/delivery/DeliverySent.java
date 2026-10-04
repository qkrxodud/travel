package com.kobi.territory.notification.domain.delivery;

import java.time.Instant;

/** 보낸 알림 한 건(한 기기라도 받음) — application 이 공개 이벤트 PushSent 로 옮긴다. */
public record DeliverySent(DeliveryKey key, int devices, Instant sentAt) {}
