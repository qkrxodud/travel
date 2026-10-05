package com.kobi.territory.catalog.domain.definition;

import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.common.model.RegionCode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 계절 정의: 테마 키워드로 축제 고르기, 회차 기간, 기본 목록의 출처 표시(13s단계). */
@DisplayName("계절 정의")
class SeasonDefinitionTest {

    private static final ZoneId 서울 = ZoneId.of("Asia/Seoul");
    private static final SeasonDefinition 벚꽃 = new SeasonDefinition("spring", "벚꽃 명소", "", MonthDay.of(3, 20), MonthDay.of(4, 30),
        List.of(RegionCode.of("KR-38115")), "벚꽃 순례자", "🌸", List.of("벚꽃", "왕벚"), null);
    private static final SeasonDefinition 단풍 = new SeasonDefinition("autumn", "단풍 명소", "", MonthDay.of(10, 1), MonthDay.of(11, 30),
        List.of(RegionCode.of("KR-32060")), "단풍 사냥꾼", "🍁", List.of("단풍", "가을 산"), "ai-estimate");

    @Nested
    @DisplayName("축제 이름이")
    class Theme {

        @Test
        @DisplayName("키워드를 하나라도 담으면 그 계절의 축제다")
        void matches() {
            assertThat(벚꽃.themeMatches("진해 벚꽃 축제")).isTrue();
            assertThat(벚꽃.themeMatches("제주 왕벚꽃 축제")).isTrue();
            assertThat(단풍.themeMatches("내장산 단풍 축제")).isTrue();
        }

        @Test
        @DisplayName("띄어쓰기가 달라도 맞는다")
        void ignoresSpaces() {
            assertThat(단풍.themeMatches("가을산 걷기 축제")).isTrue();
            assertThat(벚꽃.themeMatches("벚 꽃 길 축제")).isTrue();
        }

        @Test
        @DisplayName("키워드가 없으면 그 계절의 축제가 아니다")
        void unrelated() {
            assertThat(벚꽃.themeMatches("봄 딸기 축제")).isFalse();
            assertThat(벚꽃.themeMatches(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("회차 기간은")
    class Window {

        @Test
        @DisplayName("그 해 첫날 0시부터 마지막 날 다음 날 0시 전까지다(서울 시각)")
        void yearWindow() {
            SeasonRoundWindow 봄2027 = 벚꽃.windowOf(2027, 서울);
            assertThat(봄2027.roundId()).isEqualTo("spring-2027");
            assertThat(봄2027.startsAt()).isEqualTo(Instant.parse("2027-03-19T15:00:00Z"));
            assertThat(봄2027.endsAt()).isEqualTo(Instant.parse("2027-04-30T15:00:00Z"));
        }

        @Test
        @DisplayName("다음 회차는 올해 회차가 이미 열렸으면 다음 해 회차다")
        void upcoming() {
            Instant 가을중 = Instant.parse("2026-10-05T03:00:00Z");
            assertThat(단풍.upcomingWindow(가을중, 서울).roundId()).isEqualTo("autumn-2027");
            assertThat(벚꽃.upcomingWindow(가을중, 서울).roundId()).isEqualTo("spring-2027");
            assertThat(단풍.openWindow(가을중, 서울)).map(SeasonRoundWindow::roundId).hasValue("autumn-2026");
            assertThat(벚꽃.openWindow(가을중, 서울)).isEmpty();
        }

        @Test
        @DisplayName("축제를 찾는 범위는 회차 앞뒤로 여유를 둔다")
        void searchRange() {
            SeasonRoundWindow 봄2027 = 벚꽃.windowOf(2027, 서울);
            assertThat(봄2027.searchFrom(14)).isEqualTo(LocalDate.of(2027, 3, 6));
            assertThat(봄2027.searchUntil(14)).isEqualTo(LocalDate.of(2027, 5, 14));
            assertThat(봄2027.overlapsSearchRange(LocalDate.of(2027, 2, 1), LocalDate.of(2027, 3, 6), 14)).isTrue();
            assertThat(봄2027.overlapsSearchRange(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 3, 5), 14)).isFalse();
        }

        @Test
        @DisplayName("회차 id 에서 연도를 읽는다")
        void yearOf() {
            assertThat(SeasonDefinition.yearOf("autumn-2027")).hasValue(2027);
            assertThat(SeasonDefinition.yearOf("autumn")).isEmpty();
        }
    }

    @Test
    @DisplayName("기본 지역 목록의 출처를 적지 않으면 AI 추정으로 본다")
    void defaultProvenance() {
        assertThat(벚꽃.provenance()).isEqualTo(SeasonDefinition.AI_ESTIMATE);
        assertThat(new SeasonDefinition("spring", "벚꽃 명소", "", MonthDay.of(3, 20), MonthDay.of(4, 30), List.of(RegionCode.of("KR-38115")),
            "벚꽃 순례자", "🌸").keywords()).isEmpty();
    }
}
