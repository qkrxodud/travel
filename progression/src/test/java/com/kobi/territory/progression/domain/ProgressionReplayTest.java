package com.kobi.territory.progression.domain;

import static com.kobi.territory.progression.domain.Fixtures.GAPYEONG;
import static com.kobi.territory.progression.domain.Fixtures.JONGNO;
import static com.kobi.territory.progression.domain.Fixtures.JUNG;
import static com.kobi.territory.progression.domain.Fixtures.MAP;
import static com.kobi.territory.progression.domain.Fixtures.ME;
import static com.kobi.territory.progression.domain.Fixtures.POLICY;
import static com.kobi.territory.progression.domain.Fixtures.QUESTS;
import static com.kobi.territory.progression.domain.Fixtures.SETS;
import static com.kobi.territory.progression.domain.Fixtures.T0;
import static com.kobi.territory.progression.domain.Fixtures.visit;
import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.common.model.RegionCode;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 재계산 = 이벤트 누적. 같은 애그리거트 메서드를 같은 순서로 부르기 때문. + 복구 규칙(QA P1-2). */
class ProgressionReplayTest {

    static final YearMonth OCT = YearMonth.of(2026, 10);
    static final Instant LATER = T0.plusSeconds(3600);

    static ProgressionReplay.Result replay(ExplorerProgress current, List<ReplayVisit> history, CollectionBook book,
                                           List<QuestBoard> boards) {
        return ProgressionReplay.replay(ME, current, Map.of(MAP, history), book == null ? Map.of() : Map.of(MAP, book),
            boards, POLICY, SETS, QUESTS, OCT, LATER);
    }

    @Test
    void 재생_결과는_이벤트를_하나씩_반영한_결과와_같다() {
        List<RegionCode> codes = List.of(JONGNO, GAPYEONG, JUNG);
        // 이벤트 누적(핸들러 흐름) — 퀘스트 사실은 핸들러처럼 진행의 지역 기록으로 만든다
        ExplorerProgress accumulated = ExplorerProgress.start(ME, POLICY, T0);
        CollectionBook book = CollectionBook.empty(MAP);
        QuestBoard month = QuestBoard.empty(ME, QuestPeriod.of(OCT));
        QuestBoard always = QuestBoard.empty(ME, QuestPeriod.ALL);
        for (int i = 0; i < codes.size(); i++) {
            ProgressVisit visit = visit(codes.get(i), T0.plusSeconds(i));
            QuestFact fact = QuestFact.of(visit, SETS, accumulated.regions());
            accumulated.applyVisit(visit, POLICY);
            book.applyVisit(visit.region(), ME, visit.visitedAt(), SETS)
                .forEach(completion -> accumulated.applySetCompleted(completion.setId(), completion.completedAt(), POLICY));
            month.applyVisit(fact, QUESTS, OCT);
            always.applyVisit(fact, QUESTS, OCT);
        }

        List<ReplayVisit> history = codes.stream()
            .map(code -> new ReplayVisit(ME, visit(code, T0.plusSeconds(codes.indexOf(code))))).toList();
        ProgressionReplay.Result result = replay(ExplorerProgress.start(ME, POLICY, T0), history, null, List.of());

        assertThat(result.progress().xp()).isEqualTo(accumulated.xp());
        assertThat(result.progress().level()).isEqualTo(accumulated.level());
        assertThat(result.progress().badges().keySet()).isEqualTo(accumulated.badges().keySet());
        assertThat(result.progress().titles().keySet()).isEqualTo(accumulated.titles().keySet());
        assertThat(result.progress().streak()).isEqualTo(accumulated.streak());
        assertThat(result.progress().ledger().entries()).extracting(XpLedgerEntry::refId)
            .containsExactlyInAnyOrderElementsOf(accumulated.ledger().entries().stream().map(XpLedgerEntry::refId).toList());
        assertThat(result.collections().get(0).rows()).containsExactlyInAnyOrderElementsOf(book.rows());
        assertThat(result.monthly().rows()).containsExactlyInAnyOrderElementsOf(month.rows());
        assertThat(result.always().rows()).containsExactlyInAnyOrderElementsOf(always.rows());
        assertThat(month.of("mprov").current(QUESTS.require("mprov"))).isEqualTo(1);
    }

