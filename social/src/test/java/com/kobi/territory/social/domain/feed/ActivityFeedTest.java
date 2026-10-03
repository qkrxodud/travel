package com.kobi.territory.social.domain.feed;

import static com.kobi.territory.social.domain.Fixtures.ANON;
import static com.kobi.territory.social.domain.Fixtures.KIM;
import static com.kobi.territory.social.domain.Fixtures.PERSONAL_MAP;
import static com.kobi.territory.social.domain.Fixtures.SHARED_MAP;
import static com.kobi.territory.social.domain.Fixtures.T0;
import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.common.model.Rarity;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("친구 소식 목록")
class ActivityFeedTest {

    /** 김이 개인 지도에서 종로(0초), 공유 지도에서 종로(60초)·울릉(120초), 레벨 2(30초) — 익명 시절의 같은 레벨 2(−30초)를 합쳐 왔다. */
    static final FeedEntry PERSONAL = FeedEntry.visit(KIM, PERSONAL_MAP, "KR-11010", Rarity.COMMON, 1, T0);
    static final FeedEntry SHARED = FeedEntry.visit(KIM, SHARED_MAP, "KR-11010", Rarity.COMMON, 1, T0.plusSeconds(60));
    static final FeedEntry LEGEND = FeedEntry.visit(KIM, SHARED_MAP, "KR-37430", Rarity.LEGEND, 1, T0.plusSeconds(120));
    static final FeedEntry LEVEL = FeedEntry.levelUp(KIM, 2, T0.plusSeconds(30));
    static final FeedEntry MERGED_LEVEL = FeedEntry.levelUp(ANON, 2, T0.minusSeconds(30)).attributedTo(KIM);
    static final ActivityFeed FEED = ActivityFeed.of(List.of(SHARED, LEGEND, PERSONAL, LEVEL, MERGED_LEVEL));

    @Test
    @DisplayName("같은 사람의 같은 소식은 가장 이른 것 하나만 보인다")
    void earliestOfSameNews() {
        assertThat(FEED.size()).isEqualTo(3);
    }

    @Test
    @DisplayName("최근 소식부터 보인다")
    void latestFirst() {
        assertThat(FEED.latest(10)).containsExactly(LEGEND, PERSONAL, MERGED_LEVEL);
    }

    @Test
    @DisplayName("보여 줄 개수만큼만 보인다")
    void limited() {
        assertThat(FEED.latest(1)).containsExactly(LEGEND);
    }

    @Test
    @DisplayName("합쳐 온 익명 시절 소식은 계정 주인의 소식으로 보인다")
    void mergedNewsBelongsToAccount() {
        assertThat(MERGED_LEVEL.actorId()).isEqualTo(KIM);
    }

    @Test
    @DisplayName("합쳐 온 소식은 원래 사건과 같은 소식으로 남아 다시 와도 겹치지 않는다")
    void mergedNewsKeepsOrigin() {
        assertThat(MERGED_LEVEL.refId()).contains(ANON.value()).isEqualTo(FeedEntry.levelUp(ANON, 2, T0).refId());
    }
}
