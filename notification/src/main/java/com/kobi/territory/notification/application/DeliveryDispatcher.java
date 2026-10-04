package com.kobi.territory.notification.application;

import com.kobi.territory.common.event.EventOutbox;
import com.kobi.territory.notification.api.event.PushSent;
import com.kobi.territory.notification.domain.delivery.ClaimOutcome;
import com.kobi.territory.notification.domain.delivery.DeliveryPolicy;
import com.kobi.territory.notification.domain.delivery.DeliveryReclaimed;
import com.kobi.territory.notification.domain.delivery.DeliveryStatus;
import com.kobi.territory.notification.domain.delivery.PushDelivery;
import com.kobi.territory.notification.domain.delivery.PushDeliveryRepository;
import com.kobi.territory.notification.domain.policy.Reach;
import com.kobi.territory.notification.domain.push.DeviceSend;
import com.kobi.territory.notification.domain.push.PushSender;
import com.kobi.territory.notification.domain.push.SendReport;
import com.kobi.territory.notification.domain.recipient.PushRecipient;
import com.kobi.territory.notification.domain.recipient.PushRecipientRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 발송기 — 보낼 때가 된 발송 기록을 batchSize 개씩 잡아(SENDING) 기기마다 보내고 결과로 닫는다(territory.push.dispatch.delay-ms 마다).
 * 한 건의 흐름: 잡기(짧은 트랜잭션, 낙관적 잠금 — 다른 발송기가 먼저 잡았으면 건너뜀) → 동의·설정 다시 확인 → 기기마다 보내기(트랜잭션 밖,
 * 초당 maxPerSecond 를 넘지 않게) → 기록(받는 사람 루트 잠금 → 없어진 기기 지우기 → 상태 → 보냈으면 outbox {@link PushSent}).
 * <p>
 * <b>단일 인스턴스 가정</b>: 속도 제한은 이 인스턴스 안에서만 지킨다. 잡기는 낙관적 잠금이라 여러 인스턴스가 같은 기록을 두 번 보내지는 않지만,
 * 보내는 중에 죽은 기록은 claim-timeout 뒤 다시 잡아 보낸다(최소 1회 — 같은 알림을 두 번 받을 수 있다, 기기에서는 tag 가 같아 하나로 겹친다).
 */
@Component
public class DeliveryDispatcher {

    private static final Logger log = LoggerFactory.getLogger(DeliveryDispatcher.class);
    /** 한 번 깨어날 때 최대 몇 묶음까지(나머지는 다음 차례). */
    private static final int MAX_BATCHES_PER_RUN = 50;

    private final PushDeliveryRepository deliveries;
    private final PushRecipientRepository recipients;
    private final PushSubscriptionService subscriptions;
    private final PushSender sender;
    private final EventOutbox outbox;
    private final NotificationSettings settings;
    private final DeliveryPolicy policy;
    private final Clock clock;
    private final TransactionTemplate shortTx;
    private final SendPacer pacer;
    private final boolean enabled;

    public DeliveryDispatcher(PushDeliveryRepository deliveries, PushRecipientRepository recipients, PushSubscriptionService subscriptions,
                              PushSender sender, EventOutbox outbox, NotificationSettings settings, Clock clock,
                              PlatformTransactionManager transactionManager,
                              @Value("${territory.push.dispatch.enabled:true}") boolean enabled) {
        this.deliveries = deliveries;
        this.recipients = recipients;
        this.subscriptions = subscriptions;
        this.sender = sender;
        this.outbox = outbox;
        this.settings = settings;
        this.policy = settings.deliveryPolicy();
        this.clock = clock;
        this.shortTx = new TransactionTemplate(transactionManager);
        this.shortTx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.pacer = new SendPacer(settings.maxPerSecond());
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${territory.push.dispatch.delay-ms:30000}",
        initialDelayString = "${territory.push.dispatch.initial-delay-ms:15000}")
    public void scheduled() {
        if (!enabled) return;
        DispatchRun run = dispatchDue();
        if (!run.outcomes().isEmpty()) log.info("알림 발송: {} (잡지 못함 {}, 지운 기기 {})", run.outcomes(), run.notClaimed(), run.goneDevices());
    }

