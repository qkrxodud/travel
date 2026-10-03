package com.kobi.territory.social.domain.friendship;

import static com.kobi.territory.social.domain.Fixtures.KIM;
import static com.kobi.territory.social.domain.Fixtures.LEE;
import static com.kobi.territory.social.domain.Fixtures.ME;
import static com.kobi.territory.social.domain.Fixtures.PARK;
import static com.kobi.territory.social.domain.Fixtures.T0;
import static com.kobi.territory.social.domain.Fixtures.follows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 회귀 출처: 5단계 QA P3-3(동시 팔로우 경합) · r2 P2-A(숨은 대상 팔로우는 존재를 알리지 않음). */
@DisplayName("팔로우")
class FollowingsTest {

    static Followings mine() {
        return Followings.of(ME, true, List.of());
    }

    @Nested
    @DisplayName("보이는 사람을 팔로우하면")
    class Visible {

        @Test
        @DisplayName("팔로우가 시작되고 그 사람이 보였다고 알린다")
        void startsFollowing() {
            FollowResult result = mine().follow(KIM, true, T0);

            assertThat(result.revealed()).isTrue();
            assertThat(result.started()).contains(follows(ME, KIM));
        }

        @Test
        @DisplayName("내 팔로잉에 들어간다")
        void addedToFollowing() {
            Followings followings = mine();
            followings.follow(KIM, true, T0);

            assertThat(followings.contains(KIM)).isTrue();
        }

        @Test
        @DisplayName("동시에 두 번 팔로우해 겹치면 이미 팔로우 중이라고 알린다")
        void raceReportsAlreadyFollowing() {
            assertThat(mine().follow(PARK, true, T0).duplicateError().code()).isEqualTo("ALREADY_FOLLOWING");
        }
    }

    @Nested
    @DisplayName("숨은 사람을 팔로우하면")
    class Hidden {

        @Test
        @DisplayName("팔로우는 기록해 두어 그가 나를 팔로우하면 친구가 된다")
        void recordsFollow() {
            assertThat(mine().follow(LEE, false, T0).started()).contains(follows(ME, LEE));
        }

        @Test
        @DisplayName("그 사람이 있다는 것을 알리지 않는다")
        void doesNotReveal() {
            assertThat(mine().follow(LEE, false, T0).revealed()).isFalse();
        }

        @Test
        @DisplayName("다시 팔로우해도 이미 팔로우 중이라고 알리지 않고 같은 결과를 낸다")
        void repeatLooksTheSame() {
            Followings followings = mine();
            followings.follow(LEE, false, T0);

            FollowResult again = followings.follow(LEE, false, T0);

            assertThat(again.revealed()).isFalse();
            assertThat(again.started()).isEmpty();
        }

        @Test
        @DisplayName("동시에 두 번 팔로우해 겹쳐도 없는 사람처럼 보인다")
        void raceLooksLikeMissingProfile() {
            assertThat(mine().follow(LEE, false, T0).duplicateError().code()).isEqualTo("PROFILE_NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("팔로우할 수 없는 경우")
    class Rejections {

        @Test
        @DisplayName("로그인하지 않으면 팔로우할 수 없다")
        void loginRequired() {
            assertThatThrownBy(() -> Followings.of(ME, false, List.of()).follow(KIM, true, T0))
                .hasFieldOrPropertyWithValue("code", "LOGIN_REQUIRED");
        }

        @Test
        @DisplayName("로그인하지 않으면 친구 관계를 다룰 수 없다")
        void accountRequired() {
            assertThatThrownBy(() -> Followings.of(ME, false, List.of()).requireAccount())
                .hasFieldOrPropertyWithValue("code", "LOGIN_REQUIRED");
        }

        @Test
        @DisplayName("자기 자신은 팔로우할 수 없다")
        void self() {
            assertThatThrownBy(() -> mine().follow(ME, true, T0)).hasFieldOrPropertyWithValue("code", "CANNOT_FOLLOW_SELF");
        }

        @Test
        @DisplayName("저장된 관계라도 자기 자신을 팔로우할 수는 없다")
        void selfRestored() {
            assertThatThrownBy(() -> follows(ME, ME)).hasFieldOrPropertyWithValue("code", "CANNOT_FOLLOW_SELF");
        }

        @Test
        @DisplayName("이미 팔로우 중인 보이는 사람은 다시 팔로우할 수 없다")
        void alreadyFollowing() {
            Followings followings = mine();
            followings.follow(KIM, true, T0);

            assertThatThrownBy(() -> followings.follow(KIM, true, T0)).hasFieldOrPropertyWithValue("code", "ALREADY_FOLLOWING");
        }

        @Test
        @DisplayName("다른 사람의 팔로우는 내 팔로잉이 될 수 없다")
        void foreignFollow() {
            assertThatThrownBy(() -> Followings.of(ME, true, List.of(follows(KIM, LEE)))).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("언팔로우하면")
    class Unfollow {

        @Test
        @DisplayName("팔로잉에서 빠진다")
        void removes() {
            Followings followings = Followings.of(ME, true, List.of(follows(ME, KIM)));

            assertThat(followings.unfollow(KIM)).contains(follows(ME, KIM));
            assertThat(followings.contains(KIM)).isFalse();
        }

        @Test
        @DisplayName("이미 언팔로우했으면 아무 일도 없다")
        void idempotent() {
            Followings followings = Followings.of(ME, true, List.of(follows(ME, KIM)));
            followings.unfollow(KIM);

            assertThat(followings.unfollow(KIM)).isEmpty();
        }
    }
}
