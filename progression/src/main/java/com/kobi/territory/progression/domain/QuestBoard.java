package com.kobi.territory.progression.domain;

import com.kobi.territory.common.model.ExplorerId;
import java.time.Instant;
import java.util.Collection;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 퀘스트 보드 애그리거트(explorerId, period). 달이 바뀌면 새 보드, 상시 도전은 period='ALL' 보드 하나.
 *
 * 불변식
 * - 보상은 달성 후 한 번만(claim).
 * - 지난 달 보드는 불변: 진행도 보상 받기도 바뀌지 않는다(처리 시각의 달 기준).
 * - 월간 퀘스트는 본인 체크인 기준, 소급 없음(처리 시각 visitedAt 이 속한 달의 보드에만 센다).
 */
public final class QuestBoard {

    private final ExplorerId explorerId;
    private final QuestPeriod period;
    private final Map<String, QuestProgress> progress;

    private QuestBoard(ExplorerId explorerId, QuestPeriod period, Collection<QuestProgress> rows) {
        this.explorerId = Objects.requireNonNull(explorerId, "explorerId");
        this.period = Objects.requireNonNull(period, "period");
        this.progress = new LinkedHashMap<>();
        rows.forEach(row -> progress.put(row.questId(), row));
    }

    public static QuestBoard empty(ExplorerId explorerId, QuestPeriod period) {
        return new QuestBoard(explorerId, period, List.of());
    }

    public static QuestBoard restore(ExplorerId explorerId, QuestPeriod period, Collection<QuestProgress> rows) {
        return new QuestBoard(explorerId, period, rows);
    }

    /** 체크인 사실 반영. 닫힌(지난 달) 보드면 무시한다. 같은 지역은 한 번만 센다(멱등). */
    public void applyVisit(QuestFact fact, QuestRules rules, YearMonth current) {
        if (period.closedAt(current)) return;
        rules.of(period).forEach(rule -> {
            QuestProgress questProgress = of(rule.id());
            progress.put(rule.id(), new QuestProgress(rule.id(), questProgress.tally().count(fact, rule), questProgress.claimedAt()));
        });
    }

    /** 보상 받기 — 달성했고 아직 안 받았고 보드가 열려 있어야 한다. */
    public QuestReward claim(String questId, QuestRules rules, Instant at, YearMonth current) {
        QuestRule rule = rules.require(questId);
        if (!rule.belongsTo(period)) throw ProgressionError.QUEST_NOT_FOUND.exception(questId);
        if (period.closedAt(current)) throw ProgressionError.QUEST_BOARD_CLOSED.exception(period.value());
        QuestProgress questProgress = of(questId);
        if (questProgress.claimed()) throw ProgressionError.QUEST_ALREADY_CLAIMED.exception();
        if (!questProgress.achieved(rule)) {
            throw ProgressionError.QUEST_NOT_COMPLETED.exception(questProgress.current(rule), rule.target());
        }
        progress.put(questId, new QuestProgress(questId, questProgress.tally(), at));
        return new QuestReward(explorerId, period, questId, rule.xp(), at);
    }

    /** 보상을 받은 퀘스트들의 보상(재계산 복구 규칙용 — 장부에 XP 가 없으면 지급). */
    public List<QuestReward> claimedRewards(QuestRules rules) {
        return progress.values().stream().filter(QuestProgress::claimed)
            .map(claimed -> new QuestReward(explorerId, period, claimed.questId(), rules.require(claimed.questId()).xp(),
                claimed.claimedAt()))
            .toList();
    }

    public QuestProgress of(String questId) {
        return progress.getOrDefault(questId, QuestProgress.empty(questId));
    }

    public ExplorerId explorerId() { return explorerId; }
    public QuestPeriod period() { return period; }
    public List<QuestProgress> rows() { return List.copyOf(progress.values()); }
}
