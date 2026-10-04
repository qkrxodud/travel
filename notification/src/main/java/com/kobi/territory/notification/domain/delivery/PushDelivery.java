package com.kobi.territory.notification.domain.delivery;

import com.kobi.territory.notification.domain.campaign.Campaign;
import com.kobi.territory.notification.domain.policy.Reach;
import com.kobi.territory.notification.domain.push.PushMessage;
import com.kobi.territory.notification.domain.push.SendReport;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * 애그리거트: 발송 기록 한 건 — 한 사람에게 한 종류·한 기간의 알림 하나. 계획할 때 문구와 보낼 날(서울 날짜)·시각을 정해 두고, 발송기가
 * 잡아서(SENDING) 보낸 결과로 닫는다.
 * <ul>
 *   <li>보낼 날 안에서만 보낸다 — 조용한 시간이거나 날짜가 바뀌었으면 만료(EXPIRED). 그래서 재시도가 밤이나 다음 날로 밀려 "하루 최대 1개"를
 *       깨지 않는다.</li>
 *   <li>한 기기라도 받으면 SENT(다른 기기의 일시 실패는 다시 보내지 않는다 — 같은 알림을 두 번 받지 않게).</li>
 *   <li>모든 기기 구독이 없어졌으면 CANCELLED, 잠시 실패면 재시도(백오프), 다시 보내도 안 되면 FAILED.</li>
 * </ul>
 */
public final class PushDelivery {

    static final String NO_DEVICE = "NO_DEVICE";
    static final String KIND_OFF = "KIND_OFF";

    private final Long id;
    private final DeliveryKey key;
    private final PushMessage message;
    private final LocalDate deliveryDay;
    private final Instant dueAt;
    private final Instant createdAt;
    private final long version;
    private final boolean immediate;
    private DeliveryStatus status;
    private int attempts;
    private Instant nextAttemptAt;
    private Instant claimedAt;
    private Instant sentAt;
    private String lastError;
    private int deliveredDevices;

    private PushDelivery(Long id, DeliveryKey key, PushMessage message, LocalDate deliveryDay, Instant dueAt, boolean immediate,
                         Instant createdAt, long version, DeliveryStatus status, int attempts, Instant nextAttemptAt, Instant claimedAt, Instant sentAt,
                         int deliveredDevices, String lastError) {
        this.id = id;
        this.key = Objects.requireNonNull(key, "key");
        this.message = Objects.requireNonNull(message, "message");
        this.deliveryDay = Objects.requireNonNull(deliveryDay, "deliveryDay");
        this.dueAt = Objects.requireNonNull(dueAt, "dueAt");
        this.immediate = immediate;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.version = version;
        this.status = Objects.requireNonNull(status, "status");
        this.attempts = attempts;
        this.nextAttemptAt = Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
        this.claimedAt = claimedAt;
        this.sentAt = sentAt;
        this.deliveredDevices = deliveredDevices;
        this.lastError = lastError;
    }

    /** 새 계획 — 캠페인이 정한 날·시각에 보낸다. */
    static PushDelivery schedule(DeliveryKey key, PushMessage message, Campaign campaign, Instant now) {
        return new PushDelivery(null, key, message, campaign.deliveryDay(), campaign.dueAt(), campaign.immediate(), now, 0,
            DeliveryStatus.PENDING, 0,
            campaign.dueAt(), null, null, 0, null);
    }

    public static PushDelivery restore(Long id, DeliveryKey key, PushMessage message, LocalDate deliveryDay, Instant dueAt,
                                       boolean immediate, Instant createdAt, long version, DeliveryStatus status, int attempts, Instant nextAttemptAt,
                                       Instant claimedAt, Instant sentAt, int deliveredDevices, String lastError) {
        return new PushDelivery(id, key, message, deliveryDay, dueAt, immediate, createdAt, version, status, attempts, nextAttemptAt, claimedAt,
            sentAt, deliveredDevices, lastError);
    }

