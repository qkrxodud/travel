package com.kobi.territory.notification.domain.delivery;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.notification.domain.campaign.Campaign;
import com.kobi.territory.notification.domain.policy.Reach;
import com.kobi.territory.notification.domain.push.PushMessage;
import java.time.Instant;
import java.util.Optional;

/**
 * 도메인 서비스: 한 사람에게 이번 알림을 계획할지. 순서대로 — 같은 열쇠가 있으면 하지 않음(멱등), 그 종류를 껐거나 기기가 없으면 하지 않음
 * (동의·설정 존중), 그날 받을 알림이 이미 최대 개수면 하지 않음(하루 최대 1개 — 먼저 계획된 알림이 이긴다), 아니면 보낼 시각에 계획.
 */
public final class DeliveryPlanner {

    private DeliveryPlanner() {}

    public static PlanResult plan(ExplorerId explorerId, Reach reach, Campaign campaign, PushMessage message,
                                  ScheduledDeliveries scheduled, DeliveryPolicy policy, Instant now) {
        if (!scheduled.day().equals(campaign.deliveryDay())) throw new IllegalArgumentException("계획하는 날과 기록의 날이 다르다");
        if (message.kind() != campaign.kind()) throw new IllegalArgumentException("알림 종류가 다르다");
        if (scheduled.keyTaken()) return PlanResult.skipped(PlanDecision.ALREADY_PLANNED);
        if (reach == Reach.KIND_OFF) return PlanResult.skipped(PlanDecision.KIND_OFF);
        if (reach == Reach.NO_DEVICE) return PlanResult.skipped(PlanDecision.NO_DEVICE);
        if (scheduled.activeCount() >= policy.dailyLimit()) return PlanResult.skipped(PlanDecision.DAILY_LIMIT);
        return new PlanResult(PlanDecision.PLANNED, Optional.of(
            PushDelivery.schedule(new DeliveryKey(explorerId, campaign.kind(), campaign.period()), message, campaign, now)));
    }
}
