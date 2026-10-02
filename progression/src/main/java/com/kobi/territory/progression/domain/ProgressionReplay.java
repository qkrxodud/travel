package com.kobi.territory.progression.domain;

import com.kobi.territory.common.model.ExplorerId;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 재계산 도메인 서비스(일관성 원칙 3 — 진행·도감·퀘스트는 영토로부터 다시 만들 수 있다). 이벤트 핸들러와 같은 애그리거트
 * 메서드를 처리 시각 순으로 다시 호출하므로, 결과가 이벤트 누적 결과와 같다.
 * 재계산은 "덧붙이기"다 — 현재 방문으로만 정해지는 값(지역 활성·기본 XP·스트릭·도감의 모은 지역)만 다시 만들고,
 * 취소 비대칭·추가만 규칙으로 남은 것(보너스 XP·세트 완성 기록·뱃지·칭호·퀘스트 집계와 보상 기록)은 지우지 않는다.
 * <ul>
 *   <li>도감: 지도마다 rebuildBase(완성 기록 유지)에서 그 지도의 모든 멤버 방문을 재생</li>
 *   <li>진행: rebuildBase 에서 본인 방문 + 본인이 완성시킨 세트를 시간 순으로 재생</li>
 *   <li>퀘스트: 이번 달 보드·상시 보드에 덧붙여 센다(같은 지역은 한 번). 지난 달 보드는 불변이라 손대지 않는다.
 *       mprov 의 "처음 가는 시·도"는 재생 중인 진행의 지역 기록(처음 밟은 시각)으로 판단한다</li>
 *   <li>복구 규칙(QA P1-2): 완성 기록이 있는데 세트 보너스가 장부에 없거나, 보상을 받은 퀘스트인데 퀘스트 XP 가 장부에 없으면 지급</li>
 * </ul>
 */
public final class ProgressionReplay {

    private ProgressionReplay() {}

    /**
     * @param histories   탐험가가 속한 지도별 방문 이력(모든 멤버, 처리 시각 순)
     * @param collections 그 지도들의 현재 도감(mapId → CollectionBook)
     * @param boards      탐험가의 퀘스트 보드 전부(지난 달 포함 — 복구 규칙에 쓴다). 이번 달·상시 보드가 없으면 빈 보드로 시작
     */
    public static Result replay(ExplorerId explorer, ExplorerProgress current, Map<String, List<ReplayVisit>> histories,
                                Map<String, CollectionBook> collections, List<QuestBoard> boards,
                                ProgressionPolicy policy, SetCatalog sets, QuestRules quests, YearMonth now, Instant at) {
        List<Step> timeline = new ArrayList<>();
        List<CollectionBook> rebuilt = new ArrayList<>();
        histories.forEach((mapId, visits) -> {
            CollectionBook collectionBook = collections.getOrDefault(mapId, CollectionBook.empty(mapId)).rebuildBase();
            for (ReplayVisit replayVisit : visits) {
                List<SetCompletion> completions = collectionBook.applyVisit(replayVisit.visit().region(),
                    replayVisit.explorer(), replayVisit.visit().visitedAt(), sets);
                if (replayVisit.explorer().equals(explorer)) {
                    timeline.add(new Step(replayVisit.visit().visitedAt(), 0, replayVisit, null));
                }
                completions.stream().filter(completion -> completion.completedBy().equals(explorer))
                    .forEach(completion -> timeline.add(new Step(completion.completedAt(), 1, null, completion)));
            }
            rebuilt.add(collectionBook);
        });
        timeline.sort(Comparator.comparing(Step::at).thenComparingInt(Step::order));

        ExplorerProgress progress = current.rebuildBase();
        QuestBoard monthly = boardOf(boards, explorer, QuestPeriod.of(now));
        QuestBoard always = boardOf(boards, explorer, QuestPeriod.ALL);
        for (Step step : timeline) {
            if (step.visit() != null) {
                ProgressVisit visit = step.visit().visit();
                QuestFact fact = QuestFact.of(visit, sets, progress.regions()); // 이 방문 반영 전의 지역 기록으로
                progress.applyVisit(visit, policy);
                if (QuestPeriod.monthOf(step.at(), policy.zone()).equals(monthly.period())) {
                    monthly.applyVisit(fact, quests, now);
                }
                always.applyVisit(fact, quests, now);
            } else {
                progress.applySetCompleted(step.completion().setId(), step.at(), policy);
            }
        }

        List<String> completedSetIds = rebuilt.stream().flatMap(book -> book.completedSetIds().stream()).distinct().toList();
        List<QuestReward> claimed = boards.stream().flatMap(board -> board.claimedRewards(quests).stream()).toList();
        progress.recoverRewards(completedSetIds, claimed, at, policy);
        return new Result(progress, List.copyOf(rebuilt), monthly, always);
    }

    private static QuestBoard boardOf(List<QuestBoard> boards, ExplorerId explorer, QuestPeriod period) {
        return boards.stream().filter(board -> board.period().equals(period)).findFirst()
            .orElseGet(() -> QuestBoard.empty(explorer, period));
    }

    private record Step(Instant at, int order, ReplayVisit visit, SetCompletion completion) {}

    public record Result(ExplorerProgress progress, List<CollectionBook> collections, QuestBoard monthly, QuestBoard always) {}
}
