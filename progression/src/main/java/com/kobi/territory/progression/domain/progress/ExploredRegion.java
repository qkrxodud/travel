package com.kobi.territory.progression.domain.progress;

import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.common.model.RegionCode;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 탐험가가 밟은 지역 한 곳(explorer_region 행). 여러 지도에서 같은 지역을 칠해도 하나다.
 * activeMaps: 지금 이 지역 방문이 살아 있는 지도들 — 개수가 active_map_count(D2). 0이 되면 기본 XP 회수 대상.
 * 지도 id 집합으로 들고 있는 이유: 같은 RegionVisited/VisitCancelled 가 두 번 와도 +1/−1 이 두 번 되지 않게(멱등).
 * <p>
 * marks(결정 6): 지도마다 마지막으로 반영한 방문 이벤트의 회차 — 양수 g = g회차 체크인, 음수 −g = g회차 취소.
 * 이보다 오래된 회차의 이벤트(늦게 온 재전달)는 무시한다. 회차 0(필드가 없던 예전 이벤트)은 그 지도에 표시가 하나라도 있으면
 * 더 오래된 것으로 보고 무시한다(Q3 — 이제 모든 이벤트가 회차를 싣는다). 표시가 없을 때만 반영한다.
 * 다음 상태(지도 추가·제거)를 계산하므로 class(class vs record 기준).
 */
public final class ExploredRegion {

    private final RegionCode code;
    private final String provinceCode;
    private final Rarity rarity;
    /** 처음 밟은 처리 시각(전체 랭킹·상위%·도감 뱃지용 — 탈퇴로 지도가 사라져도 남는다). */
    private final Instant firstVisitedAt;
    private final Set<String> activeMaps;
    private final Map<String, Integer> marks;

    private ExploredRegion(RegionCode code, String provinceCode, Rarity rarity, Instant firstVisitedAt,
                           Set<String> activeMaps, Map<String, Integer> marks) {
        this.code = Objects.requireNonNull(code, "code");
        this.provinceCode = Objects.requireNonNull(provinceCode, "provinceCode");
        this.rarity = Objects.requireNonNull(rarity, "rarity");
        this.firstVisitedAt = Objects.requireNonNull(firstVisitedAt, "firstVisitedAt");
        this.activeMaps = Set.copyOf(new TreeSet<>(activeMaps));
        this.marks = Map.copyOf(new TreeMap<>(marks));
    }

    /** 저장된 값으로 복원(회차 표시가 없던 예전 행은 marks 비움). */
    public static ExploredRegion restore(RegionCode code, String provinceCode, Rarity rarity, Instant firstVisitedAt,
                                         Set<String> activeMaps, Map<String, Integer> marks) {
        return new ExploredRegion(code, provinceCode, rarity, firstVisitedAt, activeMaps, marks);
    }

    /** 처음 밟은 지역(아직 활성 지도 없음). */
    static ExploredRegion firstVisit(RegionCode code, String provinceCode, Rarity rarity, Instant at) {
        return new ExploredRegion(code, provinceCode, rarity, at, Set.of(), Map.of());
    }

    public boolean active() {
        return !activeMaps.isEmpty();
    }

    public int activeMapCount() {
        return activeMaps.size();
    }

    /** 이 지도에서 generation 회차 체크인 이벤트가 이미 지난 일인지(더 새 회차를 봤거나 같은 회차의 취소를 봤다). */
    boolean staleVisit(String mapId, int generation) {
        if (generation == 0) return marks.containsKey(mapId);
        int mark = marks.getOrDefault(mapId, 0);
        return generation < Math.abs(mark) || generation == -mark;
    }

    /** 이 지도에서 generation 회차 취소 이벤트가 이미 지난 일인지(더 새 회차를 봤다). */
    boolean staleCancel(String mapId, int generation) {
        if (generation == 0) return marks.containsKey(mapId);
        return generation < Math.abs(marks.getOrDefault(mapId, 0));
    }

    ExploredRegion withMap(String mapId, int generation) {
        Set<String> maps = new TreeSet<>(activeMaps);
        maps.add(mapId);
        return new ExploredRegion(code, provinceCode, rarity, firstVisitedAt, maps, mark(mapId, generation));
    }

    ExploredRegion withoutMap(String mapId, int generation) {
        Set<String> maps = new TreeSet<>(activeMaps);
        maps.remove(mapId);
        return new ExploredRegion(code, provinceCode, rarity, firstVisitedAt, maps, mark(mapId, -generation));
    }

    /**
     * 재계산용: 처음 밟은 시각만 남기고, 지금 멤버인 지도의 활성·회차 표시를 비운 사본 — 재생이 현재 방문(과 그 회차)으로 다시
     * 채운다(손상된 표시도 복구된다 — QA P3-4). 단 frozen(탈퇴해 이제 멤버가 아닌 지도)의 활성·표시는 남긴다 — 탈퇴는 탐험가 단위
     * 기록을 줄이지 않는다(§5). 취소된 지역의 표시는 재생할 방문이 없어 사라진다 — 재계산은 미전달 이벤트가 없을 때만 돌므로
     * (S3-3) 늦게 올 예전 이벤트가 없다.
     */
    ExploredRegion deactivatedExcept(Set<String> frozenMaps) {
        Set<String> kept = new TreeSet<>(activeMaps);
        kept.retainAll(frozenMaps);
        Map<String, Integer> keptMarks = new TreeMap<>(marks);
        keptMarks.keySet().retainAll(frozenMaps);
        return new ExploredRegion(code, provinceCode, rarity, firstVisitedAt, kept, keptMarks);
    }

    private Map<String, Integer> mark(String mapId, int signedGeneration) {
        Map<String, Integer> next = new TreeMap<>(marks);
        if (signedGeneration != 0) next.put(mapId, signedGeneration);
        return next;
    }

    public RegionCode code() { return code; }
    public String provinceCode() { return provinceCode; }
    public Rarity rarity() { return rarity; }
    public Instant firstVisitedAt() { return firstVisitedAt; }
    public Set<String> activeMaps() { return activeMaps; }
    /** 지도 → 마지막으로 반영한 회차(양수 체크인·음수 취소). */
    public Map<String, Integer> marks() { return marks; }

    @Override
    public boolean equals(Object other) {
        return other instanceof ExploredRegion region && code.equals(region.code) && provinceCode.equals(region.provinceCode)
            && rarity == region.rarity && firstVisitedAt.equals(region.firstVisitedAt) && activeMaps.equals(region.activeMaps)
            && marks.equals(region.marks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, provinceCode, rarity, firstVisitedAt, activeMaps, marks);
    }

    @Override
    public String toString() {
        return "ExploredRegion[" + code + ", maps=" + activeMaps + ", marks=" + marks + ", first=" + firstVisitedAt + "]";
    }
}
