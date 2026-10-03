package com.kobi.territory.sharing.domain.privacy;

import static com.kobi.territory.sharing.domain.Fixtures.FRIEND;
import static com.kobi.territory.sharing.domain.Fixtures.OWNER;
import static com.kobi.territory.sharing.domain.Fixtures.STRANGER;
import static com.kobi.territory.sharing.domain.Fixtures.T0;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.common.model.ExplorerId;
import java.util.function.Predicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 회귀 출처: 4단계 사용자 결정 Q1(기본 비공개) · 5단계 친구 공개(맞팔로우) · 리더 결정 2(주인 미리보기). */
@DisplayName("공개 범위")
class PrivacySettingsTest {

    /** 주인과 서로 팔로우한 사람은 FRIEND 뿐이다. */
    static final Predicate<ExplorerId> MUTUAL_WITH_OWNER = FRIEND::equals;

    static PrivacySettings settingsAt(ProfileVisibility visibility) {
        PrivacySettings settings = PrivacySettings.defaults(OWNER);
        settings.change(visibility, T0);
        return settings;
    }

    @Nested
    @DisplayName("처음에는")
    class Defaults {

        @Test
        @DisplayName("비공개다")
        void privateByDefault() {
            assertThat(PrivacySettings.defaults(OWNER).visibility()).isEqualTo(ProfileVisibility.PRIVATE);
        }

        @Test
        @DisplayName("공개 경로에서 없는 사람처럼 보인다")
        void hiddenFromPublic() {
            assertThatThrownBy(PrivacySettings.defaults(OWNER)::requireVisibleToPublic)
                .hasFieldOrPropertyWithValue("code", "PROFILE_NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("공개하기를 켜면")
    class Public {

        @Test
        @DisplayName("공개 경로에서 누구에게나 보인다")
        void visibleToPublic() {
            assertThatCode(settingsAt(ProfileVisibility.PUBLIC)::requireVisibleToPublic).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("로그인하지 않은 사람에게도 친구 관계를 묻지 않고 보인다")
        void anonymousWithoutAskingFriendship() {
            assertThat(settingsAt(ProfileVisibility.PUBLIC)
                .visibleTo(null, viewer -> { throw new AssertionError("전체 공개는 친구 관계를 묻지 않는다"); })).isTrue();
        }
    }

    @Nested
    @DisplayName("친구에게만 공개하면")
    class Friends {

        @Test
        @DisplayName("서로 팔로우한 친구에게 보인다")
        void visibleToMutualFriend() {
            PrivacySettings settings = settingsAt(ProfileVisibility.FRIENDS);

            assertThat(settings.visibleTo(FRIEND, MUTUAL_WITH_OWNER)).isTrue();
            assertThatCode(() -> settings.requireVisibleTo(FRIEND, MUTUAL_WITH_OWNER)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("친구가 아닌 사람에게는 없는 사람처럼 보인다")
        void hiddenFromStranger() {
            PrivacySettings settings = settingsAt(ProfileVisibility.FRIENDS);

            assertThat(settings.visibleTo(STRANGER, MUTUAL_WITH_OWNER)).isFalse();
            assertThatThrownBy(() -> settings.requireVisibleTo(STRANGER, MUTUAL_WITH_OWNER))
                .hasFieldOrPropertyWithValue("code", "PROFILE_NOT_FOUND");
        }

        @Test
        @DisplayName("로그인하지 않은 사람은 친구가 아니다")
        void anonymousIsNotFriend() {
            assertThat(settingsAt(ProfileVisibility.FRIENDS).visibleTo(null, MUTUAL_WITH_OWNER)).isFalse();
        }

        @Test
        @DisplayName("로그인 없이 여는 공개 경로에서는 없는 사람처럼 보인다")
        void hiddenOnPublicPath() {
            PrivacySettings settings = settingsAt(ProfileVisibility.FRIENDS);

            assertThat(settings.visibleToPublic()).isFalse();
            assertThatThrownBy(settings::requireVisibleToPublic).hasFieldOrPropertyWithValue("code", "PROFILE_NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("비공개로 두면")
    class Private {

        @Test
        @DisplayName("서로 팔로우한 친구에게도 안 보인다")
        void hiddenFromFriend() {
            assertThat(settingsAt(ProfileVisibility.PRIVATE).visibleTo(FRIEND, MUTUAL_WITH_OWNER)).isFalse();
        }

        @Test
        @DisplayName("공개 경로에서도 없는 사람처럼 보인다")
        void hiddenOnPublicPath() {
            PrivacySettings settings = settingsAt(ProfileVisibility.FRIENDS);
            settings.change(ProfileVisibility.PRIVATE, T0);

            assertThatThrownBy(settings::requireVisibleToPublic).hasFieldOrPropertyWithValue("code", "PROFILE_NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("주인 본인은")
    class Owner {

        @Test
        @DisplayName("비공개여도 자기 프로필을 미리 볼 수 있다")
        void ownerSeesPrivate() {
            assertThat(settingsAt(ProfileVisibility.PRIVATE).visibleTo(OWNER, MUTUAL_WITH_OWNER)).isTrue();
        }

        @Test
        @DisplayName("친구 공개여도 친구 관계와 상관없이 볼 수 있다")
        void ownerSeesFriendsOnly() {
            assertThat(settingsAt(ProfileVisibility.FRIENDS).visibleTo(OWNER, viewer -> false)).isTrue();
        }
    }

    @Nested
    @DisplayName("공개 범위를 바꿀 때")
    class Change {

        @Test
        @DisplayName("다른 범위로 바꾸면 바뀌었다고 알린다")
        void reportsChange() {
            assertThat(PrivacySettings.defaults(OWNER).change(ProfileVisibility.PUBLIC, T0)).isTrue();
        }

        @Test
        @DisplayName("같은 범위로 다시 고르면 바뀌지 않는다")
        void sameVisibilityIsNoChange() {
            assertThat(PrivacySettings.defaults(OWNER).change(ProfileVisibility.PRIVATE, T0)).isFalse();
        }

        @Test
        @DisplayName("범위 이름은 대소문자와 앞뒤 공백을 가리지 않는다")
        void parsesLeniently() {
            assertThat(ProfileVisibility.parse(" public ")).isEqualTo(ProfileVisibility.PUBLIC);
        }

        @Test
        @DisplayName("모르는 범위는 고를 수 없다")
        void rejectsUnknown() {
            assertThatThrownBy(() -> ProfileVisibility.parse("everyone")).hasFieldOrPropertyWithValue("code", "INVALID_VISIBILITY");
        }
    }
}
