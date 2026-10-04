package com.kobi.territory.notification.infra.entity;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.notification.domain.push.DeviceKeys;
import com.kobi.territory.notification.domain.push.PushEndpoint;
import com.kobi.territory.notification.domain.recipient.PushDevice;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * push_device — 기기(브라우저 구독) 한 줄(12단계 V10, PushRecipient 의 자식). 열쇠는 구독 주소의 SHA-256 — 한 브라우저 구독은 한 탐험가에게만.
 */
@Entity
@Table(name = "push_device")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushDeviceJpaEntity {

    @Id
    @Column(name = "endpoint_hash", length = 64)
    private String endpointHash;

    @Column(name = "explorer_id", nullable = false, length = 36)
    private String explorerId;

    @Column(nullable = false, length = 1024)
    private String endpoint;

    @Column(nullable = false, length = 128)
    private String p256dh;

    @Column(nullable = false, length = 64)
    private String auth;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    public static PushDeviceJpaEntity from(ExplorerId owner, PushDevice device) {
        PushDeviceJpaEntity entity = new PushDeviceJpaEntity();
        entity.endpointHash = device.endpoint().fingerprint();
        entity.apply(owner, device);
        return entity;
    }

    public void apply(ExplorerId owner, PushDevice device) {
        this.explorerId = owner.value();
        this.endpoint = device.endpoint().value();
        this.p256dh = device.keys().p256dh();
        this.auth = device.keys().auth();
        this.registeredAt = device.registeredAt();
    }

    public PushDevice toDomain() {
        return new PushDevice(PushEndpoint.of(endpoint), new DeviceKeys(p256dh, auth), registeredAt);
    }

    public String endpointHash() {
        return endpointHash;
    }
}
