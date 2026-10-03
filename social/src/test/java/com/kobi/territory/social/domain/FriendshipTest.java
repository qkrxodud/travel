package com.kobi.territory.social.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.social.domain.friendship.FollowResult;
import com.kobi.territory.social.domain.friendship.Followings;
import com.kobi.territory.social.domain.friendship.FriendRelation;
import com.kobi.territory.social.domain.friendship.Friendship;
import com.kobi.territory.social.domain.friendship.SocialCircle;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** D1: Friendship 불변식(자기 팔로우·중복·로그인·숨은 대상), 맞팔 판정, 관계 목록, 랭킹 대상·비교 허용(공개 범위). */
class FriendshipTest {

    static final ExplorerId ME = ExplorerId.of("00000000-0000-0000-0000-00000000000a");
    static final ExplorerId KIM = ExplorerId.of("00000000-0000-0000-0000-00000000000b");
    static final ExplorerId LEE = ExplorerId.of("00000000-0000-0000-0000-00000000000c");
    static final ExplorerId PARK = ExplorerId.of("00000000-0000-0000-0000-00000000000d");
    static final Instant T0 = Instant.parse("2026-10-03T03:00:00Z");

    static Friendship follow(ExplorerId from, ExplorerId to) {
        return Friendship.restore(from, to, T0);
    }

    @Test
    void 팔로우는_로그인한_사람만_자기_자신은_안되고_보이는_대상의_중복은_409() {
        assertThatThrownBy(() -> Followings.of(ME, false, List.of()).follow(KIM, true, T0))
            .hasFieldOrPropertyWithValue("code", "LOGIN_REQUIRED");
        Followings followings = Followings.of(ME, true, List.of());
        assertThatThrownBy(() -> followings.follow(ME, true, T0)).hasFieldOrPropertyWithValue("code", "CANNOT_FOLLOW_SELF");

        FollowResult result = followings.follow(KIM, true, T0);
        assertThat(result.revealed()).isTrue();
        assertThat(result.started()).contains(follow(ME, KIM));
        assertThat(followings.contains(KIM)).isTrue();
        assertThatThrownBy(() -> followings.follow(KIM, true, T0)).hasFieldOrPropertyWithValue("code", "ALREADY_FOLLOWING");
        assertThatThrownBy(() -> Friendship.restore(ME, ME, T0)).hasFieldOrPropertyWithValue("code", "CANNOT_FOLLOW_SELF");
    }

    @Test
    void 숨은_대상은_팔로우를_기록하되_존재를_알리지_않고_중복도_같은_결과다() {
        Followings followings = Followings.of(ME, true, List.of());
        FollowResult hidden = followings.follow(LEE, false, T0);
        assertThat(hidden.revealed()).isFalse();
        assertThat(hidden.started()).contains(follow(ME, LEE)); // 기록 — 그가 나를 팔로우하면 친구
        FollowResult again = followings.follow(LEE, false, T0);
        assertThat(again.revealed()).isFalse();
        assertThat(again.started()).isEmpty(); // 409 로 존재를 알리지 않는다
        // 저장 경합(동시 요청 PK 위반)도 숨은 대상은 404, 보이는 대상은 409
        assertThat(hidden.duplicateError().code()).isEqualTo("PROFILE_NOT_FOUND");
        assertThat(followings.follow(PARK, true, T0).duplicateError().code()).isEqualTo("ALREADY_FOLLOWING");
        assertThatThrownBy(() -> Followings.of(ME, false, List.of()).requireAccount()).hasFieldOrPropertyWithValue("code", "LOGIN_REQUIRED");
    }

    @Test
    void 언팔로우는_멱등이다() {
        Followings followings = Followings.of(ME, true, List.of(follow(ME, KIM)));
        assertThat(followings.unfollow(KIM)).contains(follow(ME, KIM));
        assertThat(followings.contains(KIM)).isFalse();
        assertThat(followings.unfollow(KIM)).isEmpty();
        assertThatThrownBy(() -> Followings.of(ME, true, List.of(follow(KIM, LEE))))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 친구는_서로_팔로우한_사이다() {
        // 나 → 김(맞팔), 나 → 이(한쪽), 박 → 나(한쪽)
        SocialCircle circle = SocialCircle.of(ME, List.of(follow(ME, KIM), follow(ME, LEE)), List.of(follow(KIM, ME), follow(PARK, ME)));

        assertThat(circle.isMutual(KIM)).isTrue();
        assertThat(circle.isMutual(LEE)).isFalse();
        assertThat(circle.isMutual(PARK)).isFalse();
        assertThat(circle.mutualFriends()).containsExactly(KIM);
        assertThat(circle.rankingMembers(Set.of(KIM))).containsExactly(ME, KIM);
        assertThat(circle.rankingMembers(Set.of())).as("PRIVATE 맞팔 친구는 숨김, 본인은 항상").containsExactly(ME);
        assertThat(circle.following()).containsExactly(KIM, LEE);
        assertThat(circle.relations(Set.of(LEE))).extracting(FriendRelation::explorerId).containsExactly(KIM, LEE, PARK);
        assertThat(circle.relations(Set.of())).as("한쪽 팔로우 + 숨은 프로필은 목록에서 뺀다").extracting(FriendRelation::explorerId)
            .containsExactly(KIM, PARK);
        assertThat(circle.relations(Set.of()).get(0).mutual()).isTrue();
        assertThat(circle.followedBy(PARK)).isTrue();
        assertThat(circle.followedBy(LEE)).isFalse();
        assertThat(circle.relationTo(PARK)).isEqualTo(new FriendRelation(PARK, false, true));

        assertThat(SocialCircle.mutual(ME, KIM, List.of(follow(ME, KIM), follow(KIM, ME)))).isTrue();
        assertThat(SocialCircle.mutual(ME, KIM, List.of(follow(ME, KIM)))).isFalse();
        assertThat(SocialCircle.mutual(ME, KIM, List.of())).isFalse();
    }

    @Test
    void 비교는_프로필이_나에게_보일_때만이고_자기_자신은_안된다() {
        SocialCircle circle = SocialCircle.of(ME, List.of(follow(ME, KIM), follow(ME, LEE)), List.of(follow(KIM, ME)));

        circle.requireComparableWith(KIM, true);       // 맞팔 + FRIENDS(보임)
        assertThatThrownBy(() -> circle.requireComparableWith(KIM, false)) // 맞팔이라도 PRIVATE 면 숨김(리더 결정 1)
            .hasFieldOrPropertyWithValue("code", "PROFILE_NOT_FOUND");
        circle.requireComparableWith(LEE, true);       // 한쪽 팔로우 + 공개 프로필
        assertThatThrownBy(() -> circle.requireComparableWith(LEE, false)).hasFieldOrPropertyWithValue("code", "PROFILE_NOT_FOUND");
        assertThatThrownBy(() -> circle.requireComparableWith(PARK, false)).hasFieldOrPropertyWithValue("code", "PROFILE_NOT_FOUND");
        assertThatThrownBy(() -> circle.requireComparableWith(ME, true)).hasFieldOrPropertyWithValue("code", "CANNOT_COMPARE_SELF");
    }
}
