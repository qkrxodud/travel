package com.kobi.territory.exploration.application;

import com.kobi.territory.common.event.EventSubscriber;
import com.kobi.territory.exploration.api.event.ExplorerMerged;
import com.kobi.territory.exploration.api.event.MemberJoined;
import com.kobi.territory.exploration.api.event.MemberLeft;
import com.kobi.territory.exploration.api.event.MemberPurged;
import com.kobi.territory.exploration.api.event.MemberReassigned;
import com.kobi.territory.exploration.api.event.RegionVisited;
import com.kobi.territory.exploration.api.event.VisitsMerged;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 탐험 컨텍스트 자신의 outbox 구독(3단계). 지도(ExpeditionMap) 멤버십 이벤트를 영토(Territory)에 반영한다 — 두 애그리거트를
 * 한 트랜잭션에서 고치지 않기 위해서다(§2-9). 소비 애그리거트(Territory)당 구독자 하나. id 는 outbox_delivery 키라 바꾸지 않는다.
 */
@Configuration
public class ExplorationSubscriptions {

    @Bean
    EventSubscriber territorySubscriber(TerritoryMembershipService membership, ExplorerMergeService merges) {
        return EventSubscriber.named(TERRITORY_SUBSCRIBER)
            .on(MemberLeft.class, membership::onMemberLeft)
            .on(MemberJoined.class, membership::onMemberJoined)
            .on(MemberPurged.class, membership::onMemberPurged)
            // 4단계 병합(claimExplorer): B 개인 지도로 방문 흡수 → A 개인 지도 정리
            .on(ExplorerMerged.class, merges::onExplorerMerged)
            .on(VisitsMerged.class, merges::onVisitsMerged)
            .on(MemberReassigned.class, merges::onMemberReassigned)
            .build();
    }

    /** 4단계: 지도(ExpeditionMap) 애그리거트 구독자 — 병합 때 공유 지도 자리 정리(탐험 내부 이벤트). */
    @Bean
    EventSubscriber expeditionMapSubscriber(ExplorerMergeService merges) {
        return EventSubscriber.named("exploration.expedition-map")
            .on(MembershipHandover.class, merges::onMembershipHandover)
            .build();
    }

    /**
     * 9단계: 가고 싶은 곳(Wishlist) 구독자 — 체크인으로 핀이 다녀옴이 되고(WishFulfilled), 계정 병합 때 익명 탐험가의 핀을 합친다.
     */
    @Bean
    EventSubscriber wishlistSubscriber(WishlistService wishlists) {
        return EventSubscriber.named("exploration.wishlist")
            .on(RegionVisited.class, wishlists::onRegionVisited)
            .on(ExplorerMerged.class, wishlists::onExplorerMerged)
            .build();
    }

    /** 9단계: 재방문 도장첩(StampBook) 구독자 — 계정 병합 때 익명 탐험가의 도장을 합친다. */
    @Bean
    EventSubscriber stampBookSubscriber(RevisitService revisits) {
        return EventSubscriber.named("exploration.stamp-book")
            .on(ExplorerMerged.class, revisits::onExplorerMerged)
            .build();
    }

    /** 영토 구독자 id — 재계산 보류 판정(진행·꾸미기)이 이 구독자의 미전달 이벤트도 본다(P3-R3-1). */
    public static final String TERRITORY_SUBSCRIBER = "exploration.territory";
}
