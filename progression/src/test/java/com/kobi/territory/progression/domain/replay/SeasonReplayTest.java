package com.kobi.territory.progression.domain.replay;

import static com.kobi.territory.progression.domain.Fixtures.가평군;
import static com.kobi.territory.progression.domain.Fixtures.계절;
import static com.kobi.territory.progression.domain.Fixtures.나;
import static com.kobi.territory.progression.domain.Fixtures.방문;
import static com.kobi.territory.progression.domain.Fixtures.새_진행;
import static com.kobi.territory.progression.domain.Fixtures.서울정오;
import static com.kobi.territory.progression.domain.Fixtures.종로구;
import static com.kobi.territory.progression.domain.Fixtures.중구;
import static com.kobi.territory.progression.domain.Fixtures.지도;
import static com.kobi.territory.progression.domain.Fixtures.진행규칙;
import static com.kobi.territory.progression.domain.Fixtures.칠한다;
import static com.kobi.territory.progression.domain.Fixtures.퀘스트;
import static com.kobi.territory.progression.domain.Fixtures.테마;
import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.progression.domain.collectionbook.CollectionBook;
import com.kobi.territory.progression.domain.collectionbook.SeasonCompletion;
import com.kobi.territory.progression.domain.policy.XpSource;
import com.kobi.territory.progression.domain.progress.ExplorerProgress;
import com.kobi.territory.progression.domain.progress.RefIds;
import com.kobi.territory.progression.domain.progress.StampFact;
import com.kobi.territory.progression.domain.progress.WishFact;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 계절 한정 테마·재방문 도장·가고 싶은 곳의 재계산(9단계) — 열린 회차는 방문 이력으로 다시 세고 닫힌 회차는 그대로, 탐험 기록(도장·다녀온 곳)의
 * 보상은 빠졌으면 채운다. 몇 번을 돌려도 같다.
 */
@DisplayName("계절·도장·가고 싶은 곳 재계산")
class SeasonReplayTest {

    private static final Instant 시월4일 = 서울정오(2026, 10, 4);
    private static final Instant 시월5일 = 서울정오(2026, 10, 5);

    private static ReplayVisit 내방문(RegionCode code, Instant at) {
        return new ReplayVisit(나, 방문(code).처리시각(at).사실());
    }

    private static ProgressionReplay.Result 재계산(ExplorerProgress current, List<ReplayVisit> history, CollectionBook book,
                                               Instant at, List<StampFact> stamps, List<WishFact> wishes) {
        return ProgressionReplay.replay(나, current, Map.of(지도, history), Map.of(지도, book), List.of(), 진행규칙, 테마, 계절,
            퀘스트, YearMonth.from(at.atZone(진행규칙.zone())), at, stamps, wishes);
    }

    @Test
    @DisplayName("가을 회차 기간 안에 칠한 방문을 다시 세면 소식을 하나씩 받은 결과와 같은 완성·XP 가 나온다")
    void sameAsEvents() {
        CollectionBook liveBook = CollectionBook.empty(지도);
        ExplorerProgress live = 새_진행();
        칠한다(live, 방문(종로구).처리시각(시월4일));
        liveBook.applyVisit(종로구, 나, 시월4일, 테마);
        liveBook.applySeasonVisit(종로구, 나, 시월4일, 계절, List.of(나));
        칠한다(live, 방문(중구).처리시각(시월5일));
        liveBook.applyVisit(중구, 나, 시월5일, 테마)
            .forEach(completion -> live.applyThemeCompleted(completion.themeId(), completion.completedAt(), 진행규칙));
        List<SeasonCompletion> done = liveBook.applySeasonVisit(중구, 나, 시월5일, 계절, List.of(나));
        done.forEach(completion -> live.applySeasonCompleted(completion.roundId(), completion.completedAt(), 진행규칙));

        ProgressionReplay.Result replayed = 재계산(새_진행(), List.of(내방문(종로구, 시월4일), 내방문(중구, 시월5일)),
            CollectionBook.empty(지도), 서울정오(2026, 10, 20), List.of(), List.of());

        assertThat(replayed.collectionBooks().getFirst().seasonProgressOf("autumn-2026"))
            .isEqualTo(liveBook.seasonProgressOf("autumn-2026"));
        assertThat(replayed.progress().ledger().find(RefIds.season(나, "autumn-2026")).orElseThrow().at()).isEqualTo(시월5일);
        assertThat(replayed.progress().xp()).isEqualTo(live.xp());
    }

    @Test
    @DisplayName("같은 입력으로 두 번 다시 세도 같은 결과다")
    void deterministic() {
        List<ReplayVisit> history = List.of(내방문(종로구, 시월4일), 내방문(중구, 시월5일));
        Instant at = 서울정오(2026, 10, 20);
        ProgressionReplay.Result first = 재계산(새_진행(), history, CollectionBook.empty(지도), at, List.of(), List.of());

        ProgressionReplay.Result second = 재계산(first.progress(), history, first.collectionBooks().getFirst(), at, List.of(),
            List.of());

        assertThat(second.collectionBooks().getFirst().seasonProgresses())
            .isEqualTo(first.collectionBooks().getFirst().seasonProgresses());
        assertThat(second.progress().xp()).isEqualTo(first.progress().xp());
        assertThat(second.progress().ledger().count(XpSource.SEASON_COMPLETE)).isEqualTo(1);
    }

    @Test
    @DisplayName("닫힌 회차의 기록은 지금 남은 방문과 달라도 그대로 둔다")
    void closedRoundUntouched() {
        CollectionBook book = CollectionBook.empty(지도);
        book.applySeasonVisit(종로구, 나, 시월4일, 계절, List.of(나)); // 이 방문은 회차가 끝난 뒤 취소돼 이력에 없다

        ProgressionReplay.Result replayed = 재계산(새_진행(), List.of(내방문(중구, 시월5일)), book, 서울정오(2027, 1, 10),
            List.of(), List.of());

        assertThat(replayed.collectionBooks().getFirst().seasonProgressOf("autumn-2026").collected()).containsExactly(종로구);
        assertThat(replayed.progress().ledger().count(XpSource.SEASON_COMPLETE)).isZero();
    }

    @Test
    @DisplayName("탐험에 남은 도장·다녀온 곳의 보상이 장부에 없으면 채운다")
    void recoversRecords() {
        Instant 내년_봄 = 서울정오(2027, 4, 1);

        ProgressionReplay.Result replayed = 재계산(새_진행(), List.of(내방문(종로구, 시월4일)), CollectionBook.empty(지도),
            서울정오(2027, 4, 10), List.of(new StampFact(종로구, 2027, 내년_봄)), List.of(new WishFact(가평군, 시월5일)));

        assertThat(replayed.progress().revisitStampCount()).isEqualTo(1);
        assertThat(replayed.progress().wishesFulfilledCount()).isEqualTo(1);
        assertThat(replayed.progress().ledger().find(RefIds.revisit(나, 종로구, 2027)).orElseThrow().at()).isEqualTo(내년_봄);
    }
}
