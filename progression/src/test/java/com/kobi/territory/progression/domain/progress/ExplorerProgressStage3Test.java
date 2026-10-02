package com.kobi.territory.progression.domain.progress;

import static com.kobi.territory.progression.domain.Fixtures.GAPYEONG;
import static com.kobi.territory.progression.domain.Fixtures.JONGNO;
import static com.kobi.territory.progression.domain.Fixtures.MAP;
import static com.kobi.territory.progression.domain.Fixtures.MAP2;
import static com.kobi.territory.progression.domain.Fixtures.ME;
import static com.kobi.territory.progression.domain.Fixtures.POLICY;
import static com.kobi.territory.progression.domain.Fixtures.T0;
import static com.kobi.territory.progression.domain.Fixtures.province;
import static com.kobi.territory.progression.domain.Fixtures.rarity;
import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.common.model.RegionCode;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 3단계: 방문 회차(결정 6)·선점 이전 보너스·탈퇴한 지도의 활성 유지(재계산 출발점). */
class ExplorerProgressStage3Test {

    static Instant at(int sec) {
        return T0.plusSeconds(sec);
    }

    static ProgressVisit visit(String map, RegionCode code, int sec, int generation) {
        return new ProgressVisit(map, code, province(code), rarity(code), at(sec), false, generation);
    }

    @Nested
    @DisplayName("방문 회차 — 오래된 회차 이벤트 무시(결정 6)")
    class Generations {
        @Test
        void 취소_뒤_늦게_다시_온_예전_회차_체크인은_무시한다() {
            ExplorerProgress progress = ExplorerProgress.start(ME, POLICY, T0);
            progress.applyVisit(visit(MAP, JONGNO, 1, 1), POLICY);
            progress.revokeVisit(MAP, JONGNO, 1, at(2), POLICY);
            long afterCancel = progress.xp();
            ProgressChange redelivered = progress.applyVisit(visit(MAP, JONGNO, 1, 1), POLICY);
            assertThat(redelivered.xpDelta()).isZero();
            assertThat(progress.xp()).isEqualTo(afterCancel);
            assertThat(progress.regions().find(JONGNO).orElseThrow().active()).isFalse();
        }

        @Test
        void 새_회차_뒤에_온_예전_회차_취소는_무시한다() {
            ExplorerProgress progress = ExplorerProgress.start(ME, POLICY, T0);
            progress.applyVisit(visit(MAP, JONGNO, 1, 1), POLICY);
            progress.revokeVisit(MAP, JONGNO, 1, at(2), POLICY);
            progress.applyVisit(visit(MAP, JONGNO, 3, 2), POLICY); // 2회차 다시 칠함 → 기본 XP 다시(#2)
            long xp = progress.xp();
            assertThat(progress.revokeVisit(MAP, JONGNO, 1, at(4), POLICY).xpDelta()).isZero(); // 1회차 취소 재전달
            assertThat(progress.xp()).isEqualTo(xp);
            assertThat(progress.regions().find(JONGNO).orElseThrow().active()).isTrue();
            assertThat(progress.revokeVisit(MAP, JONGNO, 2, at(5), POLICY).xpDelta()).isEqualTo(-10); // 2회차 취소는 반영
        }

        @Test
        void 회차_0인_예전_이벤트는_더_새_회차_표시가_있으면_무시한다_Q3() {
            ExplorerProgress progress = ExplorerProgress.start(ME, POLICY, T0);
            progress.applyVisit(visit(MAP, JONGNO, 1, 1), POLICY);
            assertThat(progress.revokeVisit(MAP, JONGNO, 0, at(2), POLICY).xpDelta()).isZero();
            assertThat(progress.regions().find(JONGNO).orElseThrow().active()).isTrue();
            assertThat(progress.applyVisit(visit(MAP2, JONGNO, 3, 0), POLICY).xpDelta()).isZero(); // MAP2 는 표시 없음 → 반영(기본 XP 는 이미 활성)
            assertThat(progress.regions().find(JONGNO).orElseThrow().activeMapCount()).isEqualTo(2);
        }

        @Test
        void 재계산_출발점은_지금_멤버인_지도의_회차_표시를_비워_재생이_다시_채운다_P3_4() {
            ExplorerProgress progress = ExplorerProgress.start(ME, POLICY, T0);
            progress.applyVisit(visit(MAP, JONGNO, 1, 3), POLICY);
            progress.applyVisit(visit(MAP2, GAPYEONG, 2, 1), POLICY);
            ExplorerProgress base = progress.rebuildBase(Set.of(MAP));
            assertThat(base.regions().find(JONGNO).orElseThrow().marks()).isEmpty();
            assertThat(base.regions().find(GAPYEONG).orElseThrow().marks()).containsEntry(MAP2, 1);
            base.applyVisit(visit(MAP, JONGNO, 1, 3), POLICY);
            assertThat(base.regions().find(JONGNO).orElseThrow().marks()).containsEntry(MAP, 3);
        }

        @Test
        void 회차_0인_예전_이벤트는_판단하지_않고_반영한다() {
            ExplorerProgress progress = ExplorerProgress.start(ME, POLICY, T0);
            progress.applyVisit(visit(MAP, JONGNO, 1, 0), POLICY);
            assertThat(progress.revokeVisit(MAP, JONGNO, 0, at(2), POLICY).xpDelta()).isEqualTo(-10);
            assertThat(progress.applyVisit(visit(MAP, JONGNO, 3, 0), POLICY).xpDelta()).isEqualTo(10);
        }
    }

    @Test
    void 선점_이전은_새_선점자에게_선점_보너스를_한_번만_준다() {
        ExplorerProgress progress = ExplorerProgress.start(ME, POLICY, T0);
        progress.applyVisit(visit(MAP, GAPYEONG, 1, 1), POLICY); // 내가 두 번째로 칠함(선점 아님)
        assertThat(progress.applyClaimTransferred(MAP, GAPYEONG, Rarity.RARE, at(2), POLICY).xpDelta()).isEqualTo(10);
        assertThat(progress.applyClaimTransferred(MAP, GAPYEONG, Rarity.RARE, at(3), POLICY).xpDelta()).isZero();
        assertThat(progress.ledger().has(RefIds.claim(MAP, GAPYEONG, ME))).isTrue();
    }

    @Test
    void 재계산_출발점은_탈퇴한_지도의_활성과_그_기본_XP를_남긴다() {
        ExplorerProgress progress = ExplorerProgress.start(ME, POLICY, T0);
        progress.applyVisit(visit(MAP, JONGNO, 1, 1), POLICY);   // MAP: 지금 멤버
        progress.applyVisit(visit(MAP2, GAPYEONG, 2, 1), POLICY); // MAP2: 탈퇴한 지도
        ExplorerProgress base = progress.rebuildBase(Set.of(MAP));
        assertThat(base.regions().find(JONGNO).orElseThrow().active()).isFalse();   // 재생이 다시 채운다
        assertThat(base.regions().find(GAPYEONG).orElseThrow().activeMaps()).containsExactly(MAP2); // 탈퇴는 줄이지 않음
        assertThat(base.ledger().has(RefIds.regionGrant(ME, GAPYEONG, 1))).isTrue();
        assertThat(base.ledger().has(RefIds.regionGrant(ME, JONGNO, 1))).isFalse();
    }
}
