package com.kobi.territory.exploration.domain.territory;

import static com.kobi.territory.exploration.domain.Fixtures.FRIEND;
import static com.kobi.territory.exploration.domain.Fixtures.GAPYEONG;
import static com.kobi.territory.exploration.domain.Fixtures.JONGNO;
import static com.kobi.territory.exploration.domain.Fixtures.JUNG;
import static com.kobi.territory.exploration.domain.Fixtures.KST;
import static com.kobi.territory.exploration.domain.Fixtures.MAP;
import static com.kobi.territory.exploration.domain.Fixtures.ME;
import static com.kobi.territory.exploration.domain.Fixtures.NOON;
import static com.kobi.territory.exploration.domain.Fixtures.POLICY;
import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.domain.map.MapId;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * 4단계 D1: 병합(claimExplorer) — 익명 탐험가(FRIEND)의 개인 지도 방문을 계정 탐험가(ME)의 개인 지도로 옮긴다.
 * 같은 지역은 방문일이 더 이른 쪽을 남기고(메모·사진은 남는 쪽 것), 다시 해도 같다(멱등).
 */
class TerritoryMergeTest {

    static final MapId ANON_MAP = MapId.of("44444444-4444-4444-4444-444444444444");

    static CheckInContext ctx(Instant now) {
        return new CheckInContext(POLICY, now.minus(Duration.ofHours(1)), now, KST);
    }

    static void visit(Territory territory, com.kobi.territory.common.model.ExplorerId who, RegionSnapshot region, LocalDate date,
                      String memo, int minutes) {
        territory.checkIn(who, region, VisitDate.of(date), Memo.of(memo), PhotoRef.ofNullable(memo.isEmpty() ? null :
            "https://photo.example/" + memo), ctx(NOON.plusSeconds(60L * minutes)));
    }

