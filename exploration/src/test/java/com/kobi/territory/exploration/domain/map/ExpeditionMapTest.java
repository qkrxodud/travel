package com.kobi.territory.exploration.domain.map;

import static com.kobi.territory.exploration.domain.Fixtures.ACCOUNT;
import static com.kobi.territory.exploration.domain.Fixtures.CODE;
import static com.kobi.territory.exploration.domain.Fixtures.FIFTH;
import static com.kobi.territory.exploration.domain.Fixtures.FOURTH;
import static com.kobi.territory.exploration.domain.Fixtures.FRIEND;
import static com.kobi.territory.exploration.domain.Fixtures.GRACE;
import static com.kobi.territory.exploration.domain.Fixtures.MAP;
import static com.kobi.territory.exploration.domain.Fixtures.ME;
import static com.kobi.territory.exploration.domain.Fixtures.NOON;
import static com.kobi.territory.exploration.domain.Fixtures.ONBOARDING;
import static com.kobi.territory.exploration.domain.Fixtures.THIRD;
import static com.kobi.territory.exploration.domain.Fixtures.hours;
import static com.kobi.territory.exploration.domain.Fixtures.personalMap;
import static com.kobi.territory.exploration.domain.Fixtures.refusal;
import static com.kobi.territory.exploration.domain.Fixtures.sharedMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.domain.ExplorationError;
import com.kobi.territory.exploration.domain.ExplorationException;
import com.kobi.territory.exploration.domain.policy.CheckInPolicy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 탐험 지도(공유 지도·개인 지도)의 멤버·지도장·설정 규칙. 회귀 출처: 3~4단계 QA(Q1·Q2·P3-5).
 */
@DisplayName("탐험 지도")
class ExpeditionMapTest {

    static ExpeditionMap restored(MapKind kind, List<Member> members) {
        return ExpeditionMap.restore(MAP, "공유", CountryCode.KR, CODE, ME, kind, MapSettings.defaults(5), NOON, members);
    }

    @Nested
    @DisplayName("가입하면 생기는 개인 지도")
    class Personal {

        @Test
        @DisplayName("만든 사람 혼자 지도장인 지도다")
        void ownerAlone() {
            ExpeditionMap map = personalMap();
            assertThat(map.members()).containsExactly(new Member(ME, MemberRole.OWNER, NOON));
            assertThat(map.ownerId()).isEqualTo(ME);
            assertThat(map.kind()).isEqualTo(MapKind.PERSONAL);
            assertThat(map.country()).isEqualTo(CountryCode.KR);
        }

        @Test
        @DisplayName("사진은 선택이고 비공개이며 하루 상한은 기본값이다")
        void defaultSettings() {
            assertThat(personalMap().settings()).isEqualTo(new MapSettings(false, 5, MapVisibility.PRIVATE));
        }

        @Test
        @DisplayName("다른 사람이 합류할 수 없다")
        void noJoin() {
            assertThat(refusal(() -> personalMap().join(FRIEND, NOON, GRACE))).isEqualTo(ExplorationError.PERSONAL_MAP_ONLY_ME);
        }

        @Test
        @DisplayName("탈퇴할 수 없다")
        void noLeave() {
            assertThat(refusal(() -> personalMap().leave(ME, NOON))).isEqualTo(ExplorationError.PERSONAL_MAP_ONLY_ME);
        }

        @Test
        @DisplayName("지도장을 넘길 수 없다")
        void noTransfer() {
            assertThat(refusal(() -> personalMap().transferOwner(ME, FRIEND))).isEqualTo(ExplorationError.PERSONAL_MAP_ONLY_ME);
        }

        @Test
        @DisplayName("설정을 바꿀 수 없다")
        void noSettings() {
            assertThat(refusal(() -> personalMap().changeSettings(ME, new MapSettings(true, 3, MapVisibility.PRIVATE), 5)))
                .isEqualTo(ExplorationError.PERSONAL_MAP_ONLY_ME);
        }

        @Test
        @DisplayName("공개 범위가 전체여도 프로필 링크로 열리지 않는다")
        void notOpenToProfile() {
            ExpeditionMap personal = ExpeditionMap.create(MAP, ME, "나의 지도", CountryCode.KR, CODE, MapKind.PERSONAL,
                new MapSettings(false, 5, MapVisibility.PUBLIC), NOON);
            assertThat(personal.openToProfileOf(ME)).isFalse();
        }

