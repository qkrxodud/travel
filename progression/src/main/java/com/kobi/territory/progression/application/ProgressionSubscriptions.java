package com.kobi.territory.progression.application;

import com.kobi.territory.common.event.EventSubscriber;
import com.kobi.territory.exploration.api.event.RegionVisited;
import com.kobi.territory.exploration.api.event.VisitCancelled;
import com.kobi.territory.progression.api.event.QuestCompleted;
import com.kobi.territory.progression.api.event.SetCompleted;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 진행 컨텍스트의 outbox 구독(§3 체크인 이벤트 흐름). 소비 애그리거트마다 구독자 하나(QA P1-1) — 같은 지도(aggregate)의
 * 체크인·취소가 한 줄로 순서대로 도착한다. id 는 outbox_delivery.subscriber 키라 바꾸지 않는다.
 * MapCreated 는 구독하지 않는다(빈 도감은 첫 체크인 때 만든다).
 */
@Configuration
public class ProgressionSubscriptions {

    /** ExplorerProgress: 체크인·취소·세트 완성·퀘스트 보상. */
    @Bean
    EventSubscriber progressSubscriber(ProgressService progress) {
        return EventSubscriber.named("progression.progress")
            .on(RegionVisited.class, progress::onRegionVisited)
            .on(VisitCancelled.class, progress::onVisitCancelled)
            .on(SetCompleted.class, progress::onSetCompleted)
            .on(QuestCompleted.class, progress::onQuestCompleted)
            .build();
    }

    /** CollectionBook(도감, 지도 단위): 체크인·취소. */
    @Bean
    EventSubscriber collectionBookSubscriber(CollectionBookService collectionBooks) {
        return EventSubscriber.named("progression.collection-book")
            .on(RegionVisited.class, collectionBooks::onRegionVisited)
            .on(VisitCancelled.class, collectionBooks::onVisitCancelled)
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
