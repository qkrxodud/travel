package com.kobi.territory.wardrobe.domain.inventory;

import com.kobi.territory.common.model.ExplorerId;
import java.util.List;

/**
 * 세트 보상 수령자 판단(QA P3-1·P3-R2-5). SetCompleted 는 완성 시점 멤버(수령자)마다 한 건씩 오고, 각 건은 그 수령자 Inventory
 * 하나만 고친다. 완성 직후 합류해 수령자 목록에도 없고 합류 처리 때 완성 기록도 못 본 멤버에게는 별도 이벤트(ThemeRewardOwed)를
 * 내 각자 트랜잭션에서 주게 한다 — 중복을 줄이려고 완성자 몫의 SetCompleted 한 건에서만 낸다(지급은 멱등이라 중복돼도 안전).
 */
public final class ThemeRewardRecipients {

    private ThemeRewardRecipients() {}

    /**
     * @param eventRecipient 이 SetCompleted 의 수령자(explorerId)
     * @param completedBy    마지막 지역을 칠한 탐험가(예전 이벤트는 null — 수령자가 곧 완성자)
     * @param recipients     완성 시점 수령자 전원(예전 이벤트는 null — 수령자 한 명)
     * @param currentMembers 지금 지도 멤버
     * @return 따로 보상을 줘야 할 지금 멤버(완성자 몫이 아니면 빈 목록)
     */
    public static List<ExplorerId> lateJoiners(ExplorerId eventRecipient, ExplorerId completedBy, List<ExplorerId> recipients,
                                               List<ExplorerId> currentMembers) {
        ExplorerId completer = completedBy == null ? eventRecipient : completedBy;
        if (!completer.equals(eventRecipient)) return List.of();
        List<ExplorerId> rewarded = recipients == null ? List.of(eventRecipient) : recipients;
        return currentMembers.stream().filter(member -> !rewarded.contains(member)).toList();
    }
}
