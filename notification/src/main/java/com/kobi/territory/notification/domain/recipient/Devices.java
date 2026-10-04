package com.kobi.territory.notification.domain.recipient;

import com.kobi.territory.notification.domain.push.PushEndpoint;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 일급 컬렉션: 한 사람의 기기들. 같은 주소는 한 기기(다시 보내면 키·등록 시각만 바뀐다). 상한을 넘으면 가장 오래 전에 등록한 기기부터 뺀다
 * — 새 기기를 거절하지 않는 이유: 사용자가 지금 켠 브라우저에서 알림이 오지 않으면 고칠 방법이 없다. 저장소가 지운 기기만 지우도록
 * 뺀 주소를 기억한다({@link #removed()}).
 */
public final class Devices {

    private final List<PushDevice> devices;
    private final Set<PushEndpoint> removed = new LinkedHashSet<>();

    private Devices(Collection<PushDevice> devices) {
        this.devices = new ArrayList<>(devices);
        this.devices.sort(Comparator.comparing(PushDevice::registeredAt));
    }

    public static Devices of(Collection<PushDevice> devices) {
        return new Devices(devices);
    }

    public static Devices none() {
        return new Devices(List.of());
    }

    /** 등록(같은 주소면 갱신). @return 상한 때문에 뺀 기기 주소(오래된 순) */
    List<PushEndpoint> register(PushDevice device, int maxDevices) {
        devices.removeIf(existing -> existing.endpoint().equals(device.endpoint()));
        removed.remove(device.endpoint());
        devices.add(device);
        devices.sort(Comparator.comparing(PushDevice::registeredAt));
        List<PushEndpoint> evicted = new ArrayList<>();
        while (devices.size() > maxDevices) {
            PushDevice oldest = devices.stream().filter(existing -> !existing.endpoint().equals(device.endpoint())).findFirst()
                .orElseThrow();
            devices.remove(oldest);
            removed.add(oldest.endpoint());
            evicted.add(oldest.endpoint());
        }
        return evicted;
    }

    /** 뺀다. @return 있었으면 true */
    boolean remove(PushEndpoint endpoint) {
        boolean existed = devices.removeIf(existing -> existing.endpoint().equals(endpoint));
        if (existed) removed.add(endpoint);
        return existed;
    }

    public boolean contains(PushEndpoint endpoint) {
        return find(endpoint).isPresent();
    }

    public Optional<PushDevice> find(PushEndpoint endpoint) {
        return devices.stream().filter(existing -> existing.endpoint().equals(endpoint)).findFirst();
    }

    public int count() {
        return devices.size();
    }

    public boolean isEmpty() {
        return devices.isEmpty();
    }

    /** 등록 순(오래된 것 먼저). */
    public Stream<PushDevice> stream() {
        return List.copyOf(devices).stream();
    }

    /** 이 컬렉션에서 뺀 주소(저장소가 지울 것). */
    public List<PushEndpoint> removed() {
        return List.copyOf(removed);
    }
}
