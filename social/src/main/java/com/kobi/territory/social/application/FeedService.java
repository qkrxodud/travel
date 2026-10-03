package com.kobi.territory.social.application;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.api.query.ExplorerProfileQuery;
import com.kobi.territory.exploration.api.query.TerritoryQuery;
import com.kobi.territory.social.domain.feed.ActivityFeed;
import com.kobi.territory.social.domain.feed.FeedAge;
import com.kobi.territory.social.domain.feed.FeedEntry;
import com.kobi.territory.social.domain.feed.FeedEntryRepository;
import com.kobi.territory.social.domain.feed.FeedGenerationRepository;
import com.kobi.territory.social.domain.friendship.FriendshipRepository;
import com.kobi.territory.social.domain.friendship.SocialCircle;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 친구 소식 조회(GET /feed): 내가 팔로우한 사람들 중 프로필이 나에게 보이는 사람(공개 범위 — PRIVATE 제외, FRIENDS 는 맞팔로우만)의
 * 최근 소식. 같은 소식 묶기·최근 순은 ActivityFeed, 상대 시각(일 단위)은 FeedAge 가 한다. 정확한 시각·메모·사진은 내보내지 않는다.
 */
@Service
public class FeedService {

    /** 같은 소식 묶기로 줄어드는 몫을 감안해 넉넉히 읽는다. */
    private static final int READ_FACTOR = 3;

    private final FeedEntryRepository feed;
    private final FeedGenerationRepository generations;
    private final FriendshipRepository friendships;
    private final ProfileAudience audience;
    private final ExplorerProfileQuery profiles;
    private final TerritoryQuery territories;
    private final SocialSettings settings;
    private final Clock clock;

    public FeedService(FeedEntryRepository feed, FeedGenerationRepository generations, FriendshipRepository friendships, ProfileAudience audience,
                       ExplorerProfileQuery profiles, TerritoryQuery territories, SocialSettings settings, Clock clock) {
        this.feed = feed;
        this.generations = generations;
        this.friendships = friendships;
        this.audience = audience;
        this.profiles = profiles;
        this.territories = territories;
        this.settings = settings;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Feed feed(ExplorerId me) {
        territories.personalMapId(me.value());
        SocialCircle circle = SocialCircle.of(me, friendships.outgoing(me), friendships.incoming(me));
        Set<String> visible = audience.visibleAmong(circle.following().stream().map(ExplorerId::value).toList(), me.value());
        List<FeedEntry> recent = feed.recentOf(generations.live(), visible.stream().map(ExplorerId::of).toList(), settings.feedSize() * READ_FACTOR);
        Instant now = clock.instant();
        List<FeedItem> items = ActivityFeed.of(recent).latest(settings.feedSize()).stream()
            .map(entry -> new FeedItem(entry, profiles.handleOf(entry.actorId().value()).orElse(null),
                FeedAge.of(entry.occurredAt(), now, clock.getZone())))
            .toList();
        // 팔로잉 수는 나에게 드러난 팔로우만(숨은 대상 팔로우가 수로 드러나지 않게 — QA r2 P2-A)
        Set<ExplorerId> visibleIds = visible.stream().map(ExplorerId::of).collect(Collectors.toUnmodifiableSet());
        return new Feed(profiles.accountLinked(me.value()), circle.revealedFollowing(visibleIds).size(), items);
    }

    /** 소식 한 건 + 주인의 지금 handle + 상대 시각. */
    public record FeedItem(FeedEntry entry, String handle, FeedAge age) {}

    /** @param loggedIn 팔로우할 수 있는지 · @param followingCount 나에게 드러난 팔로잉 수(숨은 대상 팔로우 제외 — QA r2 P2-A) */
    public record Feed(boolean loggedIn, int followingCount, List<FeedItem> items) {
        public Feed {
            items = List.copyOf(items);
        }
    }
}
