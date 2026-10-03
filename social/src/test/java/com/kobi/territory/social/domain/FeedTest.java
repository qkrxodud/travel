package com.kobi.territory.social.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.social.domain.feed.ActivityFeed;
import com.kobi.territory.social.domain.feed.FeedAge;
import com.kobi.territory.social.domain.feed.FeedEntry;
import com.kobi.territory.social.domain.feed.FeedKind;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

/** D1: 소식 멱등 키(refId), 같은 소식 묶기, 상대 시각(일 단위), 병합 귀속. */
class FeedTest {

    static final ExplorerId KIM = ExplorerId.of("00000000-0000-0000-0000-00000000000b");
    static final ExplorerId ANON = ExplorerId.of("00000000-0000-0000-0000-0000000000ee");
    static final String PERSONAL = "00000000-0000-0000-0000-000000000101";
    static final String SHARED = "00000000-0000-0000-0000-000000000202";
    static final Instant T0 = Instant.parse("2026-10-03T03:00:00Z");
    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Test
    void 같은_이벤트는_같은_refId이고_회차가_다르면_다른_소식이다() {
        FeedEntry first = FeedEntry.visit(KIM, PERSONAL, "KR-11010", Rarity.COMMON, 1, T0);
        assertThat(FeedEntry.visit(KIM, PERSONAL, "KR-11010", Rarity.COMMON, 1, T0.plusSeconds(5)).refId()).isEqualTo(first.refId());
        assertThat(FeedEntry.visit(KIM, PERSONAL, "KR-11010", Rarity.COMMON, 2, T0).refId()).isNotEqualTo(first.refId());
        assertThat(first.refId()).isEqualTo(FeedEntry.visitRef(KIM, PERSONAL, "KR-11010", 1, T0.plusSeconds(99)));
        // 회차를 모르는 예전 이벤트(0)는 처리 시각으로 가른다
        assertThat(FeedEntry.visit(KIM, PERSONAL, "KR-11010", Rarity.COMMON, 0, T0).refId()).endsWith("@" + T0.toEpochMilli());
        assertThat(FeedEntry.levelUp(KIM, 3, T0).refId()).isEqualTo(FeedEntry.levelUp(KIM, 3, T0.plusSeconds(60)).refId());
        assertThat(FeedEntry.themeCompleted(KIM, "sea", T0).kind()).isEqualTo(FeedKind.THEME_COMPLETED);
        assertThat(first.visitGeneration()).isEqualTo(1);
        assertThat(first.attributedTo(ANON).visitGeneration()).isEqualTo(1);
        assertThat(FeedEntry.levelUp(KIM, 3, T0).visitGeneration()).isZero();
    }

    @Test
    void 같은_사람의_같은_소식은_가장_이른_것_하나만_최근_순으로_보인다() {
        FeedEntry personal = FeedEntry.visit(KIM, PERSONAL, "KR-11010", Rarity.COMMON, 1, T0);
        FeedEntry shared = FeedEntry.visit(KIM, SHARED, "KR-11010", Rarity.COMMON, 1, T0.plusSeconds(60));
        FeedEntry legend = FeedEntry.visit(KIM, SHARED, "KR-37430", Rarity.LEGEND, 1, T0.plusSeconds(120));
        FeedEntry level = FeedEntry.levelUp(KIM, 2, T0.plusSeconds(30));
        // 병합으로 옮겨 온 익명 시절의 같은 레벨 소식
        FeedEntry mergedLevel = FeedEntry.levelUp(ANON, 2, T0.minusSeconds(30)).attributedTo(KIM);

        ActivityFeed feed = ActivityFeed.of(List.of(shared, legend, personal, level, mergedLevel));

        assertThat(feed.size()).isEqualTo(3);
        assertThat(feed.latest(10)).containsExactly(legend, personal, mergedLevel);
        assertThat(feed.latest(1)).containsExactly(legend);
        assertThat(mergedLevel.actorId()).isEqualTo(KIM);
        assertThat(mergedLevel.refId()).contains(ANON.value());
    }

    @Test
    void 상대_시각은_서버_시간대의_달력_날짜로_센다() {
        Instant now = Instant.parse("2026-10-03T14:59:00Z"); // 서울 23:59
        assertThat(FeedAge.of(Instant.parse("2026-10-02T15:00:00Z"), now, SEOUL)).isEqualTo(new FeedAge(0, "오늘")); // 서울 10/3 00:00
        assertThat(FeedAge.of(Instant.parse("2026-10-02T14:59:00Z"), now, SEOUL)).isEqualTo(new FeedAge(1, "어제"));
        assertThat(FeedAge.of(now.minus(Duration.ofDays(6)), now, SEOUL).label()).isEqualTo("6일 전");
        assertThat(FeedAge.of(now.minus(Duration.ofDays(7)), now, SEOUL).label()).isEqualTo("1주 전");
        assertThat(FeedAge.of(now.minus(Duration.ofDays(29)), now, SEOUL).label()).isEqualTo("4주 전");
        assertThat(FeedAge.of(now.minus(Duration.ofDays(65)), now, SEOUL).label()).isEqualTo("2개월 전");
        assertThat(FeedAge.of(now.plusSeconds(3600), now, SEOUL).daysAgo()).isZero();
    }
}
