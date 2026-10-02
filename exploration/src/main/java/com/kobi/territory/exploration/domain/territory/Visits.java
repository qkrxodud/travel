package com.kobi.territory.exploration.domain.territory;

import com.kobi.territory.exploration.domain.ExplorationError;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 일급 컬렉션: 한 지도(Territory)의 방문 목록. (지역, 멤버) 유일성을 지키고, 방문 집합에 대한 질의
 * (멤버별·지역별 조회, 선점자, 하루 처리 건수, 시·도 방문 여부, 칠해진 지역, 최근 순 정렬)를 담당한다.
 * 탈퇴 유예로 숨긴 방문(hidden)은 (지역, 멤버) 유일성에만 참여하고 그 밖의 질의(지도에 보이는 것)에서는 빠진다.
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

    /** (지역, 멤버)의 방문 — 숨긴 방문 포함(유일성 판단용). */
    public Optional<Visit> find(RegionCode code, ExplorerId member) {
        return items.stream().filter(visit -> visit.is(code, member)).findFirst();
    }

    /** (지역, 멤버)의 보이는 방문 — 없으면 VISIT_NOT_FOUND. 수정·취소·이의의 대상. */
    public Visit require(RegionCode code, ExplorerId member) {
        return visible().filter(visit -> visit.is(code, member)).findFirst()
            .orElseThrow(() -> ExplorationError.VISIT_NOT_FOUND.exception(code.value()));
    }

    public boolean contains(RegionCode code, ExplorerId member) {
        return find(code, member).isPresent();
    }

    /** 이 멤버의 보이는 방문만. */
    public Visits of(ExplorerId member) {
        return new Visits(new ArrayList<>(visible().filter(visit -> visit.checkedInBy().equals(member)).toList()));
    }

    /** 이 멤버가 at 까지 처리한(visitedAt ≤ at) 보이는 방문. */
    public List<Visit> of(ExplorerId member, Instant until) {
        return visible().filter(visit -> visit.checkedInBy().equals(member) && !visit.visitedAt().isAfter(until)).toList();
    }

    /** 이 멤버가 선점자인 지역 수. */
    public int claimCountOf(ExplorerId member) {
        return (int) claims().stream().filter(claim -> claim.checkedInBy().equals(member)).count();
    }

    /** candidates 중 지금 그 지역의 선점인 방문. */
    public List<Visit> claimsAmong(List<Visit> candidates) {
        return candidates.stream().filter(visit -> claimOf(visit.regionCode()).filter(claim -> claim == visit).isPresent()).toList();
    }

    /** candidates 의 지역 중 지금 지도에 아무도 칠하지 않은 지역. */
    public List<RegionCode> regionsGoneAmong(List<Visit> candidates) {
        return candidates.stream().map(Visit::regionCode).filter(code -> !anyIn(code)).toList();
    }

    /** 지도장이 이의 표시한 보이는 방문. */
    public List<Visit> disputed() {
        return visible().filter(Visit::disputed).toList();
    }

    /** 이 멤버의 숨긴 방문(탈퇴 유예 중). */
    public List<Visit> hiddenOf(ExplorerId member) {
        return items.stream().filter(Visit::hidden).filter(visit -> visit.checkedInBy().equals(member)).toList();
    }

    /** 지도에서 누구든 이 지역을 칠했는지(보이는 방문 기준). */
    public boolean anyIn(RegionCode code) {
        return visible().anyMatch(visit -> visit.regionCode().equals(code));
    }

    /** 이 시·도에 보이는 방문이 있는지. */
    public boolean touches(String provinceCode) {
        return visible().anyMatch(visit -> visit.region().provinceCode().equals(provinceCode));
    }

    /** 지역의 선점 방문 — 보이는 방문 중 선점 순서(claimRankAt, 같으면 처리 시각)가 가장 이른 것. */
    public Optional<Visit> claimOf(RegionCode code) {
        return visible().filter(visit -> visit.regionCode().equals(code))
            .min(Comparator.comparing(Visit::claimRankAt).thenComparing(Visit::visitedAt));
    }

    /** 지역마다 선점 방문(지역 코드 순). */
    public List<Visit> claims() {
        return regions().stream().map(region -> claimOf(region.code()).orElseThrow())
            .sorted(Comparator.comparing(Visit::regionCode, Comparator.comparing(RegionCode::value))).toList();
    }

    /** day(zone 기준)에 처리된(visitedAt) 보이는 방문 수. */
    public int processedOn(LocalDate day, ZoneId zone) {
        return (int) visible().filter(visit -> visit.visitedAt().atZone(zone).toLocalDate().equals(day)).count();
    }

    /** 칠해진 지역(멤버 무관, 중복 제거, 보이는 방문 기준). 정복률 계산 단위. */
    public Set<RegionSnapshot> regions() {
        Set<RegionSnapshot> out = new LinkedHashSet<>();
        visible().forEach(visit -> out.add(visit.region()));
        return out;
    }

    /** 방문일 최근 순, 같으면 처리 시각 최근 순(탐험 일지 정렬). 보이는 방문만. */
    public List<Visit> recentFirst() {
        return visible()
            .sorted(Comparator.comparing((Visit visit) -> visit.visitDate().value()).reversed()
                .thenComparing(Comparator.comparing(Visit::visitedAt).reversed()))
            .toList();
    }

    /** 처리 시각(visitedAt) 순(같으면 저장 순). 보이는 방문만. */
    public List<Visit> chronological() {
        return visible().sorted(Comparator.comparing(Visit::visitedAt)).toList();
    }

    /** 보이는 방문 수. */
    public int size() {
        return (int) visible().count();
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    /** 보이는 방문(불변 사본). */
    public List<Visit> asList() {
        return visible().toList();
    }

    /** 숨긴 방문까지 전부(저장용, 불변 사본). */
    public List<Visit> all() {
        return List.copyOf(items);
    }

    private Stream<Visit> visible() {
        return items.stream().filter(visit -> !visit.hidden());
    }
}
