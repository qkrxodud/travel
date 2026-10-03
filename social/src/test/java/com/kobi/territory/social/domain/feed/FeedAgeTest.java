package com.kobi.territory.social.domain.feed;

import static com.kobi.territory.social.domain.Fixtures.SEOUL;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("소식 시각")
class FeedAgeTest {

    /** 서울 10월 3일 23:59 */
    static final Instant NOW = Instant.parse("2026-10-03T14:59:00Z");

    static String ageOf(Instant occurredAt) {
        return FeedAge.of(occurredAt, NOW, SEOUL).label();
    }

    @Test
    @DisplayName("서울 날짜로 같은 날이면 자정 직후 소식도 오늘이다")
    void today() {
        assertThat(FeedAge.of(Instant.parse("2026-10-02T15:00:00Z"), NOW, SEOUL)).isEqualTo(new FeedAge(0, "오늘"));
    }

    @Test
    @DisplayName("서울 날짜로 전날이면 어제다")
    void yesterday() {
        assertThat(FeedAge.of(Instant.parse("2026-10-02T14:59:00Z"), NOW, SEOUL)).isEqualTo(new FeedAge(1, "어제"));
    }

    @Test
    @DisplayName("일주일이 안 되면 며칠 전이다")
    void daysAgo() {
        assertThat(ageOf(NOW.minus(Duration.ofDays(6)))).isEqualTo("6일 전");
    }

    @Test
    @DisplayName("일주일부터는 몇 주 전이다")
    void weeksAgo() {
        assertThat(ageOf(NOW.minus(Duration.ofDays(7)))).isEqualTo("1주 전");
        assertThat(ageOf(NOW.minus(Duration.ofDays(29)))).isEqualTo("4주 전");
    }

    @Test
    @DisplayName("두 달쯤 지나면 몇 개월 전이다")
    void monthsAgo() {
        assertThat(ageOf(NOW.minus(Duration.ofDays(65)))).isEqualTo("2개월 전");
    }

    @Test
    @DisplayName("서버 시계보다 앞선 소식은 오늘이다")
    void futureIsToday() {
        assertThat(FeedAge.of(NOW.plusSeconds(3600), NOW, SEOUL).daysAgo()).isZero();
    }
}
