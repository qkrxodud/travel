package com.kobi.territory.progression.api.web;

import com.kobi.territory.catalog.api.query.ProgressionRules;
import com.kobi.territory.common.identity.CurrentExplorer;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.progression.api.web.ProgressionDtos.ClaimResponse;
import com.kobi.territory.progression.api.web.ProgressionDtos.QuestResponse;
import com.kobi.territory.progression.api.web.ProgressionDtos.QuestsResponse;
import com.kobi.territory.progression.application.ProgressionCatalog;
import com.kobi.territory.progression.application.QuestService;
import com.kobi.territory.progression.domain.QuestBoard;
import com.kobi.territory.progression.domain.QuestProgress;
import com.kobi.territory.progression.domain.QuestReward;
import com.kobi.territory.progression.domain.QuestRule;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** 이번 달 퀘스트 + 상시 도전, 보상 받기(1회). */
@RestController
public class QuestController {

    private final QuestService quests;
    private final ProgressionCatalog policy;
    private final ProgressionRules rules;

    public QuestController(QuestService quests, ProgressionCatalog policy, ProgressionRules rules) {
        this.quests = quests;
        this.policy = policy;
        this.rules = rules;
    }

    @GetMapping("/quests")
    public QuestsResponse quests(@CurrentExplorer ExplorerId explorerId) {
        QuestService.Boards boards = quests.view(explorerId);
        List<QuestResponse> monthly = responses("MONTHLY", boards.monthly());
        return new QuestsResponse(boards.month().toString(), (int) monthly.stream().filter(QuestResponse::achieved).count(),
            monthly, responses("ALWAYS", boards.always()));
    }

    @PostMapping("/quests/{questId}/claim")
    public ClaimResponse claim(@CurrentExplorer ExplorerId explorerId, @PathVariable("questId") String questId) {
        QuestReward reward = quests.claim(explorerId, questId);
        return new ClaimResponse(reward.questId(), reward.period().value(), reward.xp(), reward.claimedAt());
    }

    private List<QuestResponse> responses(String scope, QuestBoard board) {
        return rules.quests().stream().filter(quest -> quest.scope().equals(scope)).map(quest -> {
            QuestRule rule = policy.quests().require(quest.id());
            QuestProgress questProgress = board.of(quest.id());
            return new QuestResponse(quest.id(), quest.scope(), quest.ico(), quest.name(), quest.desc(),
                questProgress.current(rule), quest.target(), quest.xp(), quest.title(), questProgress.achieved(rule),
                questProgress.claimed(), questProgress.claimedAt(), questProgress.claimable(rule));
        }).toList();
    }
}