        @Test
        @DisplayName("멤버가 둘인 개인 지도 기록은 불러오지 않는다")
        void twoMembersRefused() {
            var two = List.of(new Member(ME, MemberRole.OWNER, NOON), new Member(FRIEND, MemberRole.MEMBER, NOON));
            assertThatThrownBy(() -> restored(MapKind.PERSONAL, two)).isInstanceOf(ExplorationException.class);
        }
    }

    @Nested
    @DisplayName("지도 이름")
    class Name {

        @Test
        @DisplayName("비워 둘 수 없다")
        void required() {
            assertThatThrownBy(() -> ExpeditionMap.create(MAP, ME, " ", CountryCode.KR, CODE, MapKind.PERSONAL,
                MapSettings.defaults(5), NOON)).isInstanceOf(ExplorationException.class);
        }

        @Test
        @DisplayName("40자를 넘을 수 없다")
        void max40() {
            assertThatThrownBy(() -> ExpeditionMap.create(MAP, ME, "가".repeat(41), CountryCode.KR, CODE, MapKind.PERSONAL,
                MapSettings.defaults(5), NOON)).isInstanceOf(ExplorationException.class);
        }
    }

    @Nested
    @DisplayName("체크인 규칙")
    class CheckInRules {

        @Test
        @DisplayName("하루 상한 기본값은 설정에서 받은 값을 따른다")
        void injectedDefault() {
            ExpeditionMap map = ExpeditionMap.create(MAP, ME, "x", CountryCode.KR, CODE, MapKind.PERSONAL, MapSettings.defaults(7), NOON);
            CheckInPolicy policy = map.checkInPolicy(ONBOARDING);
            assertThat(policy.dailyCap()).isEqualTo(7);
            assertThat(policy.onboardingGrace()).isEqualTo(ONBOARDING);
            assertThat(policy.photoRequired()).isFalse();
        }

        @Test
        @DisplayName("멤버는 칠할 수 있는 사람으로 확인된다")
        void memberAllowed() {
            assertThat(personalMap().requireMember(ME).joinedAt()).isEqualTo(NOON);
        }

        @Test
        @DisplayName("멤버가 아니면 칠할 수 없다")
        void nonMemberRefused() {
            assertThat(refusal(() -> personalMap().requireMember(FRIEND))).isEqualTo(ExplorationError.NOT_A_MEMBER);
        }
    }

    @Nested
    @DisplayName("초대코드로 합류할 때")
    class Join {

        @Test
        @DisplayName("일반 멤버로 들어온다")
        void asMember() {
            JoinResult joined = sharedMap().join(FRIEND, NOON.plusSeconds(1), GRACE);
            assertThat(joined.member().role()).isEqualTo(MemberRole.MEMBER);
            assertThat(joined.rejoined()).isFalse();
        }

        @Test
        @DisplayName("초대한 사람은 지도장이다")
        void invitedByOwner() {
            assertThat(sharedMap(MapVisibility.PRIVATE).join(FRIEND, NOON, GRACE).invitedBy()).isEqualTo(ME);
        }

        @Test
        @DisplayName("멤버는 들어온 순서대로 넷까지다")
        void upToFour() {
            ExpeditionMap map = sharedMap();
            map.join(FRIEND, NOON.plusSeconds(1), GRACE);
            map.join(THIRD, NOON.plusSeconds(2), GRACE);
            map.join(FOURTH, NOON.plusSeconds(3), GRACE);
            assertThat(map.memberIds()).containsExactly(ME, FRIEND, THIRD, FOURTH);
        }

        @Test
        @DisplayName("이미 멤버면 다시 합류할 수 없다")
        void alreadyMember() {
            ExpeditionMap map = sharedMap();
            map.join(FRIEND, NOON, GRACE);
            assertThat(refusal(() -> map.join(FRIEND, NOON, GRACE))).isEqualTo(ExplorationError.ALREADY_MEMBER);
        }

        @Test
        @DisplayName("넷이 찬 지도에는 다섯 번째가 들어올 수 없다")
        void fifthRefused() {
            ExpeditionMap map = sharedMap();
            map.join(FRIEND, NOON, GRACE);
            map.join(THIRD, NOON, GRACE);
            map.join(FOURTH, NOON, GRACE);
            assertThat(refusal(() -> map.join(FIFTH, NOON, GRACE))).isEqualTo(ExplorationError.MAP_FULL);
        }
    }

    @Nested
    @DisplayName("공개 프로필 링크로 합류할 때")
    class ProfileJoin {

