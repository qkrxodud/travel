package com.kobi.territory.exploration.domain.territory;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 일급 컬렉션: (지역, 멤버)별 마지막 체크인 회차(결정 6). 방문은 취소하면 물리 삭제되므로 회차는 따로 기억한다
 * (visit_generation 행). 다시 칠하면 다음 회차를 받는다. 저장소가 바뀐 행만 쓰도록 복원 이후 바뀐 키를 기억한다.
 */
public final class VisitGenerations {

    private final Map<VisitKey, Integer> lastByKey = new LinkedHashMap<>();
    private final Set<VisitKey> changed = new LinkedHashSet<>();

    private VisitGenerations() {}

    public static VisitGenerations empty() {
        return new VisitGenerations();
    }

    /**
     * 복원. 저장된 회차와 지금 있는 방문의 회차 중 큰 값을 마지막 회차로 본다(회차 행이 없던 예전 방문은 1회차).
     */
    public static VisitGenerations restore(Collection<VisitGeneration> rows, Collection<Visit> visits) {
        VisitGenerations generations = new VisitGenerations();
        rows.forEach(row -> generations.lastByKey.merge(new VisitKey(row.region(), row.member()), row.last(), Math::max));
        visits.forEach(visit -> generations.lastByKey.merge(new VisitKey(visit.regionCode(), visit.checkedInBy()),
            visit.generation(), Math::max));
        return generations;
    }

    /** 다음 회차를 발급하고 기억한다. */
    int next(RegionCode region, ExplorerId member) {
        VisitKey key = new VisitKey(region, member);
        int next = lastByKey.getOrDefault(key, 0) + 1;
        lastByKey.put(key, next);
        changed.add(key);
        return next;
    }

    public int last(RegionCode region, ExplorerId member) {
        return lastByKey.getOrDefault(new VisitKey(region, member), 0);
    }

    /** 복원 이후 바뀐 회차(저장소가 이것만 쓴다). */
    public List<VisitGeneration> changed() {
        return changed.stream().map(key -> new VisitGeneration(key.region(), key.member(), lastByKey.get(key))).toList();
    }

    /** (지역, 멤버)의 마지막 회차 한 건(visit_generation 행). */
    public record VisitGeneration(RegionCode region, ExplorerId member, int last) {
        public VisitGeneration {
            Objects.requireNonNull(region, "region");
            Objects.requireNonNull(member, "member");
            if (last < 1) throw new IllegalArgumentException("last >= 1: " + last);
        }
    }

    private record VisitKey(RegionCode region, ExplorerId member) {}
}
