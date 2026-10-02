package com.kobi.territory.exploration.domain;

import static com.kobi.territory.exploration.domain.Fixtures.FRIEND;
import static com.kobi.territory.exploration.domain.Fixtures.MAP;
import static com.kobi.territory.exploration.domain.Fixtures.ME;
import static com.kobi.territory.exploration.domain.Fixtures.NOON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.common.model.ExplorerId;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExpeditionMapTest {

    private static final InviteCode CODE = new InviteCode("ABCDEFGH");

    private static ExpeditionMap personal() {
        return ExpeditionMap.create(MAP, ME, "나의 영토", CountryCode.KR, CODE, MapKind.PERSONAL, MapSettings.defaults(5), NOON);
    }

    @Test
    void 개인_지도는_생성자_1명이_OWNER이고_설정은_기본값() {
        ExpeditionMap map = personal();
        assertThat(map.members()).containsExactly(new Member(ME, MemberRole.OWNER, NOON));
        assertThat(map.ownerId()).isEqualTo(ME);
        assertThat(map.settings()).isEqualTo(new MapSettings(false, 5, MapVisibility.PRIVATE));
        assertThat(map.kind()).isEqualTo(MapKind.PERSONAL);
        assertThat(map.country()).isEqualTo(CountryCode.KR);
    }

    @Test
    void 기본_상한은_주입된_값을_따른다() {
        ExpeditionMap map = ExpeditionMap.create(MAP, ME, "x", CountryCode.KR, CODE, MapKind.PERSONAL,
            MapSettings.defaults(7), NOON);
        CheckInPolicy policy = map.checkInPolicy(Duration.ofHours(72));
        assertThat(policy.dailyCap()).isEqualTo(7);
        assertThat(policy.onboardingGrace()).isEqualTo(Duration.ofHours(72));
        assertThat(policy.photoRequired()).isFalse();
    }

    @Test
    void 멤버가_아니면_거부한다() {
        assertThat(personal().requireMember(ME).joinedAt()).isEqualTo(NOON);
        assertThatThrownBy(() -> personal().requireMember(FRIEND))
            .satisfies(exception -> assertThat(((ExplorationException) exception).error()).isEqualTo(ExplorationError.NOT_A_MEMBER));
    }

    @Test
    void 멤버는_4명까지() {
        List<Member> five = new ArrayList<>();
        five.add(new Member(ME, MemberRole.OWNER, NOON));
        for (int i = 0; i < 4; i++) five.add(new Member(ExplorerId.newId(), MemberRole.MEMBER, NOON));
        assertThatThrownBy(() -> ExpeditionMap.restore(MAP, "공유", CountryCode.KR, CODE, ME, MapKind.SHARED,
            MapSettings.defaults(5), NOON, five))
            .satisfies(exception -> assertThat(((ExplorationException) exception).error()).isEqualTo(ExplorationError.MAP_FULL));
        ExpeditionMap four = ExpeditionMap.restore(MAP, "공유", CountryCode.KR, CODE, ME, MapKind.SHARED,
            MapSettings.defaults(5), NOON, five.subList(0, 4));
        assertThat(four.members()).hasSize(4);
    }

    @Test
    void OWNER는_정확히_한명이고_중복_멤버는_없다() {
        var twoOwners = List.of(new Member(ME, MemberRole.OWNER, NOON), new Member(FRIEND, MemberRole.OWNER, NOON));
        assertThatThrownBy(() -> ExpeditionMap.restore(MAP, "공유", CountryCode.KR, CODE, ME, MapKind.SHARED,
            MapSettings.defaults(5), NOON, twoOwners)).isInstanceOf(ExplorationException.class);
        var dup = List.of(new Member(ME, MemberRole.OWNER, NOON), new Member(ME, MemberRole.MEMBER, NOON));
        assertThatThrownBy(() -> ExpeditionMap.restore(MAP, "공유", CountryCode.KR, CODE, ME, MapKind.SHARED,
            MapSettings.defaults(5), NOON, dup))
            .satisfies(exception -> assertThat(((ExplorationException) exception).error()).isEqualTo(ExplorationError.ALREADY_MEMBER));
    }

    @Test
    void 개인_지도는_멤버가_1명이다() {
        var two = List.of(new Member(ME, MemberRole.OWNER, NOON), new Member(FRIEND, MemberRole.MEMBER, NOON));
        assertThatThrownBy(() -> ExpeditionMap.restore(MAP, "x", CountryCode.KR, CODE, ME, MapKind.PERSONAL,
            MapSettings.defaults(5), NOON, two)).isInstanceOf(ExplorationException.class);
    }

    @Test
    void 이름은_필수이고_40자_이하() {
        assertThatThrownBy(() -> ExpeditionMap.create(MAP, ME, " ", CountryCode.KR, CODE, MapKind.PERSONAL,
            MapSettings.defaults(5), NOON)).isInstanceOf(ExplorationException.class);
        assertThatThrownBy(() -> ExpeditionMap.create(MAP, ME, "가".repeat(41), CountryCode.KR, CODE, MapKind.PERSONAL,
            MapSettings.defaults(5), NOON)).isInstanceOf(ExplorationException.class);
    }
}
