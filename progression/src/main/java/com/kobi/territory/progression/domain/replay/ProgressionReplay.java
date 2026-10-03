package com.kobi.territory.progression.domain.replay;

import com.kobi.territory.progression.domain.collectionbook.CollectionBook;
import com.kobi.territory.progression.domain.collectionbook.ThemeCompletion;
import com.kobi.territory.progression.domain.collectionbook.Themes;
import com.kobi.territory.progression.domain.policy.ProgressionPolicy;
import com.kobi.territory.progression.domain.progress.ExplorerProgress;
import com.kobi.territory.progression.domain.progress.FreezeGrant;
import com.kobi.territory.progression.domain.progress.ProgressVisit;
import com.kobi.territory.progression.domain.progress.QuestXp;
import com.kobi.territory.progression.domain.quest.QuestBoard;
import com.kobi.territory.progression.domain.quest.QuestFact;
import com.kobi.territory.progression.domain.quest.QuestPeriod;
import com.kobi.territory.progression.domain.quest.QuestRules;
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
 * 취소 비대칭·추가만 규칙으로 남은 것(보너스 XP·테마(세트) 완성 기록·뱃지·칭호·퀘스트 집계와 보상 기록)은 지우지 않는다.
 * <ul>
 *   <li>도감: 지도마다 rebuildBase(완성 기록·수령자 유지)에서 그 지도의 모든 멤버 방문을 재생</li>
 *   <li>진행: rebuildBase(지금 멤버인 지도만 비움 — 탈퇴한 지도의 활성은 유지, §5) 에서 본인 방문 + 본인이 수령자인 새 완성을
 *       시간 순으로 재생</li>
 *   <li>퀘스트: 이번 달 보드·상시 보드에 덧붙여 센다(같은 지역은 한 번). 지난 달 보드는 불변이라 손대지 않는다.
 *       mprov 의 "처음 가는 시·도"는 재생 중인 진행의 지역 기록(처음 밟은 시각)으로 판단한다</li>
 *   <li>복구 규칙(QA P1-2): 완성 기록(본인이 완성 시점 멤버였던 것만 — 결정 1·R2-1)이 있는데 세트 보너스가 장부에 없거나,
 *       보상을 받은 퀘스트인데 퀘스트 XP 가 장부에 없으면 지급</li>
 *   <li>보호권(8단계): 장부를 비우고, 장부에 남은 보상에서 다시 만든 "받을 일"(마일스톤·한 달 월간 퀘스트 완주)과 체크인(빈 달 소모)을
 *       처리 시각 순으로 섞어 다시 쌓는다 — 같은 입력이면 몇 번을 돌려도 같은 보유 수. 미스터리·시·도 정복·마일스톤 XP 는 회수 없는
 *       보상이라 남고, 재생에서 새로 닿은 시·도 정복은 그때 지급된다(복구 규칙)</li>
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
                                ProgressionPolicy policy, Themes themes, QuestRules questRules, YearMonth now, Instant at) {
        List<Step> timeline = new ArrayList<>();
        List<CollectionBook> rebuilt = new ArrayList<>();
        histories.forEach((mapId, visits) -> {
            CollectionBook collectionBook = collections.getOrDefault(mapId, CollectionBook.empty(mapId)).rebuildBase();
            for (ReplayVisit replayVisit : visits) {
                List<ThemeCompletion> completions = collectionBook.applyVisit(replayVisit.visit().region(),
                    replayVisit.explorer(), replayVisit.visit().visitedAt(), themes, replayVisit.members());
                if (replayVisit.explorer().equals(explorer)) {
                    timeline.add(new Step(replayVisit.visit().visitedAt(), 0, replayVisit, null, null));
                }
                completions.stream().filter(completion -> completion.rewards(explorer))
                    .forEach(completion -> timeline.add(new Step(completion.completedAt(), 1, null, completion, null)));
            }
            rebuilt.add(collectionBook);
        });
        ExplorerProgress progress = current.rebuildBase(histories.keySet());
        progress.freezeGrantsOnRecord(policy).forEach(grant -> timeline.add(new Step(grant.at(), 2, null, null, grant)));
        timeline.sort(Comparator.comparing(Step::at).thenComparingInt(Step::order));

        QuestBoard monthly = boardOf(boards, explorer, QuestPeriod.of(now));
        QuestBoard always = boardOf(boards, explorer, QuestPeriod.ALL);
        for (Step step : timeline) {
            if (step.visit() != null) {
                ProgressVisit visit = step.visit().visit();
                QuestFact fact = QuestFact.of(visit.region(), visit.provinceCode(), visit.rarity(), themes.includeAny(visit.region()),
                    progress.regions().visitedProvinceBefore(visit.provinceCode(), visit.visitedAt())); // 반영 전 기록으로
                progress.applyVisit(visit, policy);
                if (QuestPeriod.monthOf(step.at(), policy.zone()).equals(monthly.period())) {
                    monthly.applyVisit(fact, questRules, now);
                }
                always.applyVisit(fact, questRules, now);
            } else if (step.completion() != null) {
                progress.applyThemeCompleted(step.completion().themeId(), step.at(), policy);
            } else {
                progress.applyFreezeGrant(step.freezeGrant(), policy);
            }
        }

        List<String> completedThemeIds = rebuilt.stream().flatMap(book -> book.themeIdsRewardedTo(explorer).stream())
            .distinct().toList();
        List<QuestXp> claimed = boards.stream().flatMap(board -> board.claimedRewards(questRules).stream())
            .map(reward -> new QuestXp(reward.period(), reward.questId(), reward.xp())).toList();
        progress.recoverRewards(completedThemeIds, claimed, at, policy);
        return new Result(progress, rebuilt, monthly, always);
    }

    private static QuestBoard boardOf(List<QuestBoard> boards, ExplorerId explorer, QuestPeriod period) {
        return boards.stream().filter(board -> board.period().equals(period)).findFirst()
            .orElseGet(() -> QuestBoard.empty(explorer, period));
    }

    /** 재생 한 걸음 — 같은 시각이면 체크인(0) → 테마 완성(1) → 보호권 받기(2) 순(이벤트 처리 순서와 같다). */
    private record Step(Instant at, int order, ReplayVisit visit, ThemeCompletion completion, FreezeGrant freezeGrant) {}

    /** 재계산 결과. 컬렉션은 방어 복사. */
    public record Result(ExplorerProgress progress, List<CollectionBook> collectionBooks, QuestBoard monthly, QuestBoard always) {
        public Result {
            collectionBooks = List.copyOf(collectionBooks);
        }
    }
}
