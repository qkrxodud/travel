package com.kobi.territory.progression.domain.replay;

import static com.kobi.territory.progression.domain.Fixtures.가평군;
import static com.kobi.territory.progression.domain.Fixtures.그달에_칠한다;
import static com.kobi.territory.progression.domain.Fixtures.그달의방문;
import static com.kobi.territory.progression.domain.Fixtures.용산구;
import static com.kobi.territory.progression.domain.Fixtures.월간퀘스트를_모두_받는다;
import static com.kobi.territory.progression.domain.Fixtures.기준시각;
import static com.kobi.territory.progression.domain.Fixtures.나;
import static com.kobi.territory.progression.domain.Fixtures.늦게온멤버;
import static com.kobi.territory.progression.domain.Fixtures.방문;
import static com.kobi.territory.progression.domain.Fixtures.새_진행;
import static com.kobi.territory.progression.domain.Fixtures.종로구;
import static com.kobi.territory.progression.domain.Fixtures.중구;
import static com.kobi.territory.progression.domain.Fixtures.지도;
import static com.kobi.territory.progression.domain.Fixtures.진행규칙;
import static com.kobi.territory.progression.domain.Fixtures.초;
import static com.kobi.territory.progression.domain.Fixtures.취소한다;
import static com.kobi.territory.progression.domain.Fixtures.친구;
import static com.kobi.territory.progression.domain.Fixtures.칠한다;
import static com.kobi.territory.progression.domain.Fixtures.퀘스트;
import static com.kobi.territory.progression.domain.Fixtures.퀘스트사실;
import static com.kobi.territory.progression.domain.Fixtures.테마;
import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.progression.domain.collectionbook.CollectionBook;
import com.kobi.territory.progression.domain.progress.ExplorerProgress;
import com.kobi.territory.progression.domain.progress.ProgressVisit;
import com.kobi.territory.progression.domain.progress.RefIds;
import com.kobi.territory.progression.domain.progress.StreakFreezeEntry;
import com.kobi.territory.progression.domain.policy.XpSource;
import com.kobi.territory.progression.domain.progress.XpLedgerEntry;
import com.kobi.territory.progression.domain.quest.QuestBoard;
import com.kobi.territory.progression.domain.quest.QuestFact;
import com.kobi.territory.progression.domain.quest.QuestPeriod;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 진행 재계산: 영토의 방문 이력을 처음부터 다시 반영해 진행·도감·퀘스트를 되살린다.
 * 회귀 출처: QA P1-2(복구 규칙), 3단계 R2-1(완성 시점 멤버만 복구 지급).
 */
@DisplayName("진행 재계산")
class ProgressionReplayTest {

    private static final YearMonth 시월 = YearMonth.of(2026, 10);
    private static final Instant 재계산시각 = 기준시각.plusSeconds(3600);

    private static ProgressionReplay.Result 재계산(ExplorerId who, ExplorerProgress current, List<ReplayVisit> history,
                                               CollectionBook book, List<QuestBoard> boards) {
        return ProgressionReplay.replay(who, current, Map.of(지도, history), book == null ? Map.of() : Map.of(지도, book),
            boards, 진행규칙, 테마, 퀘스트, 시월, 재계산시각);
    }

    private static ProgressionReplay.Result 재계산(ExplorerProgress current, List<ReplayVisit> history, CollectionBook book,
                                               List<QuestBoard> boards) {
        return 재계산(나, current, history, book, boards);
    }

    private static ReplayVisit 내방문(RegionCode code, Instant at) {
        return new ReplayVisit(나, 방문(code).처리시각(at).사실());
    }

    @Nested
    @DisplayName("방문 이력을 다시 반영하면 소식을 하나씩 받은 결과와 같다")
    class SameAsEvents {

        private final List<RegionCode> 칠한순서 = List.of(종로구, 가평군, 중구);

        /** 소식을 하나씩 받은 쪽(핸들러 흐름) — 퀘스트 사실은 핸들러처럼 진행의 지역 기록으로 만든다. */
        private final ExplorerProgress 누적진행 = 새_진행();
        private final CollectionBook 누적도감 = CollectionBook.empty(지도);
        private final QuestBoard 누적월간 = QuestBoard.empty(나, QuestPeriod.of(시월));
        private final QuestBoard 누적상시 = QuestBoard.empty(나, QuestPeriod.ALL);
        private final ProgressionReplay.Result 결과;