        @Test
        @DisplayName("지도장의 프로필에서 전체 공개 지도로 들어올 수 있고 초대한 사람은 프로필 주인이다")
        void ownerPublicMap() {
            assertThat(sharedMap(MapVisibility.PUBLIC).openToProfileOf(ME)).isTrue();
            assertThat(sharedMap(MapVisibility.PUBLIC).joinViaProfile(ME, true, FRIEND, NOON, GRACE).invitedBy()).isEqualTo(ME);
        }

        @Test
        @DisplayName("프로필이 비공개면 지도가 전체 공개여도 찾을 수 없다")
        void privateProfile() {
            assertThat(refusal(() -> sharedMap(MapVisibility.PUBLIC).joinViaProfile(ME, false, FRIEND, NOON, GRACE)))
                .isEqualTo(ExplorationError.PROFILE_MAP_NOT_FOUND);
        }

        @Test
        @DisplayName("비공개 지도는 찾을 수 없다")
        void privateMap() {
            assertThat(refusal(() -> sharedMap(MapVisibility.PRIVATE).joinViaProfile(ME, true, FRIEND, NOON, GRACE)))
                .isEqualTo(ExplorationError.PROFILE_MAP_NOT_FOUND);
        }

        @Test
        @DisplayName("친구 공개 지도도 프로필 링크로는 찾을 수 없다")
        void friendsMap() {
            assertThat(refusal(() -> sharedMap(MapVisibility.FRIENDS).joinViaProfile(ME, true, FRIEND, NOON, GRACE)))
                .isEqualTo(ExplorationError.PROFILE_MAP_NOT_FOUND);
        }

        @Test
        @DisplayName("지도장이 아닌 멤버의 프로필로는 열리지 않는다")
        void nonOwnerProfile() {
            ExpeditionMap map = sharedMap(MapVisibility.PUBLIC);
            map.join(FRIEND, NOON, GRACE);
            assertThat(map.openToProfileOf(FRIEND)).isFalse();
            assertThat(refusal(() -> map.joinViaProfile(FRIEND, true, THIRD, NOON, GRACE))).isEqualTo(ExplorationError.PROFILE_MAP_NOT_FOUND);
        }

        @Test
        @DisplayName("이미 멤버인 사람은 다시 들어올 수 없다")
        void alreadyMember() {
            assertThat(refusal(() -> sharedMap(MapVisibility.PUBLIC).joinViaProfile(ME, true, ME, NOON, GRACE)))
                .isEqualTo(ExplorationError.ALREADY_MEMBER);
        }
    }

    @Nested
    @DisplayName("탈퇴할 때")
    class Leave {

        @Test
        @DisplayName("멤버에서 빠지고 7일 유예가 시작된다")
        void startsGrace() {
            ExpeditionMap map = sharedMap();
            map.join(FRIEND, NOON, GRACE);
            Departure departure = map.leave(FRIEND, NOON.plusSeconds(60));
            assertThat(departure.purgeAfter(GRACE)).isEqualTo(NOON.plusSeconds(60).plus(GRACE));
            assertThat(map.member(FRIEND)).isEmpty();
            assertThat(map.departures()).hasSize(1);
        }

        @Test
        @DisplayName("지도장은 지도장을 넘기기 전에는 탈퇴할 수 없다")
        void ownerCannotLeave() {
            ExpeditionMap map = sharedMap();
            map.join(FRIEND, NOON, GRACE);
            assertThat(refusal(() -> map.leave(ME, NOON))).isEqualTo(ExplorationError.OWNER_CANNOT_LEAVE);
        }

        @Test
        @DisplayName("지도장을 넘긴 뒤에는 탈퇴할 수 있다")
        void ownerLeavesAfterTransfer() {
            ExpeditionMap map = sharedMap();
            map.join(FRIEND, NOON, GRACE);
            map.transferOwner(ME, FRIEND);
            map.leave(ME, NOON.plusSeconds(60));
            assertThat(map.member(ME)).isEmpty();
        }
    }

    @Nested
    @DisplayName("탈퇴한 사람이 다시 합류할 때")
    class Rejoin {