    /**
     * 발송기가 잡는다. 보낼 때가 된 PENDING, 또는 claimTimeout 넘게 SENDING 에 머문 것(발송기가 죽음)만. 잡을 때 그날 안에 보낼 수 없으면
     * (조용한 시간·날짜가 바뀜) 만료로 닫는다.
     */
    public ClaimOutcome claim(Instant now, DeliveryPolicy policy) {
        boolean due = status == DeliveryStatus.PENDING && !nextAttemptAt.isAfter(now);
        boolean stale = status == DeliveryStatus.SENDING && claimedAt != null && !claimedAt.plus(policy.claimTimeout()).isAfter(now);
        if (!due && !stale) return ClaimOutcome.NOT_DUE;
        if (!sendableAt(now, policy)) {
            status = DeliveryStatus.EXPIRED;
            lastError = "보낼 날 안에 보내지 못함";
            return ClaimOutcome.EXPIRED;
        }
        status = DeliveryStatus.SENDING;
        claimedAt = now;
        return ClaimOutcome.CLAIMED;
    }

    /**
     * 잡은 뒤 보내기 직전에 동의·설정을 다시 본다(계획한 뒤 그 종류를 껐거나 기기를 모두 해지했을 수 있다). 닿지 않으면 보내지 않고 닫는다.
     *
     * @return 보내도 되면 true
     */
    public boolean confirmReach(Reach reach, Instant claim) {
        requireHeld(claim);
        if (reach == Reach.KIND_OFF) cancel(KIND_OFF);
        if (reach == Reach.NO_DEVICE) cancel(NO_DEVICE);
        return reach == Reach.REACHABLE;
    }

    private void cancel(String reason) {
        status = DeliveryStatus.CANCELLED;
        lastError = reason;
    }

    /**
     * 보낸 결과로 닫거나 다시 보낼 시각을 정한다. 자기가 잡은(claim 시각이 같은) 기록만 — 그새 다른 발송기가 다시 잡았으면
     * {@link DeliveryReclaimed}. @return 바뀐 상태
     */
    public DeliveryStatus complete(SendReport report, Instant claim, Instant now, DeliveryPolicy policy) {
        requireHeld(claim);
        attempts++;
        lastError = report.anyDelivered() ? null : report.summary();
        if (report.anyDelivered()) {
            status = DeliveryStatus.SENT;
            sentAt = now;
            deliveredDevices = report.deliveredCount();
        } else if (report.allGone()) {
            status = DeliveryStatus.CANCELLED;
            lastError = NO_DEVICE;
        } else if (report.retryable() && !policy.retry().exhausted(attempts)) {
            Instant next = policy.retry().nextAttemptAt(attempts, now, report.retryAfter());
            status = sendableAt(next, policy) ? DeliveryStatus.PENDING : DeliveryStatus.EXPIRED;
            nextAttemptAt = next;
        } else {
            status = DeliveryStatus.FAILED;
        }
        return status;
    }

    /** 보냈으면(SENT) 그 사실 — 분석에 알릴 공개 이벤트의 재료. */
    public Optional<DeliverySent> sent() {
        if (status != DeliveryStatus.SENT) return Optional.empty();
        return Optional.of(new DeliverySent(key, deliveredDevices, sentAt));
    }

    /** 이 시각에 보내도 되는지 — 보낼 날(서울 날짜) 안이고 조용한 시간이 아니다(local 즉시 발송은 조용한 시간에도). */
    public boolean sendableAt(Instant at, DeliveryPolicy policy) {
        return policy.quietHours().dayOf(at).equals(deliveryDay) && (immediate || policy.quietHours().allows(at));
    }

    /** 이 claim 으로 잡은 채인지. */
    public boolean heldBy(Instant claim) {
        return status == DeliveryStatus.SENDING && claim != null && claim.equals(claimedAt);
    }

    private void requireHeld(Instant claim) {
        if (!heldBy(claim)) throw new DeliveryReclaimed(key, status);
    }

    public Optional<Long> id() { return Optional.ofNullable(id); }
    public DeliveryKey key() { return key; }
    public PushMessage message() { return message; }
    public LocalDate deliveryDay() { return deliveryDay; }
    public Instant dueAt() { return dueAt; }
    public boolean immediate() { return immediate; }
    public Instant createdAt() { return createdAt; }
    public long version() { return version; }
    public DeliveryStatus status() { return status; }
    public int attempts() { return attempts; }
    public Instant nextAttemptAt() { return nextAttemptAt; }
    public Optional<Instant> claimedAt() { return Optional.ofNullable(claimedAt); }
    public Optional<Instant> sentAt() { return Optional.ofNullable(sentAt); }
    public int deliveredDevices() { return deliveredDevices; }
    public Optional<String> lastError() { return Optional.ofNullable(lastError); }
}
