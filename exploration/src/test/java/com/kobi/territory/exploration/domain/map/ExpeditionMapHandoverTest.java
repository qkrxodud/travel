package com.kobi.territory.exploration.domain.map;

import static com.kobi.territory.exploration.domain.Fixtures.FRIEND;
import static com.kobi.territory.exploration.domain.Fixtures.MAP;
import static com.kobi.territory.exploration.domain.Fixtures.ME;
import static com.kobi.territory.exploration.domain.Fixtures.NOON;
import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.common.model.ExplorerId;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * 4단계 D1: 병합 때 공유 지도 자리 정리 — 익명 탐험가 A(FRIEND)가 계정 탐험가 B(ACCOUNT)로 병합된다.
 * B 가 멤버가 아니면 B 가 A 의 자리(역할·가입 시각)를 잇고, B 가 이미 멤버면 A 만 빠진다(지도장이었으면 B 가 지도장). 사용자 결정 Q2:
 * A 의 방문·선점은 재귀속(MemberReassigned)으로 가므로 A 의 탈퇴 유예 기록은 만들지 않는다.
 */
class ExpeditionMapHandoverTest {

    static final ExplorerId ACCOUNT = ExplorerId.of("88888888-8888-8888-8888-888888888888");
    static final ExplorerId THIRD = ExplorerId.of("55555555-5555-5555-5555-555555555555");
    static final Duration GRACE = Duration.ofDays(7);

    static Instant at(int hours) {
        return NOON.plus(Duration.ofHours(hours));
    }

    /** ME 가 지도장, FRIEND 가 멤버(1시간 뒤 합류). */
    static ExpeditionMap map() {
        ExpeditionMap map = ExpeditionMap.create(MAP, ME, "원정대", CountryCode.KR, new InviteCode("ABCDEFGH"), MapKind.SHARED,
            MapSettings.defaults(5), NOON);
        map.join(FRIEND, at(1), GRACE);
        return map;
    }

    @Test
    void 계정_탐험가가_멤버가_아니면_자리를_잇고_익명은_탈퇴_기록_없이_빠진다() {
        ExpeditionMap map = map();
        Handover handover = map.handOver(FRIEND, ACCOUNT, at(5), GRACE).orElseThrow();
        assertThat(handover.joined()).map(Member::explorerId).contains(ACCOUNT);
        assertThat(handover.joined()).map(Member::role).contains(MemberRole.MEMBER);
        assertThat(handover.joined()).map(Member::joinedAt).contains(at(1)); // A 의 가입 시각(온보딩 예외 재사용 방지)
        assertThat(handover.rejoined()).isFalse();
        assertThat(map.memberIds()).containsExactly(ME, ACCOUNT);
        assertThat(map.departures()).isEmpty();
    }

    @Test
    void 익명이_지도장이었으면_계정_탐험가가_지도장() {
        ExpeditionMap map = map();
        Handover handover = map.handOver(ME, ACCOUNT, at(5), GRACE).orElseThrow();
        assertThat(handover.joined()).map(Member::role).contains(MemberRole.OWNER);
        assertThat(map.ownerId()).isEqualTo(ACCOUNT);
        assertThat(map.member(ACCOUNT)).map(Member::owner).contains(true);
        assertThat(map.memberIds()).containsExactlyInAnyOrder(ACCOUNT, FRIEND);
    }

    @Test
    void 계정_탐험가가_이미_멤버면_익명만_탈퇴_지도장이었으면_넘긴다() {
        ExpeditionMap map = map();
        map.join(ACCOUNT, at(2), GRACE);
        Handover handover = map.handOver(ME, ACCOUNT, at(5), GRACE).orElseThrow();
        assertThat(handover.joined()).isEmpty();
        assertThat(map.ownerId()).isEqualTo(ACCOUNT);
        assertThat(map.memberIds()).containsExactly(FRIEND, ACCOUNT);
        assertThat(map.departures()).isEmpty();
    }

    @Test
    void 계정_탐험가가_유예_중_탈퇴_기록이_있으면_재가입으로_원래_가입_시각() {
        ExpeditionMap map = map();
        map.join(ACCOUNT, at(2), GRACE);
        map.leave(ACCOUNT, at(3));
        Handover handover = map.handOver(FRIEND, ACCOUNT, at(4), GRACE).orElseThrow();
        assertThat(handover.rejoined()).isTrue();
        assertThat(handover.joined()).map(Member::joinedAt).contains(at(2));
        assertThat(map.departures()).isEmpty();
    }

    @Test
    void 유예가_끝난_예전_탈퇴_기록은_지우고_새로_잇는다() {
        ExpeditionMap map = map();
        map.join(ACCOUNT, at(2), GRACE);
        map.leave(ACCOUNT, at(3));
        Handover handover = map.handOver(FRIEND, ACCOUNT, at(3).plus(GRACE).plusSeconds(1), GRACE).orElseThrow();
        assertThat(handover.rejoined()).isFalse();
        assertThat(handover.purgedDeparture()).map(Departure::explorerId).contains(ACCOUNT);
        assertThat(handover.joined()).map(Member::joinedAt).contains(at(1));
    }

    @Test
    void 가득_찬_지도도_익명이_비운_자리라_들어간다() {
        ExpeditionMap map = map();
        map.join(THIRD, at(2), GRACE);
        map.join(ExplorerId.of("99999999-9999-9999-9999-999999999999"), at(3), GRACE);
        assertThat(map.members()).hasSize(4);
        assertThat(map.handOver(FRIEND, ACCOUNT, at(4), GRACE)).isPresent();
        assertThat(map.members()).hasSize(4);
    }

    @Test
    void 익명이_이미_멤버가_아니면_아무것도_하지_않는다_멱등() {
        ExpeditionMap map = map();
        map.handOver(FRIEND, ACCOUNT, at(5), GRACE);
        assertThat(map.handOver(FRIEND, ACCOUNT, at(6), GRACE)).isEmpty();
        assertThat(map.memberIds()).containsExactly(ME, ACCOUNT);
    }
}
