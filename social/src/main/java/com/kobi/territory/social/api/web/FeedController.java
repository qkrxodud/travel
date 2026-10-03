package com.kobi.territory.social.api.web;

import com.kobi.territory.common.identity.CurrentExplorer;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.social.api.web.SocialDtos.FeedResponse;
import com.kobi.territory.social.application.FeedService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /feed} — 내가 팔로우한 사람들(프로필이 나에게 보이는 사람만)의 최근 소식. 시각은 상대 시각(일 단위)만. */
@RestController
public class FeedController {

    private final FeedService feeds;

    public FeedController(FeedService feeds) {
        this.feeds = feeds;
    }

    @GetMapping("/feed")
    public FeedResponse feed(@CurrentExplorer ExplorerId explorerId) {
        return FeedResponse.of(feeds.feed(explorerId));
    }
}
