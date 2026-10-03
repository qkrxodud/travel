package com.kobi.territory.social.application;

import com.kobi.territory.common.event.DomainEvent;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.api.event.ExplorerMerged;
import com.kobi.territory.exploration.api.event.RegionVisited;
import com.kobi.territory.exploration.api.event.VisitCancelled;
import com.kobi.territory.exploration.api.event.VisitsHidden;
import com.kobi.territory.exploration.api.event.VisitsRestored;
import com.kobi.territory.exploration.api.query.ExplorerProfileQuery;
import com.kobi.territory.progression.api.event.BadgeEarned;
import com.kobi.territory.progression.api.event.LevelUp;
import com.kobi.territory.progression.api.event.SetCompleted;
import com.kobi.territory.social.domain.feed.FeedEntry;
import com.kobi.territory.social.domain.feed.FeedEntryRepository;
import com.kobi.territory.social.domain.feed.FeedGeneration;
import com.kobi.territory.social.domain.feed.FeedGenerationRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 친구 소식 프로젝터 — 공개 이벤트 → feed_entry. 실시간(구독자 {@code social.feed})은 지금 세대에, 재구성(FeedRebuildService)은 다음
 * 세대에 같은 규칙으로 쓴다. 모든 처리는 멱등(같은 세대의 refId UNIQUE, 거두기·숨기기·옮기기는 몇 번 해도 같음).
 * <p>
 * 병합(ExplorerMerged)되어 비활성인 탐험가(from) 앞으로 늦게 도착한 이벤트는 그 계정 탐험가(into)의 소식으로 넣는다. 취소는 (지금 주인,
 * 지도, 지역)으로 거두므로 병합으로 옮겨진 방문을 into 가 취소해도 거둬진다(QA P2-5). 공개 범위는 여기서 거르지 않는다(읽을 때).
 */
@Service
public class FeedProjector {

    /** 투영하는 이벤트 타입(구독자 등록·재구성 재생이 같은 목록을 쓴다). */
    public static final List<Class<? extends DomainEvent>> EVENT_TYPES = List.of(RegionVisited.class, VisitCancelled.class,
        VisitsHidden.class, VisitsRestored.class, SetCompleted.class, LevelUp.class, BadgeEarned.class, ExplorerMerged.class);

    private final FeedEntryRepository feed;
    private final FeedGenerationRepository generations;
    private final ExplorerProfileQuery profiles;

    public FeedProjector(FeedEntryRepository feed, FeedGenerationRepository generations, ExplorerProfileQuery profiles) {
        this.feed = feed;
        this.generations = generations;
        this.profiles = profiles;
    }

    /** 실시간 투영(구독자 social.feed) — 지금 세대에. */
    @Transactional
    public void project(DomainEvent event) {
        project(event, generations.live());
    }

    public boolean accepts(DomainEvent event) {
        return EVENT_TYPES.stream().anyMatch(type -> type.isInstance(event));
    }

    /** 이벤트 하나를 세대 generation 에 투영한다(호출자 트랜잭션 — 릴레이·재구성). */
    public void project(DomainEvent event, FeedGeneration generation) {
        switch (event) {
            case RegionVisited visited -> {
                ExplorerId explorer = ExplorerId.of(visited.explorerId());
                feed.addIfAbsent(generation, FeedEntry.visit(explorer, visited.mapId(), visited.regionCode(), visited.rarity(),
                    visited.visitGeneration(), visited.visitedAt()).attributedTo(actorOf(explorer)));
            }
            // 체크인 취소 → 그 지도·지역의 취소 이전·회차 이하 체크인 소식을 거둔다(지역 아이템·기본 XP 처럼 되돌리는 쪽 — 취소 비대칭)
            case VisitCancelled cancelled -> feed.retractVisits(generation, actorOf(ExplorerId.of(cancelled.explorerId())),
                cancelled.mapId(), cancelled.regionCode(), cancelled.visitGeneration(), cancelled.cancelledAt());
            // 탈퇴 유예 숨김 → 그 지도의 체크인 소식을 숨긴다(재가입이면 다시 보임, 유예가 끝나도 숨긴 채)
            case VisitsHidden hidden -> feed.hideVisits(generation, actorOf(ExplorerId.of(hidden.explorerId())), hidden.mapId(),
                hidden.hiddenRegionCodes(), hidden.hiddenAt());
            case VisitsRestored restored -> feed.unhideVisits(generation, actorOf(ExplorerId.of(restored.explorerId())),
                restored.mapId(), restored.restoredRegionCodes());
            case SetCompleted completed -> {
                ExplorerId explorer = ExplorerId.of(completed.explorerId());
                feed.addIfAbsent(generation, FeedEntry.themeCompleted(explorer, completed.setId(), completed.completedAt())
                    .attributedTo(actorOf(explorer)));
            }
            case LevelUp levelUp -> {
                ExplorerId explorer = ExplorerId.of(levelUp.explorerId());
                feed.addIfAbsent(generation, FeedEntry.levelUp(explorer, levelUp.level(), levelUp.at()).attributedTo(actorOf(explorer)));
            }
            case BadgeEarned badge -> {
                ExplorerId explorer = ExplorerId.of(badge.explorerId());
                feed.addIfAbsent(generation, FeedEntry.badgeEarned(explorer, badge.badgeId(), badge.at()).attributedTo(actorOf(explorer)));
            }
            // 계정 병합 → from 의 소식을 into 로, from 개인 지도 체크인 소식은 into 개인 지도로(익명은 handle 이 없어 아무도 팔로우할 수 없었다)
            case ExplorerMerged merged -> feed.absorbMerged(generation, ExplorerId.of(merged.fromExplorerId()),
                ExplorerId.of(merged.intoExplorerId()), merged.fromPersonalMapId(), merged.intoPersonalMapId());
            default -> throw new IllegalArgumentException("친구 소식이 받지 않는 이벤트: " + event.getClass().getName());
        }
    }

    /** 소식의 주인 — 병합돼 비활성이면 계정 탐험가. */
    private ExplorerId actorOf(ExplorerId explorer) {
        return profiles.mergedInto(explorer.value()).map(ExplorerId::of).orElse(explorer);
    }
}
