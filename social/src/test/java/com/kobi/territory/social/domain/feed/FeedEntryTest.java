package com.kobi.territory.social.domain.feed;

import static com.kobi.territory.social.domain.Fixtures.ANON;
import static com.kobi.territory.social.domain.Fixtures.KIM;
import static com.kobi.territory.social.domain.Fixtures.PERSONAL_MAP;
import static com.kobi.territory.social.domain.Fixtures.T0;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.common.model.Rarity;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 회귀 출처: 5단계 친구 소식 읽기 모델 — 같은 사건은 한 건(재전달·재생 멱등), 회차(QA r2 P2-B), 공개 정보만(§7). */
@DisplayName("친구 소식 한 건")
class FeedEntryTest {

    static FeedEntry jongno(int round) {
        return FeedEntry.visit(KIM, PERSONAL_MAP, "KR-11010", Rarity.COMMON, round, T0);
    }

    @Nested
    @DisplayName("체크인 소식")
    class Visit {

        @Test
        @DisplayName("같은 체크인이 다시 와도 처리 시각과 상관없이 같은 소식이다")
        void sameCheckInSameNews() {
            assertThat(FeedEntry.visit(KIM, PERSONAL_MAP, "KR-11010", Rarity.COMMON, 1, T0.plusSeconds(5)).refId())
                .isEqualTo(jongno(1).refId())
                .isEqualTo(FeedEntry.visitRef(KIM, PERSONAL_MAP, "KR-11010", 1, T0.plusSeconds(99)));
        }

        @Test
        @DisplayName("취소하고 다시 칠하면 다른 소식이다")
        void newRoundNewNews() {
            assertThat(jongno(2).refId()).isNotEqualTo(jongno(1).refId());
        }

        @Test
        @DisplayName("회차를 모르는 예전 체크인은 처리 시각으로 구분한다")
        void legacyByTime() {
            assertThat(jongno(0).refId()).endsWith("@" + T0.toEpochMilli());
        }

        @Test
        @DisplayName("몇 번째 회차인지 기억한다")
        void remembersRound() {
            assertThat(jongno(1).visitGeneration()).isEqualTo(1);
        }

        @Test
        @DisplayName("회차는 음수일 수 없다")
        void negativeRoundRejected() {
            assertThatThrownBy(() -> new FeedEntry("r", KIM, PERSONAL_MAP, FeedKind.VISIT, FeedDetail.visit("KR-11010", Rarity.COMMON),
                T0, -1)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("레벨·테마·뱃지 소식")
    class Milestones {

        @Test
        @DisplayName("같은 레벨에 다시 올라도 한 번만 소식이 된다")
        void levelOnce() {
            assertThat(FeedEntry.levelUp(KIM, 3, T0).refId()).isEqualTo(FeedEntry.levelUp(KIM, 3, T0.plusSeconds(60)).refId());
        }

        @Test
        @DisplayName("레벨 소식에는 회차가 없다")
        void levelHasNoRound() {
            assertThat(FeedEntry.levelUp(KIM, 3, T0).visitGeneration()).isZero();
        }

        @Test
        @DisplayName("테마를 완성하면 테마 완성 소식이 된다")
        void themeCompleted() {
            assertThat(FeedEntry.themeCompleted(KIM, "sea", T0).kind()).isEqualTo(FeedKind.THEME_COMPLETED);
        }

        @Test
        @DisplayName("같은 뱃지는 한 번만 소식이 되고 다른 뱃지는 따로 소식이 된다")
        void badgeOncePerBadge() {
            assertThat(FeedEntry.badgeEarned(KIM, "first", T0).refId()).isEqualTo(FeedEntry.badgeEarned(KIM, "first", T0.plusSeconds(9)).refId())
                .isNotEqualTo(FeedEntry.badgeEarned(KIM, "legend", T0).refId());
            assertThat(FeedEntry.badgeEarned(KIM, "first", T0).kind()).isEqualTo(FeedKind.BADGE_EARNED);
        }
    }

    @Nested
    @DisplayName("익명 탐험가가 계정으로 합쳐지면")
    class Merge {

        @Test
        @DisplayName("옮긴 소식도 회차를 그대로 지닌다")
        void keepsRound() {
            FeedEntry anonymous = FeedEntry.visit(ANON, PERSONAL_MAP, "KR-11010", Rarity.COMMON, 1, T0);

            assertThat(anonymous.attributedTo(KIM).visitGeneration()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("소식에는 메모·사진·방문일이 담길 자리가 없다")
    void noPrivateFields() {
        assertThat(Arrays.stream(FeedDetail.class.getRecordComponents()).map(RecordComponent::getName))
            .containsExactly("regionCode", "rarity", "themeId", "level", "badgeId");
    }
}
