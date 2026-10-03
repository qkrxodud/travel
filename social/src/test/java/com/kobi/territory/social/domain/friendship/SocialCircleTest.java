package com.kobi.territory.social.domain.friendship;

import static com.kobi.territory.social.domain.Fixtures.KIM;
import static com.kobi.territory.social.domain.Fixtures.LEE;
import static com.kobi.territory.social.domain.Fixtures.ME;
import static com.kobi.territory.social.domain.Fixtures.PARK;
import static com.kobi.territory.social.domain.Fixtures.follows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 회귀 출처: 5단계 리더 결정 1(비공개 맞팔 친구는 랭킹에서 숨김) · QA r2 P2-A(숨은 대상은 관계 목록에서 뺌). */
@DisplayName("친구 관계")
class SocialCircleTest {

    /** 나 ↔ 김(서로), 나 → 이(한쪽), 박 → 나(한쪽). */
    static final SocialCircle CIRCLE = SocialCircle.of(ME, List.of(follows(ME, KIM), follows(ME, LEE)),
        List.of(follows(KIM, ME), follows(PARK, ME)));

    @Nested
    @DisplayName("친구는")
    class Friends {

        @Test
        @DisplayName("서로 팔로우한 사이다")
        void mutual() {
            assertThat(CIRCLE.isMutual(KIM)).isTrue();
            assertThat(CIRCLE.mutualFriends()).containsExactly(KIM);
        }

        @Test
        @DisplayName("내가 한쪽으로만 팔로우하면 친구가 아니다")
        void onlyIFollow() {
            assertThat(CIRCLE.isMutual(LEE)).isFalse();
        }

        @Test
        @DisplayName("그가 한쪽으로만 나를 팔로우하면 친구가 아니다")
        void onlyTheyFollow() {
            assertThat(CIRCLE.isMutual(PARK)).isFalse();
        }

        @Test
        @DisplayName("두 사람의 팔로우 기록만 보고도 서로 팔로우했는지 안다")
        void mutualBetweenTwo() {
            assertThat(SocialCircle.mutual(ME, KIM, List.of(follows(ME, KIM), follows(KIM, ME)))).isTrue();
            assertThat(SocialCircle.mutual(ME, KIM, List.of(follows(ME, KIM)))).isFalse();
            assertThat(SocialCircle.mutual(ME, KIM, List.of())).isFalse();
        }
    }

    @Nested
    @DisplayName("친구 랭킹에 함께 서는 사람은")
    class RankingMembers {

        @Test
        @DisplayName("나와 프로필이 나에게 보이는 친구다")
        void meAndVisibleFriends() {
            assertThat(CIRCLE.rankingMembers(Set.of(KIM))).containsExactly(ME, KIM);
        }

        @Test
        @DisplayName("프로필을 비공개로 둔 친구는 빠지고 나는 언제나 선다")
        void hidesPrivateFriend() {
            assertThat(CIRCLE.rankingMembers(Set.of())).containsExactly(ME);
        }
    }

    @Nested
    @DisplayName("관계 목록은")
    class Relations {

        @Test
        @DisplayName("내가 팔로우하는 사람을 보여 준다")
        void following() {
            assertThat(CIRCLE.following()).containsExactly(KIM, LEE);
        }

        @Test
        @DisplayName("프로필이 보이면 한쪽으로만 팔로우한 사람도 넣는다")
        void includesVisibleOneWay() {
            assertThat(CIRCLE.relations(Set.of(LEE))).extracting(FriendRelation::explorerId).containsExactly(KIM, LEE, PARK);
        }

        @Test
        @DisplayName("프로필이 숨은 사람을 한쪽으로만 팔로우했으면 뺀다")
        void dropsHiddenOneWay() {
            assertThat(CIRCLE.relations(Set.of())).extracting(FriendRelation::explorerId).containsExactly(KIM, PARK);
        }

        @Test
        @DisplayName("친구에게는 서로 팔로우했다고 표시한다")
        void marksMutual() {
            assertThat(CIRCLE.relations(Set.of()).get(0).mutual()).isTrue();
        }

        @Test
        @DisplayName("나를 팔로우하는 사람을 안다")
        void followedBy() {
            assertThat(CIRCLE.followedBy(PARK)).isTrue();
            assertThat(CIRCLE.followedBy(LEE)).isFalse();
        }

        @Test
        @DisplayName("한 사람과의 관계를 내가 팔로우하는지와 그가 팔로우하는지로 알려 준다")
        void relationTo() {
            assertThat(CIRCLE.relationTo(PARK)).isEqualTo(new FriendRelation(PARK, false, true));
        }
    }

    @Nested
    @DisplayName("영토를 비교하려면")
    class Compare {

        /** 나 ↔ 김(서로), 나 → 이(한쪽). */
        static final SocialCircle COMPARE = SocialCircle.of(ME, List.of(follows(ME, KIM), follows(ME, LEE)), List.of(follows(KIM, ME)));

        @Test
        @DisplayName("친구의 프로필이 나에게 보이면 비교할 수 있다")
        void visibleFriend() {
            assertThatCode(() -> COMPARE.requireComparableWith(KIM, true)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("친구라도 프로필이 비공개면 없는 사람처럼 보인다")
        void privateFriend() {
            assertThatThrownBy(() -> COMPARE.requireComparableWith(KIM, false)).hasFieldOrPropertyWithValue("code", "PROFILE_NOT_FOUND");
        }

        @Test
        @DisplayName("한쪽으로만 팔로우해도 공개 프로필이면 비교할 수 있다")
        void publicOneWay() {
            assertThatCode(() -> COMPARE.requireComparableWith(LEE, true)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("프로필이 보이지 않는 사람은 없는 사람처럼 보인다")
        void hiddenProfile() {
            assertThatThrownBy(() -> COMPARE.requireComparableWith(LEE, false)).hasFieldOrPropertyWithValue("code", "PROFILE_NOT_FOUND");
            assertThatThrownBy(() -> COMPARE.requireComparableWith(PARK, false)).hasFieldOrPropertyWithValue("code", "PROFILE_NOT_FOUND");
        }

        @Test
        @DisplayName("자기 자신과는 비교할 수 없다")
        void self() {
            assertThatThrownBy(() -> COMPARE.requireComparableWith(ME, true)).hasFieldOrPropertyWithValue("code", "CANNOT_COMPARE_SELF");
        }
    }
}
