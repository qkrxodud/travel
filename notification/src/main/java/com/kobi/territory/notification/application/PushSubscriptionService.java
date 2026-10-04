package com.kobi.territory.notification.application;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.api.event.ExplorerMerged;
import com.kobi.territory.notification.domain.push.DeviceKeys;
import com.kobi.territory.notification.domain.push.PushEndpoint;
import com.kobi.territory.notification.domain.recipient.NotificationPreferences;
import com.kobi.territory.notification.domain.recipient.PushRecipient;
import com.kobi.territory.notification.domain.recipient.PushRecipientRepository;
import com.kobi.territory.notification.domain.recipient.SubscribeResult;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 구독·해지·알림 설정 유스케이스 — "잠그고 → 불러와서 → 도메인에 시키고 → 저장". 판단(받을 주소, 같은 브라우저, 기기 수 상한)은 PushRecipient 가
 * 한다.
 * <p>
 * 잠금·격리 규칙: 기기 수 상한("지금 몇 대인가")으로 판단하는 쓰기라 READ_COMMITTED + 루트 행(push_recipient) 잠금이 트랜잭션의 첫 조회다 —
 * 잠금 전 일반 조회가 MySQL REPEATABLE READ 스냅숏을 고정하면 동시에 들어온 구독을 못 봐 상한이 뚫린다(1·9단계 결함). 루트가 없으면 먼저 따로
 * 커밋되는 트랜잭션에서 만든다.
 */
@Service
public class PushSubscriptionService {

    private final PushRecipientRepository recipients;
    private final NotificationSettings settings;
    private final Clock clock;
    private final TransactionTemplate separateTx;

    public PushSubscriptionService(PushRecipientRepository recipients, NotificationSettings settings, Clock clock,
                                   PlatformTransactionManager transactionManager) {
        this.recipients = recipients;
        this.settings = settings;
        this.clock = clock;
        this.separateTx = new TransactionTemplate(transactionManager);
        this.separateTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.separateTx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /** POST /push/subscriptions — 이 브라우저를 알림 받을 기기로(이미 있으면 키 갱신). 같은 브라우저가 다른 탐험가에 묶여 있으면 옮긴다. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SubscribeResult subscribe(ExplorerId explorerId, PushEndpoint endpoint, DeviceKeys keys) {
        Instant now = clock.instant();
        ensureRoot(explorerId, now);
        PushRecipient recipient = locked(explorerId);
        SubscribeResult result = recipient.subscribe(endpoint, keys, now, settings.devicePolicy());
        recipients.releaseEndpoint(endpoint, explorerId);
        recipients.save(recipient);
        return result;
    }

    /** DELETE /push/subscriptions — 이 브라우저 해지(없으면 그대로 — 멱등). */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void unsubscribe(ExplorerId explorerId, PushEndpoint endpoint) {
        recipients.findLocked(explorerId).filter(recipient -> recipient.unsubscribe(endpoint, clock.instant()))
            .ifPresent(recipients::save);
    }

    /** GET /push/preferences — 아직 구독한 적이 없으면 모두 켜진 처음 상태. */
    @Transactional(readOnly = true)
    public PushRecipient view(ExplorerId explorerId) {
        return recipients.find(explorerId).orElseGet(() -> PushRecipient.start(explorerId, clock.instant()));
    }

    /** PUT /push/preferences — 종류별 켜고 끄기. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PushRecipient changePreferences(ExplorerId explorerId, NotificationPreferences preferences) {
        Instant now = clock.instant();
        ensureRoot(explorerId, now);
        PushRecipient recipient = locked(explorerId);
        recipient.changePreferences(preferences, now);
        recipients.save(recipient);
        return recipient;
    }

    /**
     * 계정 병합(구독자 notification.recipient): 익명 탐험가의 기기를 계정 탐험가로 옮긴다 — 병합된 쪽은 토큰이 무효라 그 브라우저는 이제 계정
     * 탐험가로 쓰인다. 잠금 순서: 계정(into) → 익명(from). 멱등(옮길 기기가 없으면 그대로).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void onExplorerMerged(ExplorerMerged event) {
        ExplorerId into = ExplorerId.of(event.intoExplorerId());
        ensureRoot(into, event.mergedAt());
        PushRecipient account = locked(into);
        recipients.findLocked(ExplorerId.of(event.fromExplorerId())).ifPresent(merged -> {
            account.absorb(merged, clock.instant(), settings.devicePolicy());
            recipients.save(merged);
            recipients.save(account);
        });
    }

    /** 푸시 서비스가 "구독이 없다"고 한 기기를 지운다(발송기). 그새 다시 구독한 기기는 남긴다. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void forgetGone(ExplorerId explorerId, Collection<PushEndpoint> gone, Instant sentAt) {
        if (gone.isEmpty()) return;
        recipients.findLocked(explorerId).ifPresent(recipient -> {
            recipient.forgetGone(gone, sentAt, clock.instant());
            recipients.save(recipient);
        });
    }

    private PushRecipient locked(ExplorerId explorerId) {
        return recipients.findLocked(explorerId).orElseThrow(() -> new IllegalStateException("알림 루트가 없다: " + explorerId));
    }

    /**
     * 루트 행 보장 — 따로 커밋되는 READ_COMMITTED 트랜잭션에서(동시에 만들 때의 유일성 위반·교착이 본 트랜잭션을 오염시키지 않게). 동시에 만든
     * 쪽이 이기면 이미 목표 상태라 그 실패는 흡수한다 — 이어지는 루트 잠금이 남이 만든 행을 기다려 읽는다.
     */
    private void ensureRoot(ExplorerId explorerId, Instant at) {
        try {
            separateTx.executeWithoutResult(status -> recipients.ensure(explorerId, at));
        } catch (DataIntegrityViolationException | ConcurrencyFailureException | TransactionException alreadyBeingCreated) {
            // 다른 요청이 같은 루트를 먼저 만들었다 — 목표 상태
        }
    }
}
