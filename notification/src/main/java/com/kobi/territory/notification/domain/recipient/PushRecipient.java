package com.kobi.territory.notification.domain.recipient;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.notification.domain.policy.NotificationKind;
import com.kobi.territory.notification.domain.policy.Reach;
import com.kobi.territory.notification.domain.push.DeviceKeys;
import com.kobi.territory.notification.domain.push.PushEndpoint;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 애그리거트: 알림 받는 사람 — 탐험가 한 명의 종류별 켜고 끄기와 기기(브라우저 구독)들. 루트 행(push_recipient)이 구독·해지·설정·발송 계획을
 * 탐험가 단위로 줄 세우는 잠금 대상이다(기기 수 상한·하루 최대 개수가 "지금 몇 개인가"로 판단하므로).
 * <p>
 * 동의: 화면이 첫 체크인 뒤 "알림 받을래요?"에 예라고 하고 브라우저 권한까지 받았을 때만 구독을 보낸다 — 서버에서는 기기가 있다는 것이
 * 동의다. 기기가 하나도 없으면 아무 알림도 계획하지 않는다.
 */
public final class PushRecipient {

    private final ExplorerId explorerId;
    private NotificationPreferences preferences;
    private final Devices devices;
    private final Instant createdAt;
    private Instant updatedAt;

    private PushRecipient(ExplorerId explorerId, NotificationPreferences preferences, Devices devices, Instant createdAt,
                          Instant updatedAt) {
        this.explorerId = Objects.requireNonNull(explorerId, "explorerId");
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.devices = Objects.requireNonNull(devices, "devices");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /** 처음(모든 종류 켜짐, 기기 없음). */
    public static PushRecipient start(ExplorerId explorerId, Instant at) {
        return new PushRecipient(explorerId, NotificationPreferences.ALL_ON, Devices.none(), at, at);
    }

    public static PushRecipient restore(ExplorerId explorerId, NotificationPreferences preferences, Collection<PushDevice> devices,
                                        Instant createdAt, Instant updatedAt) {
        return new PushRecipient(explorerId, preferences, Devices.of(devices), createdAt, updatedAt);
    }

    /**
     * 기기 구독(브라우저가 보낸 구독). 받을 수 없는 주소면 PUSH_ENDPOINT_NOT_ALLOWED. 같은 주소면 키·등록 시각만 갱신(멱등 — 화면은 앱을 열
     * 때마다 다시 보내도 된다), 상한을 넘으면 가장 오래 전에 등록한 기기를 뺀다.
     */
    public SubscribeResult subscribe(PushEndpoint endpoint, DeviceKeys keys, Instant at, DevicePolicy policy) {
        policy.endpointRules().require(endpoint);
        boolean added = !devices.contains(endpoint);
        List<PushEndpoint> evicted = devices.register(new PushDevice(endpoint, keys, at), policy.maxDevices());
        updatedAt = at;
        return new SubscribeResult(added, evicted, devices.count());
    }

    /** 기기 해지(없으면 그대로). @return 있었으면 true */
    public boolean unsubscribe(PushEndpoint endpoint, Instant at) {
        boolean removed = devices.remove(endpoint);
        if (removed) updatedAt = at;
        return removed;
    }

    /** 푸시 서비스가 "구독이 없다"(404·410)고 한 기기를 지운다(그새 다시 구독했으면 — 등록 시각이 보낸 시각보다 늦으면 — 남긴다). */
    public List<PushEndpoint> forgetGone(Collection<PushEndpoint> gone, Instant sentAt, Instant at) {
        List<PushEndpoint> forgotten = gone.stream()
            .filter(endpoint -> devices.find(endpoint).filter(device -> !device.registeredAt().isAfter(sentAt)).isPresent())
            .filter(endpoint -> devices.remove(endpoint)).toList();
        if (!forgotten.isEmpty()) updatedAt = at;
        return forgotten;
    }

    public void changePreferences(NotificationPreferences changed, Instant at) {
        this.preferences = Objects.requireNonNull(changed, "preferences");
        this.updatedAt = at;
    }

    /**
     * 계정 병합(익명 → 계정): 익명 쪽 기기를 이 사람에게 옮긴다(같은 브라우저면 한 기기, 상한을 넘으면 오래된 기기부터 뺀다). 설정은 이 사람 것을
     * 그대로 둔다. @return 옮긴 기기 수
     */
    public int absorb(PushRecipient merged, Instant at, DevicePolicy policy) {
        List<PushDevice> moving = merged.devices.stream().toList();
        moving.forEach(device -> {
            devices.register(device, policy.maxDevices());
            merged.devices.remove(device.endpoint());
        });
        if (!moving.isEmpty()) {
            updatedAt = at;
            merged.updatedAt = at;
        }
        return moving.size();
    }

    /** 이 종류의 알림이 닿을 수 있는지. */
    public Reach reach(NotificationKind kind) {
        if (devices.isEmpty()) return Reach.NO_DEVICE;
        return preferences.allows(kind) ? Reach.REACHABLE : Reach.KIND_OFF;
    }

    public ExplorerId explorerId() { return explorerId; }
    public NotificationPreferences preferences() { return preferences; }
    public Devices devices() { return devices; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