        SameAsEvents() {
            for (int i = 0; i < 칠한순서.size(); i++) {
                ProgressVisit visit = 방문(칠한순서.get(i)).처리시각(초(i)).사실();
                QuestFact fact = QuestFact.of(visit.region(), visit.provinceCode(), visit.rarity(), 테마.includeAny(visit.region()),
                    누적진행.regions().visitedProvinceBefore(visit.provinceCode(), visit.visitedAt()));
                누적진행.applyVisit(visit, 진행규칙);
                누적도감.applyVisit(visit.region(), 나, visit.visitedAt(), 테마)
                    .forEach(completion -> 누적진행.applyThemeCompleted(completion.themeId(), completion.completedAt(), 진행규칙));
                누적월간.applyVisit(fact, 퀘스트, 시월);
                누적상시.applyVisit(fact, 퀘스트, 시월);
            }
            List<ReplayVisit> history = 칠한순서.stream().map(code -> 내방문(code, 초(칠한순서.indexOf(code)))).toList();
            결과 = 재계산(새_진행(), history, null, List.of());
        }

        @Test
        @DisplayName("XP·레벨·장부가 같다")
        void xpLevelLedger() {
            assertThat(결과.progress().xp()).isEqualTo(누적진행.xp());
            assertThat(결과.progress().level()).isEqualTo(누적진행.level());
            assertThat(결과.progress().ledger().entries()).extracting(XpLedgerEntry::refId)
                .containsExactlyInAnyOrderElementsOf(누적진행.ledger().entries().stream().map(XpLedgerEntry::refId).toList());
        }

        @Test
        @DisplayName("뱃지·칭호·연속 탐험 달이 같다")
        void badgesTitlesStreak() {
            assertThat(결과.progress().badges().keySet()).isEqualTo(누적진행.badges().keySet());
            assertThat(결과.progress().titles().keySet()).isEqualTo(누적진행.titles().keySet());
            assertThat(결과.progress().streak()).isEqualTo(누적진행.streak());
        }

