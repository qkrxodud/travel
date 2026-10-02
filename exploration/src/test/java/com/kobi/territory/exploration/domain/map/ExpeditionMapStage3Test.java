package com.kobi.territory.exploration.domain.map;

import com.kobi.territory.exploration.domain.policy.CheckInPolicy;
import static com.kobi.territory.exploration.domain.Fixtures.FRIEND;
import static com.kobi.territory.exploration.domain.Fixtures.MAP;
import static com.kobi.territory.exploration.domain.Fixtures.ME;
import static com.kobi.territory.exploration.domain.Fixtures.NOON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.domain.ExplorationError;
import com.kobi.territory.exploration.domain.ExplorationException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 3단계 공유 지도: 합류·탈퇴(유예)·재가입·지도장 넘기기·초대코드·설정. */
class ExpeditionMapStage3Test {

    static final InviteCode CODE = new InviteCode("ABCDEFGH");
    static final Duration GRACE = Duration.ofDays(7);
    static final ExplorerId THIRD = ExplorerId.of("55555555-5555-5555-5555-555555555555");
    static final ExplorerId FOURTH = ExplorerId.of("66666666-6666-6666-6666-666666666666");
    static final ExplorerId FIFTH = ExplorerId.of("77777777-7777-7777-7777-777777777777");

    static ExpeditionMap shared() {
        return ExpeditionMap.create(MAP, ME, "부산 원정대", CountryCode.KR, CODE, MapKind.SHARED, MapSettings.defaults(5), NOON);
    }

    static ExplorationError errorOf(Runnable action) {
        try {
            action.run();
        } catch (ExplorationException exception) {
            return exception.error();
        }
        throw new AssertionError("예외가 나야 한다");
    }

    @Test
    void 합류는_MEMBER로_들어오고_4명을_넘을_수_없으며_중복_불가() {
        ExpeditionMap map = shared();
        JoinResult joined = map.join(FRIEND, NOON.plusSeconds(1), GRACE);
        assertThat(joined.member().role()).isEqualTo(MemberRole.MEMBER);
        assertThat(joined.rejoined()).isFalse();
        assertThat(errorOf(() -> map.join(FRIEND, NOON, GRACE))).isEqualTo(ExplorationError.ALREADY_MEMBER);
        map.join(THIRD, NOON.plusSeconds(2), GRACE);
        map.join(FOURTH, NOON.plusSeconds(3), GRACE);
        assertThat(errorOf(() -> map.join(FIFTH, NOON, GRACE))).isEqualTo(ExplorationError.MAP_FULL);
        assertThat(map.memberIds()).containsExactly(ME, FRIEND, THIRD, FOURTH);
    }

    @Test
    void 개인_지도는_합류_탈퇴_양도할_수_없다() {
        ExpeditionMap personal = ExpeditionMap.create(MAP, ME, "나의 영토", CountryCode.KR, CODE, MapKind.PERSONAL,
            MapSettings.defaults(5), NOON);
        assertThat(errorOf(() -> personal.join(FRIEND, NOON, GRACE))).isEqualTo(ExplorationError.PERSONAL_MAP_ONLY_ME);
        assertThat(errorOf(() -> personal.leave(ME, NOON))).isEqualTo(ExplorationError.PERSONAL_MAP_ONLY_ME);
        assertThat(errorOf(() -> personal.transferOwner(ME, FRIEND))).isEqualTo(ExplorationError.PERSONAL_MAP_ONLY_ME);
    }

    @Test
    void 지도장은_넘긴_뒤에만_탈퇴할_수_있다() {
        ExpeditionMap map = shared();
        map.join(FRIEND, NOON, GRACE);
        assertThat(errorOf(() -> map.leave(ME, NOON))).isEqualTo(ExplorationError.OWNER_CANNOT_LEAVE);
        assertThat(errorOf(() -> map.transferOwner(FRIEND, FRIEND))).isEqualTo(ExplorationError.OWNER_ONLY);
        map.transferOwner(ME, FRIEND);
        assertThat(map.ownerId()).isEqualTo(FRIEND);
        assertThat(map.member(ME)).map(Member::role).contains(MemberRole.MEMBER);
        Departure departure = map.leave(ME, NOON.plusSeconds(60));
        assertThat(departure.purgeAfter(GRACE)).isEqualTo(NOON.plusSeconds(60).plus(GRACE));
        assertThat(map.member(ME)).isEmpty();
        assertThat(map.departures()).hasSize(1);
        assertThat(errorOf(() -> map.transferOwner(FRIEND, ME))).isEqualTo(ExplorationError.NOT_A_MEMBER);
    }