    @Test
    void 취소_비대칭으로_남은_보상과_완성_기록은_재계산이_지우지_않는다() {
        ExplorerProgress accumulated = ExplorerProgress.start(ME, POLICY, T0);
        CollectionBook book = CollectionBook.empty(MAP);
        accumulated.applyVisit(visit(JONGNO, T0), POLICY);
        accumulated.applyVisit(visit(JUNG, T0.plusSeconds(1)), POLICY);
        book.applyVisit(JONGNO, ME, T0, SETS);
        book.applyVisit(JUNG, ME, T0.plusSeconds(1), SETS)
            .forEach(completion -> accumulated.applySetCompleted(completion.setId(), completion.completedAt(), POLICY));
        accumulated.revokeVisit(MAP, JUNG, T0.plusSeconds(2), POLICY);
        book.revokeVisit(JUNG, false, SETS);

        ProgressionReplay.Result result = replay(accumulated, List.of(new ReplayVisit(ME, visit(JONGNO, T0))), book, List.of());

        assertThat(result.progress().xp()).isEqualTo(accumulated.xp()).isEqualTo(145); // 35 + 선점 10 + 세트 100
        assertThat(result.progress().badges().keySet()).isEqualTo(accumulated.badges().keySet());
        assertThat(result.progress().regions().find(JUNG).orElseThrow().active()).isFalse();
        assertThat(result.collections().get(0).of("han").completedAt()).isEqualTo(T0.plusSeconds(1));
        assertThat(result.collections().get(0).of("han").have()).isEqualTo(1);
    }

    @Test
    void 복구_규칙_완성_기록은_있는데_세트_보너스가_없으면_지급하고_칭호도_보정한다() {
        // SetCompleted 전달이 영구 실패해 진행엔 보너스가 없고, 도감엔 완성 기록이 있는 상태
        ExplorerProgress lost = ExplorerProgress.start(ME, POLICY, T0);
        lost.applyVisit(visit(JONGNO, T0), POLICY);
        lost.applyVisit(visit(JUNG, T0.plusSeconds(1)), POLICY);
        CollectionBook book = CollectionBook.empty(MAP);
        book.applyVisit(JONGNO, ME, T0, SETS);
        book.applyVisit(JUNG, ME, T0.plusSeconds(1), SETS);
        assertThat(lost.titles()).doesNotContainKey("set-han");

        ProgressionReplay.Result result = replay(lost, List.of(new ReplayVisit(ME, visit(JONGNO, T0)),
            new ReplayVisit(ME, visit(JUNG, T0.plusSeconds(1)))), book, List.of());

        assertThat(result.progress().ledger().has(RefIds.set(ME, "han"))).isTrue();
        assertThat(result.progress().titles()).containsKey("set-han");
        assertThat(result.progress().badges()).containsKey("set1");
        assertThat(result.progress().xp()).isEqualTo(35 + 20 + 100);
    }

    @Test
    void 복구_규칙_받은_퀘스트인데_장부에_XP_가_없으면_지급한다_지난_달_보드도() {
        QuestBoard september = QuestBoard.empty(ME, QuestPeriod.of(YearMonth.of(2026, 9)));
        september.applyVisit(Fixtures.fact(GAPYEONG, true), QUESTS, YearMonth.of(2026, 9));
        september.claim("mgun", QUESTS, T0, YearMonth.of(2026, 9));
        QuestBoard always = QuestBoard.empty(ME, QuestPeriod.ALL);

        ProgressionReplay.Result result = replay(ExplorerProgress.start(ME, POLICY, T0), List.of(), null,
            List.of(september, always));

        assertThat(result.progress().ledger().has(RefIds.quest(ME, september.period(), "mgun"))).isTrue();
        assertThat(result.progress().xp()).isEqualTo(40);
        // 다시 돌려도 한 번만(멱등)
        ProgressionReplay.Result again = replay(result.progress(), List.of(), null, List.of(september, always));
        assertThat(again.progress().xp()).isEqualTo(40);
    }
}
