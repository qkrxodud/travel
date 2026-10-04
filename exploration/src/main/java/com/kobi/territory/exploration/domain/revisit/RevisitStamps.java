package com.kobi.territory.exploration.domain.revisit;

import com.kobi.territory.common.model.RegionCode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/** 일급 컬렉션: 한 탐험가의 재방문 도장. 같은 (지역, 연도)는 하나. 복원 이후 새로 받은 도장을 기억한다(저장소가 그것만 넣는다). */
public final class RevisitStamps {

    private final List<RevisitStamp> stamps = new ArrayList<>();
    private final List<RevisitStamp> added = new ArrayList<>();

    private RevisitStamps(Collection<RevisitStamp> restored) {
        restored.forEach(stamp -> {
            if (has(stamp.region(), stamp.year())) throw new IllegalStateException("도장 중복: " + stamp);
            stamps.add(stamp);
        });
    }

    public static RevisitStamps empty() {
        return new RevisitStamps(List.of());
    }

    public static RevisitStamps of(Collection<RevisitStamp> restored) {
        return new RevisitStamps(restored);
    }

    public boolean has(RegionCode region, int year) {
        return stamps.stream().anyMatch(stamp -> stamp.sameAs(region, year));
    }

    /** 오늘(zone 기준) 받은 도장 수 — 하루 체크인 상한을 함께 쓴다. */
    public int countOn(LocalDate day, ZoneId zone) {
        return (int) stamps.stream().filter(stamp -> stamp.stampedAt().atZone(zone).toLocalDate().equals(day)).count();
    }

    /** 이 지역에서 받은 도장의 연도(오름차순). */
    public List<Integer> yearsOf(RegionCode region) {
        return stamps.stream().filter(stamp -> stamp.region().equals(region)).map(RevisitStamp::year).sorted().toList();
    }

    public int count() {
        return stamps.size();
    }

    void add(RevisitStamp stamp) {
        stamps.add(stamp);
        added.add(stamp);
    }

    /** 최근에 받은 순. */
    public List<RevisitStamp> newestFirst() {
        return stamps.stream().sorted(Comparator.comparing(RevisitStamp::stampedAt).reversed()
            .thenComparing(stamp -> stamp.region().value())).toList();
    }

    /** 복원 이후 새로 받은 도장(저장소가 insert). */
    public List<RevisitStamp> added() {
        return List.copyOf(added);
    }
}
