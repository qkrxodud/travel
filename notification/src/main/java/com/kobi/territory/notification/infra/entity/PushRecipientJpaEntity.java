package com.kobi.territory.notification.infra.entity;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.notification.domain.recipient.NotificationPreferences;
import com.kobi.territory.notification.domain.recipient.PushRecipient;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** push_recipient — 알림 받는 사람 루트 행(12단계 V10). 구독·해지·설정·발송 계획을 탐험가 단위로 줄 세우는 잠금 대상. 자식 push_device. */
@Entity
@Table(name = "push_recipient")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushRecipientJpaEntity {

    @Id
    @Column(name = "explorer_id", length = 36)
    private String explorerId;

    @Column(name = "mystery_enabled", nullable = false)
    private boolean mysteryEnabled;

    @Column(name = "streak_enabled", nullable = false)
    private boolean streakEnabled;

    @Column(name = "season_enabled", nullable = false)
    private boolean seasonEnabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void apply(PushRecipient recipient) {
        NotificationPreferences preferences = recipient.preferences();
        this.mysteryEnabled = preferences.mystery();
        this.streakEnabled = preferences.streak();
        this.seasonEnabled = preferences.season();
        this.updatedAt = recipient.updatedAt();
    }

    public PushRecipient toDomain(List<PushDeviceJpaEntity> deviceRows) {
        return PushRecipient.restore(ExplorerId.of(explorerId), new NotificationPreferences(mysteryEnabled, streakEnabled, seasonEnabled),
            deviceRows.stream().map(PushDeviceJpaEntity::toDomain).toList(), createdAt, updatedAt);
    }
}
