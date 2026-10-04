package com.kobi.territory.progression.application;

import com.kobi.territory.common.event.EventSubscriber;
import com.kobi.territory.exploration.api.event.ClaimTransferred;
import com.kobi.territory.exploration.api.event.MapCreated;
import com.kobi.territory.exploration.api.event.MemberReassigned;
import com.kobi.territory.exploration.api.event.RevisitStamped;
import com.kobi.territory.exploration.api.event.WishFulfilled;
import com.kobi.territory.progression.api.event.SeasonCompleted;
import com.kobi.territory.exploration.api.event.RegionVisited;
import com.kobi.territory.exploration.api.event.VisitCancelled;
import com.kobi.territory.exploration.api.event.VisitsHidden;
import com.kobi.territory.exploration.api.event.VisitsRestored;
import com.kobi.territory.progression.api.event.QuestCompleted;
import com.kobi.territory.progression.api.event.SetCompleted;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 진행 컨텍스트의 outbox 구독(§3 체크인 이벤트 흐름). 소비 애그리거트마다 구독자 하나(QA P1-1) — 같은 지도(aggregate)의
 * 체크인·취소가 한 줄로 순서대로 도착한다. id 는 outbox_delivery.subscriber 키라 바꾸지 않는다.
 * 빈 도감은 첫 체크인 때 만든다. 진행 루트는 개인 지도 생성(MapCreated)에서 미리 만든다(S3-1).
 */
@Configuration
public class ProgressionSubscriptions {

    /**
     * ExplorerProgress: 체크인·취소·세트 완성(수령자별)·퀘스트 보상·선점 이전·개인 지도 생성(루트 선생성), 9단계: 계절 회차 완성(수령자별)·
     * 재방문 도장·가고 싶은 곳 다녀옴.
     */
    @Bean
    EventSubscriber progressSubscriber(ProgressService progress) {
        return EventSubscriber.named("progression.progress")
            .on(RegionVisited.class, progress::onRegionVisited)
            .on(VisitCancelled.class, progress::onVisitCancelled)
            .on(SetCompleted.class, progress::onSetCompleted)
            .on(QuestCompleted.class, progress::onQuestCompleted)
            .on(ClaimTransferred.class, progress::onClaimTransferred)
            .on(MapCreated.class, progress::onMapCreated)
            .on(SeasonCompleted.class, progress::onSeasonCompleted)
            .on(RevisitStamped.class, progress::onRevisitStamped)
            .on(WishFulfilled.class, progress::onWishFulfilled)
            .build();
    }

    /** CollectionBook(도감, 지도 단위): 체크인·취소·탈퇴 숨김·재가입 복구, 9단계: 병합 재귀속(계절 회차의 센 방문 주인 바꾸기). */
    @Bean
    EventSubscriber collectionBookSubscriber(CollectionBookService collectionBooks) {
        return EventSubscriber.named("progression.collection-book")
            .on(RegionVisited.class, collectionBooks::onRegionVisited)
            .on(VisitCancelled.class, collectionBooks::onVisitCancelled)
            .on(VisitsHidden.class, collectionBooks::onVisitsHidden)
            .on(VisitsRestored.class, collectionBooks::onVisitsRestored)
            .on(MemberReassigned.class, collectionBooks::onMemberReassigned)
            .build();
    }

    /** QuestBoard: 체크인. */
    @Bean
    EventSubscriber questBoardSubscriber(QuestService quests) {
        return EventSubscriber.named("progression.quest-board")
            .on(RegionVisited.class, quests::onRegionVisited)
            .build();
    }
}
