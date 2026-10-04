package com.kobi.territory.notification.application;

import com.kobi.territory.common.event.EventSubscriber;
import com.kobi.territory.exploration.api.event.ExplorerMerged;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 알림의 outbox 구독 — 소비 대상 하나(알림 받는 사람)에 구독자 하나 {@code notification.recipient}(id 는 outbox_delivery.subscriber 키라
 * 바꾸지 않는다). 계정 병합 때 익명 탐험가의 기기를 계정 탐험가로 옮긴다(멱등).
 */
@Configuration
public class NotificationSubscriptions {

    public static final String SUBSCRIBER = "notification.recipient";

    @Bean
    EventSubscriber notificationRecipientSubscriber(PushSubscriptionService subscriptions) {
        return EventSubscriber.named(SUBSCRIBER)
            .on(ExplorerMerged.class, subscriptions::onExplorerMerged)
            .build();
    }
}
