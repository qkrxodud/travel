package com.kobi.territory.social.application;

import com.kobi.territory.common.event.EventSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 소셜 컨텍스트의 outbox 구독. 소비 대상(읽기 모델) 하나당 구독자 하나 — {@code social.feed}(친구 소식). id 는 outbox_delivery.subscriber
 * 키라 바꾸지 않는다. 랭킹은 요청 시점 계산·일 1회 배치라 구독하지 않는다(§5).
 */
@Configuration
public class SocialSubscriptions {

    /** 구독자 id(재구성 동안 릴레이가 이 구독자만 멈춘다). */
    public static final String FEED_SUBSCRIBER = "social.feed";

    /**
     * 친구 소식: 체크인·취소·탈퇴 숨김·재가입 복구·테마 완성·레벨 업·뱃지·계정 병합({@link FeedProjector#EVENT_TYPES}).
     */
    @Bean
    EventSubscriber feedSubscriber(FeedProjector projector) {
        EventSubscriber.Builder builder = EventSubscriber.named(FEED_SUBSCRIBER);
        FeedProjector.EVENT_TYPES.forEach(type -> builder.on(type, projector::project));
        return builder.build();
    }
}
