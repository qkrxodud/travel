package com.kobi.territory.progression.domain.progress;

import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.common.model.RegionCode;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 일급 컬렉션: 탐험가 단위 지역(explorer_region). 기본 XP 지급·회수 판단(D2), 시·도 첫 방문(D3·mprov) 판단,
 * 뱃지·칭호·랭킹 집계의 기준이다. "활성" = 지금 어느 지도에든 방문이 살아 있는 지역. 행은 취소해도 남는다(처음 밟은 시각 보존).
 * 저장소가 바뀐 행만 쓰도록 복원 이후 바뀐 지역을 기억한다({@link #changed()}).
 */
public final class ExploredRegions {

    private final Map<RegionCode, ExploredRegion> byCode;
    private final Set<RegionCode> changed = new LinkedHashSet<>();

    private ExploredRegions(Collection<ExploredRegion> restored) {
        this.byCode = new LinkedHashMap<>();
        restored.forEach(region -> {
            if (byCode.put(region.code(), region) != null) throw new IllegalStateException("지역 중복: " + region.code());
        });
    }

    public static ExploredRegions empty() {
        return new ExploredRegions(List.of());
    }

    public static ExploredRegions of(Collection<ExploredRegion> restored) {
        return new ExploredRegions(restored);
    }

    /** 지도 mapId 에서 이 지역 방문이 살아났다. @return 탐험가 기준으로 새로 활성이 됐는지(0 → 1) */
    boolean add(RegionCode code, String provinceCode, Rarity rarity, String mapId, Instant at) {
        ExploredRegion current = byCode.get(code);
        boolean wasActive = current != null && current.active();
        ExploredRegion base = current != null ? current : ExploredRegion.firstVisit(code, provinceCode, rarity, at);
        put(base.withMap(mapId));
        return !wasActive;
    }

    /** 지도 mapId 의 이 지역 방문이 취소됐다. @return 탐험가 기준으로 활성이 끝났는지(1 → 0). 모르는 지도면 no-op(false) */
    boolean remove(RegionCode code, String mapId) {
        ExploredRegion current = byCode.get(code);
        if (current == null || !current.activeMaps().contains(mapId)) return false;
        ExploredRegion next = current.withoutMap(mapId);
        put(next);
        return !next.active();
    }

    private void put(ExploredRegion region) {
        byCode.put(region.code(), region);
        changed.add(region.code());
    }

    /** 이 시·도에 활성 지역이 있는지. */
    public boolean touches(String provinceCode) {
        return active().anyMatch(region -> region.provinceCode().equals(provinceCode));
    }

    /**
     * at 이전에 이 시·도를 밟은 적이 있는지(취소한 지역 포함 — "한 번도 안 가본 시·도" 판정, mprov 퀘스트).
     * 처리 순서와 무관하게 같은 답이 나오도록 처음 밟은 시각으로 판단한다.
     */
    public boolean visitedProvinceBefore(String provinceCode, Instant at) {
        return byCode.values().stream()
            .anyMatch(region -> region.provinceCode().equals(provinceCode) && region.firstVisitedAt().isBefore(at));
    }

    public int activeCount() {
        return (int) active().count();
    }

    public int count(Rarity rarity) {
        return (int) active().filter(region -> region.rarity() == rarity).count();
    }

    /** 시·도 코드 → 활성 지역 수. */
    public Map<String, Integer> perProvince() {
        return active().collect(Collectors.groupingBy(ExploredRegion::provinceCode, LinkedHashMap::new,
            Collectors.summingInt(region -> 1)));
    }

    public Optional<ExploredRegion> find(RegionCode code) {
        return Optional.ofNullable(byCode.get(code));
    }

    public List<ExploredRegion> all() {
        return List.copyOf(byCode.values());
    }

    /** 복원 이후 바뀐 지역(저장소가 이것만 쓴다). */
    public List<ExploredRegion> changed() {
        return changed.stream().map(byCode::get).toList();
    }

    /** 재계산용: 행(처음 밟은 시각)은 남기고 활성 지도만 비운 사본 — 재생이 현재 방문으로 다시 채운다. */
    ExploredRegions deactivated() {
        return new ExploredRegions(byCode.values().stream().map(ExploredRegion::deactivated).toList());
    }

    private Stream<ExploredRegion> active() {
        return byCode.values().stream().filter(ExploredRegion::active);
    }
}
