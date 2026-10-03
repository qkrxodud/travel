package com.kobi.territory.exploration.domain.map;

import static com.kobi.territory.exploration.domain.Fixtures.FRIEND;
import static com.kobi.territory.exploration.domain.Fixtures.MAP;
import static com.kobi.territory.exploration.domain.Fixtures.ME;
import static com.kobi.territory.exploration.domain.Fixtures.NOON;
import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.domain.ExplorationError;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** 4단계: 공개 프로필 링크 합류(지도장 + PUBLIC 공유 지도만)와 초대자 기록(초대 보상). */
class ExpeditionMapProfileJoinTest {

    static final Duration GRACE = Duration.ofDays(7);
    static final ExplorerId THIRD = ExplorerId.of("55555555-5555-5555-5555-555555555555");

    static ExpeditionMap shared(MapVisibility visibility) {
        return ExpeditionMap.create(MAP, ME, "부산 원정대", CountryCode.KR, new InviteCode("ABCDEFGH"), MapKind.SHARED,
            new MapSettings(false, 5, visibility), NOON);
    }

    @Test
    void 초대코드_합류의_초대자는_지도장_프로필_합류의_초대자는_프로필_주인() {
        assertThat(shared(MapVisibility.PRIVATE).join(FRIEND, NOON, GRACE).invitedBy()).isEqualTo(ME);
        assertThat(shared(MapVisibility.PUBLIC).joinViaProfile(ME, true, FRIEND, NOON, GRACE).invitedBy()).isEqualTo(ME);
    }

    @Test
    void 프로필_합류는_지도장의_PUBLIC_공유_지도만_FRIENDS_PRIVATE_다른_멤버_프로필_개인_지도는_404() {
        assertThat(shared(MapVisibility.PUBLIC).openToProfileOf(ME)).isTrue();
        // 프로필이 비공개면 지도가 PUBLIC 이어도 닫힌다(QA P3-5)
        assertThat(errorOf(() -> shared(MapVisibility.PUBLIC).joinViaProfile(ME, false, FRIEND, NOON, GRACE)))
            .isEqualTo(ExplorationError.PROFILE_MAP_NOT_FOUND);
        assertThat(errorOf(() -> shared(MapVisibility.PRIVATE).joinViaProfile(ME, true, FRIEND, NOON, GRACE)))
            .isEqualTo(ExplorationError.PROFILE_MAP_NOT_FOUND);
        assertThat(errorOf(() -> shared(MapVisibility.FRIENDS).joinViaProfile(ME, true, FRIEND, NOON, GRACE)))
            .isEqualTo(ExplorationError.PROFILE_MAP_NOT_FOUND);
        ExpeditionMap map = shared(MapVisibility.PUBLIC);
        map.join(FRIEND, NOON, GRACE);
        assertThat(map.openToProfileOf(FRIEND)).isFalse(); // 지도장이 아닌 멤버의 프로필로는 열리지 않는다
        assertThat(errorOf(() -> map.joinViaProfile(FRIEND, true, THIRD, NOON, GRACE))).isEqualTo(ExplorationError.PROFILE_MAP_NOT_FOUND);
        ExpeditionMap personal = ExpeditionMap.create(MAP, ME, "나의 지도", CountryCode.KR, new InviteCode("ABCDEFGH"),
            MapKind.PERSONAL, new MapSettings(false, 5, MapVisibility.PUBLIC), NOON);
        assertThat(personal.openToProfileOf(ME)).isFalse();
        assertThat(errorOf(() -> shared(MapVisibility.PUBLIC).joinViaProfile(ME, true, ME, NOON, GRACE)))
            .isEqualTo(ExplorationError.ALREADY_MEMBER);
    }

    static ExplorationError errorOf(Runnable action) {
        return ExpeditionMapStage3Test.errorOf(action);
    }
}
