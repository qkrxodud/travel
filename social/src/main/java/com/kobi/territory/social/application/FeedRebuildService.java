package com.kobi.territory.social.application;

import com.kobi.territory.common.event.DomainEvent;
import com.kobi.territory.social.domain.feed.FeedEntryRepository;
import com.kobi.territory.social.domain.feed.FeedGeneration;
import com.kobi.territory.social.domain.feed.FeedGenerationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 친구 소식 재구성의 세대 관리(리더 결정 4). 다음 세대에 처음부터 쌓고({@link #begin} → {@link #project} 반복) 다 쌓이면 지금 세대를 바꾼다
 * ({@link #complete}). 실패하면 쌓던 세대만 지운다({@link #abandon}) — 이전 피드는 그대로. outbox 재생·구독자 정지 같은 순서 조율은
 * 조립 모듈(app-api FeedRebuildJob)이 한다(outbox 는 app-api 인프라).
 */
@Service
public class FeedRebuildService {

    private final FeedGenerationRepository generations;
    private final FeedEntryRepository feed;
    private final FeedProjector projector;

    public FeedRebuildService(FeedGenerationRepository generations, FeedEntryRepository feed, FeedProjector projector) {
        this.generations = generations;
        this.feed = feed;
        this.projector = projector;
    }

    /** 다음 세대를 비우고(지난 실패의 남은 행) 그 세대를 낸다. */
    @Transactional
    public FeedGeneration begin() {
        FeedGeneration building = generations.live().next();
        feed.deleteGeneration(building);
        return building;
    }

    public boolean accepts(DomainEvent event) {
        return projector.accepts(event);
    }

    /** 이벤트 하나를 쌓는 세대에 투영(호출자 트랜잭션). */
    public void project(DomainEvent event, FeedGeneration building) {
        projector.project(event, building);
    }

    /** 지금 세대를 building 으로 바꾸고 옛 세대를 지운다. @return 지운 옛 행 수 */
    @Transactional
    public int complete(FeedGeneration building) {
        FeedGeneration previous = generations.live();
        generations.switchLive(building);
        return feed.deleteGeneration(previous);
    }

    /** 실패: 쌓던 세대만 버린다(지금 세대는 그대로). */
    @Transactional
    public void abandon(FeedGeneration building) {
        feed.deleteGeneration(building);
    }

    public FeedGeneration live() {
        return generations.live();
    }
}
