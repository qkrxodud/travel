package com.kobi.territory.exploration.domain.wishlist;

import com.kobi.territory.common.model.RegionCode;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 일급 컬렉션: 가고 싶은 곳 핀(지역당 하나). 저장소가 바뀐 행만 쓰도록 복원 이후 바뀐 지역·뺀 지역을 기억한다. */
public final class WishPins {

    private final Map<RegionCode, WishPin> byRegion = new LinkedHashMap<>();
    private final Set<RegionCode> changed = new LinkedHashSet<>();
    private final Set<RegionCode> removed = new LinkedHashSet<>();

    private WishPins(Collection<WishPin> restored) {
        restored.forEach(pin -> {
            if (byRegion.put(pin.region(), pin) != null) throw new IllegalStateException("핀 중복: " + pin.region());
        });
    }

    public static WishPins empty() {
        return new WishPins(List.of());
    }

    public static WishPins of(Collection<WishPin> restored) {
        return new WishPins(restored);
    }

    public Optional<WishPin> find(RegionCode region) {
        return Optional.ofNullable(byRegion.get(region));
    }

    /** 아직 다녀오지 않은 핀 수(상한 기준). */
    public int pendingCount() {
        return (int) byRegion.values().stream().filter(pin -> !pin.fulfilled()).count();
    }

    /** 아직 다녀오지 않은 핀의 지역. */
    public List<RegionCode> pendingRegions() {
        return byRegion.values().stream().filter(pin -> !pin.fulfilled()).map(WishPin::region).toList();
    }

    public int fulfilledCount() {
        return (int) byRegion.values().stream().filter(WishPin::fulfilled).count();
    }

    void put(WishPin pin) {
        byRegion.put(pin.region(), pin);
        removed.remove(pin.region());
        changed.add(pin.region());
    }

    boolean remove(RegionCode region) {
        if (byRegion.remove(region) == null) return false;
        changed.remove(region);
        removed.add(region);
        return true;
    }

    /** 최근에 꽂은 순(같으면 지역 코드 순). */
    public List<WishPin> newestFirst() {
        return byRegion.values().stream().sorted(Comparator.comparing(WishPin::pinnedAt).reversed()
            .thenComparing(pin -> pin.region().value())).toList();
    }

    /** 다녀온 핀(다녀온 순). */
    public List<WishPin> fulfilled() {
        return byRegion.values().stream().filter(WishPin::fulfilled).sorted(Comparator.comparing(WishPin::fulfilledAt)).toList();
    }

    /** 복원 이후 새로 꽂았거나 바뀐 핀(저장소가 넣거나 고친다). */
    public List<WishPin> changed() {
        return changed.stream().map(byRegion::get).toList();
    }

    /** 복원 이후 뺀 지역(저장소가 지운다). */
    public Set<RegionCode> removed() {
        return Set.copyOf(removed);
    }
}