        @Test
        @DisplayName("유예 안이면 원래 가입 시각으로 돌아온다")
        void withinGrace() {
            ExpeditionMap map = sharedMap();
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
        @DisplayName("유예가 끝났으면 새 멤버로 들어오고 남아 있던 탈퇴 기록은 지운다")
        void afterGrace() {
            ExpeditionMap map = sharedMap();
            map.join(FRIEND, NOON, GRACE);
            map.leave(FRIEND, NOON);
            Instant later = NOON.plus(GRACE).plusSeconds(1);
            JoinResult join = map.join(FRIEND, later, GRACE);
            assertThat(join.rejoined()).isFalse();
            assertThat(join.member().joinedAt()).isEqualTo(later);
            assertThat(join.purgedDeparture()).map(Departure::explorerId).isEqualTo(Optional.of(FRIEND));
        }

        @Test
        @DisplayName("유예 중인 사람은 자리를 차지하지 않아 그사이 넷이 차면 돌아올 수 없다")
        void seatNotHeld() {
            ExpeditionMap map = sharedMap();
            map.join(FRIEND, NOON, GRACE);
            map.join(THIRD, NOON, GRACE);
            map.join(FOURTH, NOON, GRACE);
            map.leave(FRIEND, hours(1));
            map.join(FIFTH, hours(2), GRACE);
            assertThat(refusal(() -> map.join(FRIEND, hours(3), GRACE))).isEqualTo(ExplorationError.MAP_FULL);
        }
    }

    @Test
    @DisplayName("유예가 끝난 탈퇴 기록만 정리된다")
    void purgeExpired() {
        ExpeditionMap map = sharedMap();
        map.join(FRIEND, NOON, GRACE);
        map.join(THIRD, NOON, GRACE);
        map.leave(FRIEND, NOON);
        map.leave(THIRD, NOON.plus(Duration.ofDays(3)));
        assertThat(map.purgeExpired(NOON.plus(GRACE), GRACE)).extracting(Departure::explorerId).containsExactly(FRIEND);
        assertThat(map.departures()).extracting(Departure::explorerId).containsExactly(THIRD);
    }

    @Nested
    @DisplayName("지도장 넘기기")
    class TransferOwner {

        @Test
        @DisplayName("넘기면 받은 멤버가 지도장이 되고 나는 일반 멤버가 된다")
        void transfers() {
            ExpeditionMap map = sharedMap();
            map.join(FRIEND, NOON, GRACE);
            map.transferOwner(ME, FRIEND);
            assertThat(map.ownerId()).isEqualTo(FRIEND);
            assertThat(map.member(ME)).map(Member::role).contains(MemberRole.MEMBER);
        }

        @Test
        @DisplayName("지도장만 넘길 수 있다")
        void ownerOnly() {
            ExpeditionMap map = sharedMap();
            map.join(FRIEND, NOON, GRACE);
            assertThat(refusal(() -> map.transferOwner(FRIEND, FRIEND))).isEqualTo(ExplorationError.OWNER_ONLY);
        }

        @Test
        @DisplayName("멤버가 아닌 사람에게는 넘길 수 없다")
        void notMemberTarget() {
            ExpeditionMap map = sharedMap();
            map.join(FRIEND, NOON, GRACE);
            map.transferOwner(ME, FRIEND);
            map.leave(ME, NOON.plusSeconds(60));
            assertThat(refusal(() -> map.transferOwner(FRIEND, ME))).isEqualTo(ExplorationError.NOT_A_MEMBER);
        }

        @Test
        @DisplayName("나 자신에게 넘기면 아무 일도 없다")
        void toSelf() {
            ExpeditionMap map = sharedMap();
            map.transferOwner(ME, ME);
            assertThat(map.ownerId()).isEqualTo(ME);
        }
    }

    @Nested
    @DisplayName("지도장만 할 수 있는 일")
    class OwnerOnly {

        @Test
        @DisplayName("지도장은 초대코드를 다시 발급할 수 있다")
        void regenerate() {
            ExpeditionMap map = sharedMap();
            InviteCode next = new InviteCode("HGFEDCBA");
            map.regenerateInviteCode(ME, next);
            assertThat(map.inviteCode()).isEqualTo(next);
        }

        @Test
        @DisplayName("일반 멤버는 초대코드를 다시 발급할 수 없다")
        void regenerateRefused() {
            ExpeditionMap map = sharedMap();
            map.join(FRIEND, NOON, GRACE);
            assertThat(refusal(() -> map.regenerateInviteCode(FRIEND, new InviteCode("HGFEDCBA")))).isEqualTo(ExplorationError.OWNER_ONLY);
        }

