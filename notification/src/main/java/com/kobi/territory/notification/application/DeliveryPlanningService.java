package com.kobi.territory.notification.application;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.notification.domain.campaign.Campaign;
import com.kobi.territory.notification.domain.delivery.DeliveryKey;
import com.kobi.territory.notification.domain.delivery.DeliveryPlanner;
import com.kobi.territory.notification.domain.delivery.PlanDecision;
import com.kobi.territory.notification.domain.delivery.PlanResult;
import com.kobi.territory.notification.domain.delivery.PushDeliveryRepository;
import com.kobi.territory.notification.domain.delivery.ScheduledDeliveries;
import com.kobi.territory.notification.domain.policy.Reach;
import com.kobi.territory.notification.domain.push.PushMessage;
import com.kobi.territory.notification.domain.recipient.PushRecipient;
import com.kobi.territory.notification.domain.recipient.PushRecipientRepository;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 한 사람에게 알림 하나를 계획한다(발송 기록 만들기). 판단(멱등·동의·설정·하루 최대 개수)은 {@link DeliveryPlanner} 가 한다.
 * <p>
 * 잠금·격리 규칙: "그날 몇 개인가"로 판단하는 쓰기라 READ_COMMITTED + 루트 행(push_recipient) 잠금이 첫 조회이고, 그날 기록·같은 열쇠는 잠금
 * 뒤에 읽는다 — 서로 다른 종류의 알림(예: 월요일 미스터리와 계절 시작)이 같은 사람에게 동시에 계획돼도 하나만 남는다. 같은 열쇠의 동시 계획은
 * 저장소의 유일성(탐험가·종류·기간)이 한 번 더 막는다(호출자가 이미 계획됨으로 센다).
 */
@Service
public class DeliveryPlanningService {

    private final PushRecipientRepository recipients;
    private final PushDeliveryRepository deliveries;
    private final NotificationSettings settings;
    private final Clock clock;

    public DeliveryPlanningService(PushRecipientRepository recipients, PushDeliveryRepository deliveries, NotificationSettings settings,
                                   Clock clock) {
        this.recipients = recipients;
        this.deliveries = deliveries;
        this.settings = settings;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PlanDecision planFor(ExplorerId explorerId, Campaign campaign, PushMessage message) {
        Optional<PushRecipient> recipient = recipients.findLocked(explorerId);          // 루트 잠금 — 첫 조회
        Reach reach = recipient.map(locked -> locked.reach(campaign.kind())).orElse(Reach.NO_DEVICE);
        ScheduledDeliveries scheduled = ScheduledDeliveries.of(campaign.deliveryDay(),
            deliveries.onDay(explorerId, campaign.deliveryDay()),
            deliveries.exists(new DeliveryKey(explorerId, campaign.kind(), campaign.period())));
        PlanResult result = DeliveryPlanner.plan(explorerId, reach, campaign, message, scheduled, settings.deliveryPolicy(),
            clock.instant());
        result.delivery().ifPresent(deliveries::add);
        return result.decision();
    }
}