    @Test
    void 새_지역은_옮기고_같은_지역은_더_이른_방문일_쪽을_남긴다_메모_사진도_그쪽() {
        Territory account = Territory.empty(MAP);
        visit(account, ME, JONGNO, LocalDate.of(2026, 9, 1), "계정메모", 0);
        visit(account, ME, JUNG, LocalDate.of(2026, 3, 1), "계정중구", 1);
        Territory anonymous = Territory.empty(ANON_MAP);
        visit(anonymous, FRIEND, JONGNO, LocalDate.of(2025, 1, 1), "익명메모", 2);  // 더 이름 → 익명 쪽 유지
        visit(anonymous, FRIEND, JUNG, LocalDate.of(2026, 5, 1), "익명중구", 3);    // 더 늦음 → 계정 쪽 유지
        visit(anonymous, FRIEND, GAPYEONG, LocalDate.of(2026, 9, 30), "", 4);      // 새 지역

        MergeSummary summary = account.mergeSummary(anonymous, FRIEND, ME);
        assertThat(summary).isEqualTo(new MergeSummary(3, 1));

        AbsorbResult result = account.absorb(anonymous, FRIEND, ME);
        assertThat(result.added()).containsExactly(GAPYEONG.code());
        assertThat(result.replaced()).containsExactly(JONGNO.code());
        assertThat(result.moved()).isEqualTo(2);

        Visit jongno = account.find(JONGNO.code(), ME).orElseThrow();
        assertThat(jongno.visitDate().value()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(jongno.memo().value()).isEqualTo("익명메모");
        assertThat(jongno.photo().url()).endsWith("익명메모");
        assertThat(jongno.generation()).isEqualTo(2); // 계정 탐험가의 다음 회차(회차는 단조 증가)
        Visit jung = account.find(JUNG.code(), ME).orElseThrow();
        assertThat(jung.memo().value()).isEqualTo("계정중구");
        assertThat(account.find(GAPYEONG.code(), ME).orElseThrow().generation()).isEqualTo(1);
        assertThat(account.visitsOf(FRIEND)).isEmpty(); // 옮긴 방문은 계정 탐험가 것
        assertThat(account.visits()).hasSize(3);
        // 원본(익명 지도)은 읽기만 — 정리는 따로
        assertThat(anonymous.visitsOf(FRIEND)).hasSize(3);
    }

    @Test
    void 다시_흡수해도_결과가_같다_멱등() {
        Territory account = Territory.empty(MAP);
        Territory anonymous = Territory.empty(ANON_MAP);
        visit(anonymous, FRIEND, JONGNO, LocalDate.of(2025, 1, 1), "메모", 0);
        account.absorb(anonymous, FRIEND, ME);
        AbsorbResult again = account.absorb(anonymous, FRIEND, ME);
        assertThat(again.moved()).isZero();
        assertThat(account.visits()).hasSize(1);
        assertThat(account.find(JONGNO.code(), ME).orElseThrow().generation()).isEqualTo(1);
    }

    @Test
    void 방문일이_같으면_계정_쪽을_남긴다() {
        Territory account = Territory.empty(MAP);
        visit(account, ME, JONGNO, LocalDate.of(2026, 1, 1), "계정", 0);
        Territory anonymous = Territory.empty(ANON_MAP);
        visit(anonymous, FRIEND, JONGNO, LocalDate.of(2026, 1, 1), "익명", 1);
        assertThat(account.absorb(anonymous, FRIEND, ME).moved()).isZero();
        assertThat(account.find(JONGNO.code(), ME).orElseThrow().memo().value()).isEqualTo("계정");
    }

    @Test
    void 정리는_그_멤버의_방문을_숨긴_것까지_지우고_다시_해도_안전하다() {
        Territory anonymous = Territory.empty(ANON_MAP);
        visit(anonymous, FRIEND, JONGNO, LocalDate.of(2025, 1, 1), "", 0);
        visit(anonymous, FRIEND, JUNG, LocalDate.of(2025, 1, 2), "", 1);
        anonymous.hideMember(FRIEND, NOON.plusSeconds(3600)); // 숨긴 방문도 대상
        assertThat(anonymous.releaseMember(FRIEND)).extracting(Visit::regionCode)
            .containsExactlyInAnyOrder(JONGNO.code(), JUNG.code());
        assertThat(anonymous.allVisits()).isEmpty();
        assertThat(anonymous.releaseMember(FRIEND)).isEmpty();
    }

    @Test
    void 빈_익명_지도는_아무것도_옮기지_않는다() {
        Territory account = Territory.empty(MAP);
        assertThat(account.mergeSummary(Territory.empty(ANON_MAP), FRIEND, ME)).isEqualTo(new MergeSummary(0, 0));
        assertThat(account.absorb(Territory.empty(ANON_MAP), FRIEND, ME).added()).isEmpty();
        assertThat(RegionCode.of("KR-11010")).isEqualTo(JONGNO.code());
    }

    // ---- 공유 지도 재귀속(사용자 결정 Q2): 선점 순서 유지, 충돌은 선점 순서가 이른 쪽 ----

    static final com.kobi.territory.common.model.ExplorerId OTHER =
        com.kobi.territory.common.model.ExplorerId.of("66666666-6666-6666-6666-666666666666");

    @Test
    void 재귀속은_선점_순서를_유지해_다른_멤버의_선점을_바꾸지_않는다() {
        Territory shared = Territory.empty(MAP);
        visit(shared, FRIEND, JONGNO, LocalDate.of(2026, 9, 1), "익명", 0); // FRIEND 선점
        visit(shared, OTHER, JONGNO, LocalDate.of(2026, 8, 1), "", 1);      // OTHER 는 나중(방문일은 더 일러도 선점 아님)
        visit(shared, OTHER, JUNG, LocalDate.of(2026, 9, 2), "", 2);        // OTHER 선점
        visit(shared, FRIEND, JUNG, LocalDate.of(2026, 9, 3), "", 3);
        Instant friendClaimRank = shared.claimOf(JONGNO.code()).orElseThrow().claimRankAt();

        ReassignResult result = shared.reassignMember(FRIEND, ME);

        assertThat(result.reassigned()).containsExactlyInAnyOrder(JONGNO.code(), JUNG.code());
        assertThat(shared.visitsOf(FRIEND)).isEmpty();
        Visit claim = shared.claimOf(JONGNO.code()).orElseThrow();
        assertThat(claim.checkedInBy()).isEqualTo(ME);             // 선점자가 같은 사람(계정)으로
        assertThat(claim.claimRankAt()).isEqualTo(friendClaimRank); // 순서 그대로
        assertThat(claim.memo().value()).isEqualTo("익명");
        assertThat(shared.claimOf(JUNG.code()).orElseThrow().checkedInBy()).isEqualTo(OTHER); // 남의 선점은 그대로
        assertThat(shared.reassignMember(FRIEND, ME).changed()).isFalse(); // 멱등
    }

    @Test
    void 계정_탐험가도_같은_지역을_칠했으면_선점_순서가_이른_쪽을_남긴다_방문일이_아니라() {
        Territory shared = Territory.empty(MAP);
        visit(shared, ME, JONGNO, LocalDate.of(2025, 1, 1), "계정", 0);     // 계정이 먼저 선점(방문일도 이름)
        visit(shared, FRIEND, JONGNO, LocalDate.of(2026, 9, 1), "익명", 1);
        visit(shared, FRIEND, JUNG, LocalDate.of(2026, 9, 1), "익명중구", 2); // 익명이 먼저
        visit(shared, ME, JUNG, LocalDate.of(2020, 1, 1), "계정중구", 3);     // 계정은 나중(방문일은 훨씬 이름)

        ReassignResult result = shared.reassignMember(FRIEND, ME);

        assertThat(result.dropped()).containsExactly(JONGNO.code());
        assertThat(result.replaced()).containsExactly(JUNG.code());
        assertThat(shared.find(JONGNO.code(), ME).orElseThrow().memo().value()).isEqualTo("계정");
        Visit jung = shared.find(JUNG.code(), ME).orElseThrow();
        assertThat(jung.memo().value()).isEqualTo("익명중구"); // 선점 순서가 이른 익명 쪽(방문일 규칙과 다름)
        assertThat(jung.visitDate().value()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(jung.generation()).isEqualTo(2); // 계정의 다음 회차
        assertThat(shared.visits()).hasSize(2);
    }
}
