package com.kobi.territory.progression.api.web;

import com.kobi.territory.catalog.api.query.ProgressionRules;
import com.kobi.territory.catalog.api.query.ProgressionRules.QuestView;
import com.kobi.territory.common.identity.CurrentExplorer;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.progression.api.web.ProgressionDtos.ClaimResponse;
import com.kobi.territory.progression.api.web.ProgressionDtos.QuestResponse;
import com.kobi.territory.progression.api.web.ProgressionDtos.QuestsResponse;
import com.kobi.territory.progression.application.ProgressionCatalog;
import com.kobi.territory.progression.application.QuestService;
import com.kobi.territory.progression.domain.quest.QuestBoard;
import com.kobi.territory.progression.domain.quest.QuestProgress;
import com.kobi.territory.progression.domain.quest.QuestReward;
import com.kobi.territory.progression.domain.quest.QuestRule;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** 이번 달 퀘스트 + 상시 도전, 보상 받기(1회). */
@RestController
public class QuestController {

    private final QuestService quests;
    private final ProgressionCatalog policy;
    private final Map<String, QuestView> questViews;

    public QuestController(QuestService quests, ProgressionCatalog policy, ProgressionRules rules) {
        this.quests = quests;
        this.policy = policy;
        this.questViews = rules.quests().stream().collect(Collectors.toMap(QuestView::id, Function.identity()));
    }

    @GetMapping("/quests")
    public QuestsResponse quests(@CurrentExplorer ExplorerId explorerId) {
        QuestService.Boards boards = quests.view(explorerId);
        List<QuestResponse> monthly = responses(QuestRule.Scope.MONTHLY, boards.monthly());
        return new QuestsResponse(boards.month().toString(), (int) monthly.stream().filter(QuestResponse::achieved).count(),
            monthly, responses(QuestRule.Scope.ALWAYS, boards.always()));
    }

    @PostMapping("/quests/{questId}/claim")
    public ClaimResponse claim(@CurrentExplorer ExplorerId explorerId, @PathVariable("questId") String questId) {
        QuestReward reward = quests.claim(explorerId, questId);
        return new ClaimResponse(reward.questId(), reward.period().value(), reward.xp(), reward.claimedAt());
    }

    private List<QuestResponse> responses(QuestRule.Scope scope, QuestBoard board) {
        return policy.questRules().byScope(scope).stream().map(rule -> {
            QuestView view = questViews.get(rule.id());
            QuestProgress questProgress = board.of(rule.id());
            return new QuestResponse(view.id(), view.scope(), view.ico(), view.name(), view.desc(),
                questProgress.current(rule), view.target(), view.xp(), view.title(), questProgress.achieved(rule),
                questProgress.claimed(), questProgress.claimedAt(), questProgress.claimable(rule));
        }).toList();
    }
}
