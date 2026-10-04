package com.kobi.territory.notification.infra.entity;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.notification.domain.delivery.DeliveryKey;
import com.kobi.territory.notification.domain.delivery.DeliveryStatus;
import com.kobi.territory.notification.domain.delivery.PushDelivery;
import com.kobi.territory.notification.domain.policy.NotificationKind;
import com.kobi.territory.notification.domain.push.PushMessage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * push_delivery — 발송 기록 한 줄(12단계 V10). UNIQUE(explorer_id, kind, period) 가 멱등 열쇠, (explorer_id, delivery_day) 가 하루 최대 개수
 * 판단, (status, next_attempt_at) 이 발송기의 보낼 때가 된 기록 찾기. version 은 발송기 잡기의 낙관적 잠금.
 */
@Entity
@Table(name = "push_delivery")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushDeliveryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "explorer_id", nullable = false, length = 36)
    private String explorerId;

    @Column(nullable = false, length = 20)
    private String kind;

    @Column(nullable = false, length = 40)
    private String period;

    @Column(name = "delivery_day", nullable = false)
    private LocalDate deliveryDay;

    @Column(nullable = false, length = 12)
    private String status;

    @Column(nullable = false, length = 80)
    private String title;

    @Column(nullable = false, length = 240)
    private String body;

    @Column(nullable = false, length = 200)
    private String url;

    @Column(nullable = false, length = 64)
    private String tag;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(nullable = false)
    private boolean immediate;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "delivered_devices", nullable = false)
    private int deliveredDevices;

    @Column(name = "last_error", length = 200)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    private long version;

    public static PushDeliveryJpaEntity from(PushDelivery delivery) {
        PushDeliveryJpaEntity entity = new PushDeliveryJpaEntity();
        DeliveryKey key = delivery.key();
        PushMessage message = delivery.message();
        entity.explorerId = key.explorerId().value();
        entity.kind = key.kind().code();
        entity.period = key.period();
        entity.deliveryDay = delivery.deliveryDay();
        entity.title = message.title();
        entity.body = message.body();
        entity.url = message.url();
        entity.tag = message.tag();
        entity.dueAt = delivery.dueAt();
        entity.immediate = delivery.immediate();
        entity.createdAt = delivery.createdAt();
        entity.apply(delivery);
        return entity;
    }

    /** 바뀌는 칸(상태·시도·시각·결과)만. */
    public void apply(PushDelivery delivery) {
        this.status = delivery.status().name();
        this.attempts = delivery.attempts();
        this.nextAttemptAt = delivery.nextAttemptAt();
        this.claimedAt = delivery.claimedAt().orElse(null);
        this.sentAt = delivery.sentAt().orElse(null);
        this.deliveredDevices = delivery.deliveredDevices();
        this.lastError = delivery.lastError().map(error -> error.length() > 200 ? error.substring(0, 200) : error).orElse(null);
    }

    public PushDelivery toDomain() {
        NotificationKind notificationKind = NotificationKind.ofCode(kind).orElseThrow(() -> new IllegalStateException("알 수 없는 알림 종류: " + kind));
        return PushDelivery.restore(id, new DeliveryKey(ExplorerId.of(explorerId), notificationKind, period),
            new PushMessage(notificationKind, title, body, url, tag), deliveryDay, dueAt, immediate, createdAt, version, DeliveryStatus.valueOf(status),
            attempts, nextAttemptAt, claimedAt, sentAt, deliveredDevices, lastError);
    }

    public long version() {
        return version;
    }
}
