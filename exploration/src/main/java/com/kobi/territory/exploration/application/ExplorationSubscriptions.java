package com.kobi.territory.exploration.application;

import com.kobi.territory.common.event.EventSubscriber;
import com.kobi.territory.exploration.api.event.MemberJoined;
import com.kobi.territory.exploration.api.event.MemberLeft;
import com.kobi.territory.exploration.api.event.MemberPurged;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 탐험 컨텍스트 자신의 outbox 구독(3단계). 지도(ExpeditionMap) 멤버십 이벤트를 영토(Territory)에 반영한다 — 두 애그리거트를
 * 한 트랜잭션에서 고치지 않기 위해서다(§2-9). 소비 애그리거트(Territory)당 구독자 하나. id 는 outbox_delivery 키라 바꾸지 않는다.
 */
@Configuration
public class ExplorationSubscriptions {

    @Bean
    EventSubscriber territorySubscriber(TerritoryMembershipService membership) {
        return EventSubscriber.named("exploration.territory")
            .on(MemberLeft.class, membership::onMemberLeft)
            .on(MemberJoined.class, membership::onMemberJoined)
            .on(MemberPurged.class, membership::onMemberPurged)
            .build();
    }
}