    @Test
    void 유예_안_재가입은_원래_가입_시각으로_돌아온다() {
        ExpeditionMap map = shared();
        Instant joinedAt = NOON.plusSeconds(10);
        map.join(FRIEND, joinedAt, GRACE);
        map.leave(FRIEND, NOON.plusSeconds(100));
        JoinResult rejoin = map.join(FRIEND, NOON.plus(Duration.ofDays(6)), GRACE);
        assertThat(rejoin.rejoined()).isTrue();
        assertThat(rejoin.member().joinedAt()).isEqualTo(joinedAt);
        assertThat(rejoin.purgedDeparture()).isEmpty();
        assertThat(map.departures()).isEmpty();
    }

    @Test
    void 유예가_끝난_뒤_합류는_새_멤버이고_남은_탈퇴_기록을_지운다() {
        ExpeditionMap map = shared();
        map.join(FRIEND, NOON, GRACE);
        map.leave(FRIEND, NOON);
        Instant later = NOON.plus(GRACE).plusSeconds(1);
        JoinResult join = map.join(FRIEND, later, GRACE);
        assertThat(join.rejoined()).isFalse();
        assertThat(join.member().joinedAt()).isEqualTo(later);
        assertThat(join.purgedDeparture()).map(Departure::explorerId).isEqualTo(Optional.of(FRIEND));
    }

    @Test
    void 유예가_끝난_기록만_배치가_지운다() {
        ExpeditionMap map = shared();
        map.join(FRIEND, NOON, GRACE);
        map.join(THIRD, NOON, GRACE);
        map.leave(FRIEND, NOON);
        map.leave(THIRD, NOON.plus(Duration.ofDays(3)));
        assertThat(map.purgeExpired(NOON.plus(GRACE), GRACE)).extracting(Departure::explorerId).containsExactly(FRIEND);
        assertThat(map.departures()).extracting(Departure::explorerId).containsExactly(THIRD);
    }

    @Test
    void 초대코드_재발급과_설정_변경은_지도장만() {
        ExpeditionMap map = shared();
        map.join(FRIEND, NOON, GRACE);
        InviteCode next = new InviteCode("HGFEDCBA");
        assertThat(errorOf(() -> map.regenerateInviteCode(FRIEND, next))).isEqualTo(ExplorationError.OWNER_ONLY);
        map.regenerateInviteCode(ME, next);
        assertThat(map.inviteCode()).isEqualTo(next);
        MapSettings strict = new MapSettings(true, 2, MapVisibility.FRIENDS);
        assertThat(errorOf(() -> map.changeSettings(FRIEND, strict, 5))).isEqualTo(ExplorationError.OWNER_ONLY);
        map.changeSettings(ME, strict, 5);
        assertThat(map.checkInPolicy(Duration.ofHours(72))).isEqualTo(new CheckInPolicy(2, Duration.ofHours(72), true));
    }

    @Test
    void 설정값_범위와_초대코드_입력_정규화() {
        assertThatThrownBy(() -> new MapSettings(false, 0, MapVisibility.PRIVATE)).isInstanceOf(ExplorationException.class);
        // 하루 상한은 기본값(5) 이하로 낮추기만(Q1), 개인 지도는 설정 변경 불가
        ExpeditionMap map = shared();
        assertThat(errorOf(() -> map.changeSettings(ME, new MapSettings(false, 6, MapVisibility.PRIVATE), 5)))
            .isEqualTo(ExplorationError.INVALID_SETTINGS);
        ExpeditionMap personal = ExpeditionMap.create(MAP, ME, "나의 영토", CountryCode.KR, CODE, MapKind.PERSONAL,
            MapSettings.defaults(5), NOON);
        assertThat(errorOf(() -> personal.changeSettings(ME, new MapSettings(true, 3, MapVisibility.PRIVATE), 5)))
            .isEqualTo(ExplorationError.PERSONAL_MAP_ONLY_ME);
        assertThat(InviteCode.parse(" abcdefgh ")).contains(CODE);
        assertThat(InviteCode.parse("ABC")).isEmpty();
        assertThat(InviteCode.parse("ABCDEFG0")).isEmpty(); // 0 은 쓰지 않는 글자
        assertThat(MapVisibility.parseOrPrivate(null)).isEqualTo(MapVisibility.PRIVATE);
        assertThat(MapVisibility.parseOrPrivate("friends")).isEqualTo(MapVisibility.FRIENDS);
    }
}