        @Test
        @DisplayName("지도장은 사진 필수·하루 상한·공개 범위를 바꾸고 다음 체크인부터 적용된다")
        void changeSettings() {
            ExpeditionMap map = sharedMap();
            map.changeSettings(ME, new MapSettings(true, 2, MapVisibility.FRIENDS), 5);
            assertThat(map.checkInPolicy(ONBOARDING)).isEqualTo(new CheckInPolicy(2, ONBOARDING, true));
            assertThat(map.settings().visibility()).isEqualTo(MapVisibility.FRIENDS);
        }

        @Test
        @DisplayName("일반 멤버는 설정을 바꿀 수 없다")
        void settingsRefused() {
            ExpeditionMap map = sharedMap();
            map.join(FRIEND, NOON, GRACE);
            assertThat(refusal(() -> map.changeSettings(FRIEND, new MapSettings(true, 2, MapVisibility.FRIENDS), 5)))
                .isEqualTo(ExplorationError.OWNER_ONLY);
        }

        @Test
        @DisplayName("하루 상한은 기본값보다 높일 수 없다")
        void cannotRaiseCap() {
            assertThat(refusal(() -> sharedMap().changeSettings(ME, new MapSettings(false, 6, MapVisibility.PRIVATE), 5)))
                .isEqualTo(ExplorationError.INVALID_SETTINGS);
        }

        @Test
        @DisplayName("일반 멤버는 방문 이의를 걸 권한이 없다")
        void disputeRefused() {
            ExpeditionMap map = sharedMap();
            map.join(FRIEND, NOON, GRACE);
            assertThat(map.requireOwner(ME).owner()).isTrue();
            assertThat(refusal(() -> map.requireOwner(FRIEND))).isEqualTo(ExplorationError.OWNER_ONLY);
        }

        @Test
        @DisplayName("멤버가 아니면 지도장 일을 할 수 없다고 하기 전에 멤버가 아니라고 알린다")
        void nonMember() {
            assertThat(refusal(() -> sharedMap().requireOwner(FRIEND))).isEqualTo(ExplorationError.NOT_A_MEMBER);
        }
    }

    @Nested
    @DisplayName("익명 탐험가가 계정 탐험가로 합쳐질 때 지도 자리")
    class Handover {

        /** 내가 지도장, 친구가 1시간 뒤 합류한 지도. */
        ExpeditionMap map() {
            ExpeditionMap map = sharedMap();
            map.join(FRIEND, hours(1), GRACE);
            return map;
        }

        @Test
        @DisplayName("계정 탐험가가 멤버가 아니면 익명 탐험가의 역할과 가입 시각으로 자리를 잇는다")
        void takesSeat() {
            ExpeditionMap map = map();
            var handover = map.handOver(FRIEND, ACCOUNT, hours(5), GRACE).orElseThrow();
            assertThat(handover.joined()).map(Member::explorerId).contains(ACCOUNT);
            assertThat(handover.joined()).map(Member::role).contains(MemberRole.MEMBER);
            assertThat(handover.joined()).map(Member::joinedAt).contains(hours(1));
            assertThat(handover.rejoined()).isFalse();
            assertThat(map.memberIds()).containsExactly(ME, ACCOUNT);
        }

        @Test
        @DisplayName("익명 탐험가는 탈퇴 유예 기록 없이 빠진다")
        void noDeparture() {
            ExpeditionMap map = map();
            map.handOver(FRIEND, ACCOUNT, hours(5), GRACE);
            assertThat(map.departures()).isEmpty();
        }

        @Test
        @DisplayName("익명 탐험가가 지도장이었으면 계정 탐험가가 지도장이 된다")
        void inheritsOwner() {
            ExpeditionMap map = map();
            var handover = map.handOver(ME, ACCOUNT, hours(5), GRACE).orElseThrow();
            assertThat(handover.joined()).map(Member::role).contains(MemberRole.OWNER);
            assertThat(map.ownerId()).isEqualTo(ACCOUNT);
            assertThat(map.member(ACCOUNT)).map(Member::owner).contains(true);
            assertThat(map.memberIds()).containsExactlyInAnyOrder(ACCOUNT, FRIEND);
        }

        @Test
        @DisplayName("계정 탐험가가 이미 멤버면 익명 탐험가만 빠지고 지도장이었으면 넘겨받는다")
        void alreadyMember() {
            ExpeditionMap map = map();
            map.join(ACCOUNT, hours(2), GRACE);
            var handover = map.handOver(ME, ACCOUNT, hours(5), GRACE).orElseThrow();
            assertThat(handover.joined()).isEmpty();
            assertThat(map.ownerId()).isEqualTo(ACCOUNT);
            assertThat(map.memberIds()).containsExactly(FRIEND, ACCOUNT);
            assertThat(map.departures()).isEmpty();
        }

