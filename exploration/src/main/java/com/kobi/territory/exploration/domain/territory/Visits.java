package com.kobi.territory.exploration.domain.territory;

import com.kobi.territory.exploration.domain.ExplorationError;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 일급 컬렉션: 한 지도(Territory)의 방문 목록. (지역, 멤버) 유일성을 지키고, 방문 집합에 대한 질의
 * (멤버별·지역별 조회, 선점자, 하루 처리 건수, 시·도 방문 여부, 칠해진 지역, 최근 순 정렬)를 담당한다.
 * 변경(add/remove)은 Territory만 한다.
 */
public final class Visits {

    private final List<Visit> items;

    private Visits(List<Visit> items) {
        this.items = items;
    }

    public static Visits empty() {
        return new Visits(new ArrayList<>());
    }

    /** 복원. (지역, 멤버)가 두 번 나오면 데이터 손상으로 거부한다. */
    public static Visits of(Collection<Visit> visits) {
        Visits out = empty();
        for (Visit visit : visits) {
            if (out.find(visit.regionCode(), visit.checkedInBy()).isPresent()) {
                throw new IllegalStateException("중복 방문 데이터: " + visit.regionCode() + " " + visit.checkedInBy());
            }
            out.items.add(visit);
        }
        return out;
    }

    void add(Visit visit) {
        if (find(visit.regionCode(), visit.checkedInBy()).isPresent()) {
            throw ExplorationError.DUPLICATE_VISIT.exception(visit.regionCode().value());
        }
        items.add(visit);
    }

    void remove(Visit visit) {
        items.remove(visit);
    }

    public Optional<Visit> find(RegionCode code, ExplorerId member) {
        return items.stream().filter(visit -> visit.is(code, member)).findFirst();
    }

    public Visit require(RegionCode code, ExplorerId member) {
        return find(code, member).orElseThrow(() -> ExplorationError.VISIT_NOT_FOUND.exception(code.value()));
    }

    public boolean contains(RegionCode code, ExplorerId member) {
        return find(code, member).isPresent();
    }

    /** 이 멤버의 방문만. */
    public Visits of(ExplorerId member) {
        return new Visits(new ArrayList<>(items.stream().filter(visit -> visit.checkedInBy().equals(member)).toList()));
    }

    /** 지도에서 누구든 이 지역을 칠했는지. */
    public boolean anyIn(RegionCode code) {
        return items.stream().anyMatch(visit -> visit.regionCode().equals(code));
    }

    /** 이 시·도에 방문이 있는지. */
    public boolean touches(String provinceCode) {
        return items.stream().anyMatch(visit -> visit.region().provinceCode().equals(provinceCode));
    }

    /** 지역의 선점 방문(지도 내 최초 체크인). */
    public Optional<Visit> claimOf(RegionCode code) {
        return items.stream().filter(visit -> visit.regionCode().equals(code)).min(Comparator.comparing(Visit::visitedAt));
    }

    /** day(zone 기준)에 처리된(visitedAt) 방문 수. */
    public int processedOn(LocalDate day, ZoneId zone) {
        return (int) items.stream().filter(visit -> visit.visitedAt().atZone(zone).toLocalDate().equals(day)).count();
    }

    /** 칠해진 지역(멤버 무관, 중복 제거). 정복률 계산 단위. */
    public Set<RegionSnapshot> regions() {
        Set<RegionSnapshot> out = new LinkedHashSet<>();
        items.forEach(visit -> out.add(visit.region()));
        return out;
    }

    /** 방문일 최근 순, 같으면 처리 시각 최근 순(탐험 일지 정렬). */
    public List<Visit> recentFirst() {
        return items.stream()
            .sorted(Comparator.comparing((Visit visit) -> visit.visitDate().value()).reversed()
                .thenComparing(Comparator.comparing(Visit::visitedAt).reversed()))
            .toList();
    }

    /** 처리 시각(visitedAt) 순(같으면 저장 순). */
    public List<Visit> chronological() {
        return items.stream().sorted(Comparator.comparing(Visit::visitedAt)).toList();
    }

    public int size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public List<Visit> asList() {
        return List.copyOf(items);
    }
}