        @Test
        @DisplayName("도감과 퀘스트 보드가 같다")
        void collectionAndQuests() {
            assertThat(결과.collectionBooks().get(0).themeProgresses()).containsExactlyInAnyOrderElementsOf(누적도감.themeProgresses());
            assertThat(결과.monthly().rows()).containsExactlyInAnyOrderElementsOf(누적월간.rows());
            assertThat(결과.always().rows()).containsExactlyInAnyOrderElementsOf(누적상시.rows());
            assertThat(누적월간.of("mprov").current(퀘스트.require("mprov"))).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("지난달 처리된 체크인은 이번 달 퀘스트에 세지 않는다")
    void lastMonthVisitsNotInThisMonth() {
        ProgressionReplay.Result result = 재계산(새_진행(), List.of(내방문(가평군, 기준시각.minus(Duration.ofDays(31)))), null,
            List.of());

        assertThat(result.monthly().of("m3").current(퀘스트.require("m3"))).isZero();
        assertThat(result.progress().regions().find(가평군).orElseThrow().active()).isTrue();
    }

    @Nested
    @DisplayName("취소해도 남는 것은 재계산도 지우지 않는다")
    class CancelAsymmetry {

        /** 종로구·중구를 칠해 테마를 완성한 뒤 중구를 취소한 진행과 도감. */
        private final ExplorerProgress 진행 = 새_진행();
        private final CollectionBook 도감 = CollectionBook.empty(지도);
        private final ProgressionReplay.Result 결과;

        CancelAsymmetry() {
            칠한다(진행, 방문(종로구));
            칠한다(진행, 방문(중구).처리시각(초(1)));
            도감.applyVisit(종로구, 나, 기준시각, 테마);
            도감.applyVisit(중구, 나, 초(1), 테마)
                .forEach(completion -> 진행.applyThemeCompleted(completion.themeId(), completion.completedAt(), 진행규칙));
            취소한다(진행, 방문(중구).처리시각(초(2)));
            도감.revokeVisit(중구, false, 테마);
            결과 = 재계산(진행, List.of(내방문(종로구, 기준시각)), 도감, List.of());
        }

        @Test
        @DisplayName("테마 보너스와 선점 보너스가 남는다")
        void rewardsStay() {
            assertThat(결과.progress().xp()).isEqualTo(진행.xp()).isEqualTo(145); // 35 + 중구 선점 10 + 테마 100
            assertThat(결과.progress().badges().keySet()).isEqualTo(진행.badges().keySet());
        }

        @Test
        @DisplayName("취소한 지역은 꺼진 채로, 도감의 완성 기록은 그대로 남는다")
        void cancelledRegionAndCompletion() {
            assertThat(결과.progress().regions().find(중구).orElseThrow().active()).isFalse();
            assertThat(결과.collectionBooks().get(0).progressOf("han").completedAt()).isEqualTo(초(1));
            assertThat(결과.collectionBooks().get(0).progressOf("han").have()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("보상 소식이 끝내 전달되지 못했을 때")
    class Recovery {

        /** 도감엔 완성 기록이 있지만 진행엔 테마 보너스가 없는 상태에서 재계산. */
        private ProgressionReplay.Result 테마보너스유실() {
            ExplorerProgress lost = 새_진행();
            칠한다(lost, 방문(종로구));
            칠한다(lost, 방문(중구).처리시각(초(1)));
            CollectionBook book = CollectionBook.empty(지도);
            book.applyVisit(종로구, 나, 기준시각, 테마);
            book.applyVisit(중구, 나, 초(1), 테마);
            assertThat(lost.titles()).doesNotContainKey("set-han");
            return 재계산(lost, List.of(내방문(종로구, 기준시각), 내방문(중구, 초(1))), book, List.of());
        }

        /** 9월 보드에서 보상을 받았지만 진행 장부엔 그 XP가 없는 상태. */
        private List<QuestBoard> 구월보상만받은보드() {
            QuestBoard september = QuestBoard.empty(나, QuestPeriod.of(YearMonth.of(2026, 9)));
            september.applyVisit(퀘스트사실(가평군, true), 퀘스트, YearMonth.of(2026, 9));
            september.claim("mgun", 퀘스트, 기준시각, YearMonth.of(2026, 9));
            return List.of(september, QuestBoard.empty(나, QuestPeriod.ALL));
        }

        @Test
        @DisplayName("완성 기록이 있는데 테마 보너스가 없으면 지급한다")
        void grantsMissingThemeBonus() {
            ProgressionReplay.Result result = 테마보너스유실();

            assertThat(result.progress().ledger().has(RefIds.theme(나, "han"))).isTrue();
            assertThat(result.progress().xp()).isEqualTo(35 + 20 + 100);
        }

        @Test
        @DisplayName("빠졌던 테마 칭호와 뱃지도 함께 채운다")
        void fixesTitleAndBadge() {
            ProgressionReplay.Result result = 테마보너스유실();

            assertThat(result.progress().titles()).containsKey("set-han");
            assertThat(result.progress().badges()).containsKey("set1");
        }

        @Test
        @DisplayName("받은 퀘스트인데 XP가 없으면 지난 달 보드라도 지급한다")
        void grantsMissingQuestXp() {
            List<QuestBoard> boards = 구월보상만받은보드();

            ProgressionReplay.Result result = 재계산(새_진행(), List.of(), null, boards);

            assertThat(result.progress().ledger().has(RefIds.quest(나, boards.get(0).period(), "mgun"))).isTrue();
            assertThat(result.progress().xp()).isEqualTo(40);
        }

        @Test
        @DisplayName("여러 번 돌려도 한 번만 지급한다")
        void idempotent() {
            List<QuestBoard> boards = 구월보상만받은보드();
            ProgressionReplay.Result first = 재계산(새_진행(), List.of(), null, boards);

            ProgressionReplay.Result again = 재계산(first.progress(), List.of(), null, boards);

            assertThat(again.progress().xp()).isEqualTo(40);
        }

        @Test
        @DisplayName("완성 시점 멤버였다면 직접 칠하지 않았어도 지급한다")
        void memberAtCompletion() {
            ProgressionReplay.Result mine = 재계산(새_진행(), List.of(), 친구가함께완성(), List.of());

            assertThat(mine.progress().ledger().has(RefIds.theme(나, "han"))).isTrue();
        }

        @Test
        @DisplayName("완성 뒤에 들어온 멤버에게는 지급하지 않는다")
        void lateMemberNotRecovered() {
            ProgressionReplay.Result late = 재계산(늦게온멤버, ExplorerProgress.start(늦게온멤버, 진행규칙, 기준시각), List.of(),
                친구가함께완성(), List.of());

            assertThat(late.progress().ledger().has(RefIds.theme(늦게온멤버, "han"))).isFalse();
            assertThat(late.progress().titles()).doesNotContainKey("set-han");
        }

        /** 나와 친구가 멤버일 때 친구가 혼자 종로구·중구를 칠해 완성한 도감. */
        private CollectionBook 친구가함께완성() {
            CollectionBook book = CollectionBook.empty(지도);
            book.applyVisit(종로구, 친구, 기준시각, 테마, List.of(나, 친구));
            book.applyVisit(중구, 친구, 초(1), 테마, List.of(나, 친구));
            return book;
        }
    }

    @Test
    @DisplayName("공유 지도 이력에서 새로 생기는 완성은 직접 칠하지 않은 멤버도 받는다")
    void sharedMapCompletionDuringReplay() {
        List<ReplayVisit> history = List.of(
            new ReplayVisit(친구, 방문(종로구).사실(), List.of(나, 친구)),
            new ReplayVisit(친구, 방문(중구).처리시각(초(1)).사실(), List.of(나, 친구)));

        ProgressionReplay.Result result = 재계산(새_진행(), history, null, List.of());

        assertThat(result.progress().ledger().has(RefIds.theme(나, "han"))).isTrue();
        assertThat(result.progress().xp()).isEqualTo(100); // 내 방문은 없다 — 테마 보너스만
    }

    @Nested
    @DisplayName("보호권·마일스톤·미스터리·시·도 정복은 다시 세어도")
    class GameRewards {

        private ReplayVisit 그달내방문(int year, int month) {
            return new ReplayVisit(나, 그달의방문(year, month));
        }

        /** 7월 월간 퀘스트 완주(보호권 하나) → 7·8월 칠하고 9월을 비운 뒤 10월에 칠함(보호권으로 이음 → 3개월 마일스톤 → 보호권 하나). */
        private ExplorerProgress 누적() {
            ExplorerProgress progress = 새_진행();
            월간퀘스트를_모두_받는다(progress, 2026, 7);
            그달에_칠한다(progress, 2026, 7);
            그달에_칠한다(progress, 2026, 8);
            그달에_칠한다(progress, 2026, 10);
            return progress;
        }

        private final List<ReplayVisit> 이력 = List.of(그달내방문(2026, 7), 그달내방문(2026, 8), 그달내방문(2026, 10));

        @Test
        @DisplayName("소식을 하나씩 받은 것과 같은 보호권 장부가 나온다")
        void sameFreezeLedger() {
            ExplorerProgress events = 누적();

            ExplorerProgress replayed = 재계산(events, 이력, null, List.of()).progress();

            assertThat(replayed.freezes().held()).isEqualTo(events.freezes().held()).isEqualTo(1);
            assertThat(replayed.freezes().chronological()).extracting(StreakFreezeEntry::refId)
                .containsExactlyElementsOf(events.freezes().chronological().stream().map(StreakFreezeEntry::refId).toList());
            assertThat(replayed.streak()).isEqualTo(events.streak());
        }

        @Test
        @DisplayName("보호권으로 메운 달도 소식을 하나씩 받은 것과 같다")
        void sameFrozenMonths() {
            ExplorerProgress events = 누적();

            ExplorerProgress replayed = 재계산(events, 이력, null, List.of()).progress();

            YearMonth 시월 = YearMonth.of(2026, 10);
            assertThat(replayed.frozenMonthsAsOf(시월)).isEqualTo(events.frozenMonthsAsOf(시월))
                .containsExactly(YearMonth.of(2026, 9));
        }

        @Test
        @DisplayName("몇 번을 다시 세도 같은 결과다")
        void deterministic() {
            ExplorerProgress first = 재계산(누적(), 이력, null, List.of()).progress();

            ExplorerProgress second = 재계산(first, 이력, null, List.of()).progress();

            assertThat(second.freezes().chronological()).isEqualTo(first.freezes().chronological());
            assertThat(second.xp()).isEqualTo(first.xp());
        }

        @Test
        @DisplayName("이미 받은 마일스톤·미스터리·정복 보상은 다시 주지도 지우지도 않는다")
        void keptOnce() {
            ExplorerProgress events = 누적();
            칠한다(events, 방문(가평군).처리시각(초(10)).그주의미스터리("2026-09-28", 가평군));
            List<ReplayVisit> history = new ArrayList<>(이력);
            history.add(new ReplayVisit(나, 방문(가평군).처리시각(초(10)).그주의미스터리("2026-09-28", 가평군).사실()));

            ExplorerProgress replayed = 재계산(events, history, null, List.of()).progress();

            assertThat(replayed.ledger().entriesOf(XpSource.STREAK_MILESTONE)).hasSize(1);
            assertThat(replayed.ledger().entriesOf(XpSource.MYSTERY_BONUS)).hasSize(1);
        }

        @Test
        @DisplayName("기록이 사라진 지역 때문에 지금 100%가 아니어도 받은 정복은 남는다")
        void conquestKeptAfterCancel() {
            ExplorerProgress events = 새_진행();
            칠한다(events, 방문(종로구));
            칠한다(events, 방문(중구).처리시각(초(1)));
            칠한다(events, 방문(용산구).처리시각(초(2)));
            취소한다(events, 방문(용산구).처리시각(초(3)));

            ExplorerProgress replayed = 재계산(events, List.of(내방문(종로구, 기준시각), 내방문(중구, 초(1))), null, List.of())
                .progress();

            assertThat(replayed.provincesConquered()).containsOnlyKeys("KR-11");
        }

        @Test
        @DisplayName("정복 기록이 빠졌어도 지금 다 칠해져 있으면 다시 셀 때 지급한다")
        void conquestRecovered() {
            List<ReplayVisit> history = List.of(내방문(종로구, 기준시각), 내방문(중구, 초(1)), 내방문(용산구, 초(2)));

            ExplorerProgress replayed = 재계산(새_진행(), history, null, List.of()).progress();

            assertThat(replayed.ledger().has(RefIds.conquest(나, "KR-11"))).isTrue();
        }
    }
}