    /** 보낼 때가 된 기록을 모두(최대 MAX_BATCHES_PER_RUN 묶음) 보낸다. local 즉시 발송도 이 길을 탄다. */
    public synchronized DispatchRun dispatchDue() {
        Map<DeliveryStatus, Integer> outcomes = new EnumMap<>(DeliveryStatus.class);
        int notClaimed = 0;
        int gone = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            Instant now = clock.instant();
            List<Long> due = deliveries.due(now, now.minus(policy.claimTimeout()), settings.batchSize());
            if (due.isEmpty()) break;
            for (Long id : due) {
                Optional<Dispatched> dispatched = dispatch(id);
                if (dispatched.isEmpty()) notClaimed++;
                dispatched.ifPresent(result -> outcomes.merge(result.status(), 1, Integer::sum));
                gone += dispatched.map(Dispatched::goneDevices).orElse(0);
            }
            if (due.size() < settings.batchSize()) break;
        }
        return new DispatchRun(outcomes, notClaimed, gone);
    }

    /** 한 건. 잡지 못했으면 빈 값. 잡은 뒤의 쓰기는 매번 새로 읽은 기록에 한다(자기가 잡은 채인지 도메인이 확인). */
    private Optional<Dispatched> dispatch(long id) {
        Optional<PushDelivery> claimed = claim(id);
        if (claimed.isEmpty()) return Optional.empty();
        PushDelivery snapshot = claimed.get();
        if (snapshot.status() == DeliveryStatus.EXPIRED) return Optional.of(new Dispatched(DeliveryStatus.EXPIRED, 0));
        Instant claim = snapshot.claimedAt().orElseThrow();
        try {
            Optional<PushRecipient> recipient = recipients.find(snapshot.key().explorerId());
            Reach reach = recipient.map(found -> found.reach(snapshot.key().kind())).orElse(Reach.NO_DEVICE);
            if (!snapshot.confirmReach(reach, claim)) {
                return Optional.of(new Dispatched(record(id, delivery -> delivery.confirmReach(reach, claim)), 0));
            }
            List<DeviceSend> sends = recipient.orElseThrow().devices().stream().map(device -> {
                pacer.awaitTurn();
                return sender.send(device.endpoint(), device.keys(), snapshot.message(), policy.timeToLive());
            }).toList();
            SendReport report = SendReport.of(sends);
            DeliveryStatus status = record(id, delivery -> {
                subscriptions.forgetGone(delivery.key().explorerId(), report.goneEndpoints(), claim);
                delivery.complete(report, claim, clock.instant(), policy);
            });
            return Optional.of(new Dispatched(status, report.goneEndpoints().size()));
        } catch (DeliveryReclaimed | ConcurrencyFailureException reclaimed) {
            log.warn("알림 발송 기록 {} 를 다른 발송기가 다시 잡아 결과를 쓰지 않았다: {}", id, reclaimed.getMessage());
            return Optional.empty();
        }
    }

    /** 새로 읽은 기록에 바꾸고 쓴다(짧은 트랜잭션). 보냈으면 outbox 에 PushSent. */
    private DeliveryStatus record(long id, Consumer<PushDelivery> change) {
        return shortTx.execute(status -> {
            PushDelivery delivery = deliveries.find(id).orElseThrow();
            change.accept(delivery);
            deliveries.update(delivery);
            delivery.sent().ifPresent(sent -> outbox.append("PushDelivery", sent.key().explorerId().value(),
                new PushSent(sent.key().explorerId().value(), sent.key().kind().code(), sent.key().period(), sent.devices(),
                    sent.sentAt())));
            return delivery.status();
        });
    }

    /** 잡기(짧은 트랜잭션). 다른 발송기가 먼저 고쳤으면(낙관적 잠금 충돌) 빈 값. 만료로 닫은 것도 돌려준다. */
    private Optional<PushDelivery> claim(long id) {
        try {
            return shortTx.execute(status -> deliveries.find(id).flatMap(delivery -> {
                ClaimOutcome outcome = delivery.claim(clock.instant(), policy);
                if (outcome == ClaimOutcome.NOT_DUE) return Optional.empty();
                deliveries.update(delivery);
                return Optional.of(delivery);
            }));
        } catch (ConcurrencyFailureException claimedElsewhere) {
            return Optional.empty();
        }
    }

    private record Dispatched(DeliveryStatus status, int goneDevices) {}
}