        @Test
        @DisplayName("계정 탐험가가 유예 중 탈퇴자면 재가입으로 자기 원래 가입 시각을 되찾는다")
        void rejoinsWithinGrace() {
            ExpeditionMap map = map();
            map.join(ACCOUNT, hours(2), GRACE);
            map.leave(ACCOUNT, hours(3));
            var handover = map.handOver(FRIEND, ACCOUNT, hours(4), GRACE).orElseThrow();
            assertThat(handover.rejoined()).isTrue();
            assertThat(handover.joined()).map(Member::joinedAt).contains(hours(2));
            assertThat(map.departures()).isEmpty();
        }

        @Test
        @DisplayName("계정 탐험가의 유예가 끝난 탈퇴 기록은 지우고 익명 탐험가 자리를 새로 잇는다")
        void expiredDeparture() {
            ExpeditionMap map = map();
            map.join(ACCOUNT, hours(2), GRACE);
            map.leave(ACCOUNT, hours(3));
            var handover = map.handOver(FRIEND, ACCOUNT, hours(3).plus(GRACE).plusSeconds(1), GRACE).orElseThrow();
            assertThat(handover.rejoined()).isFalse();
            assertThat(handover.purgedDeparture()).map(Departure::explorerId).contains(ACCOUNT);
            assertThat(handover.joined()).map(Member::joinedAt).contains(hours(1));
        }

        @Test
        @DisplayName("넷이 찬 지도도 익명 탐험가가 비운 자리라 들어간다")
        void fullMap() {
            ExpeditionMap map = map();
            map.join(THIRD, hours(2), GRACE);
            map.join(ExplorerId.of("99999999-9999-9999-9999-999999999999"), hours(3), GRACE);
            assertThat(map.handOver(FRIEND, ACCOUNT, hours(4), GRACE)).isPresent();
            assertThat(map.members()).hasSize(4);
        }

        @Test
        @DisplayName("익명 탐험가가 이미 빠졌으면 다시 처리해도 아무 일도 없다")
        void idempotent() {
            ExpeditionMap map = map();
            map.handOver(FRIEND, ACCOUNT, hours(5), GRACE);
            assertThat(map.handOver(FRIEND, ACCOUNT, hours(6), GRACE)).isEmpty();
            assertThat(map.memberIds()).containsExactly(ME, ACCOUNT);
        }
    }

    @Nested
    @DisplayName("저장된 지도를 불러올 때")
    class Restore {

        @Test
        @DisplayName("멤버 넷까지는 불러온다")
        void four() {
            List<Member> four = new ArrayList<>(List.of(new Member(ME, MemberRole.OWNER, NOON)));
            for (int i = 0; i < 3; i++) four.add(new Member(ExplorerId.newId(), MemberRole.MEMBER, NOON));
            assertThat(restored(MapKind.SHARED, four).members()).hasSize(4);
        }

        @Test
        @DisplayName("멤버가 다섯이면 지도가 가득 찼다고 거절된다")
        void fiveRefused() {
            List<Member> five = new ArrayList<>(List.of(new Member(ME, MemberRole.OWNER, NOON)));
            for (int i = 0; i < 4; i++) five.add(new Member(ExplorerId.newId(), MemberRole.MEMBER, NOON));
            assertThat(refusal(() -> restored(MapKind.SHARED, five))).isEqualTo(ExplorationError.MAP_FULL);
        }

        @Test
        @DisplayName("지도장이 둘이면 불러오지 않는다")
        void twoOwners() {
            var twoOwners = List.of(new Member(ME, MemberRole.OWNER, NOON), new Member(FRIEND, MemberRole.OWNER, NOON));
            assertThatThrownBy(() -> restored(MapKind.SHARED, twoOwners)).isInstanceOf(ExplorationException.class);
        }

        @Test
        @DisplayName("같은 사람이 두 번 있으면 이미 멤버라고 거절된다")
        void duplicate() {
            var dup = List.of(new Member(ME, MemberRole.OWNER, NOON), new Member(ME, MemberRole.MEMBER, NOON));
            assertThat(refusal(() -> restored(MapKind.SHARED, dup))).isEqualTo(ExplorationError.ALREADY_MEMBER);
        }
    }
}
