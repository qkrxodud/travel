package com.kobi.territory.social.domain.ranking;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.Rarity;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 일급 컬렉션: 공유 지도 하나의 멤버별 집계(지도 안 랭킹, §5). 지금 멤버만 줄을 갖고(탈퇴 유예 중인 사람 제외 — 방문도 숨겨져 있다),
 * 이의(disputed) 표시된 방문은 영토·선점·전설 어디에도 세지 않고, 그 방문이 선점이었으면 다음 이의 아닌 방문이 선점이다. 순서: 영토 수 → 선점 수 → 전설 수(모두 내림차순), 같으면 같은
 * 순위(경쟁 순위 1·2·2·4).
 */
public final class MapLeaderboard {

    private static final Comparator<Tally> ORDER = Comparator.comparingInt(Tally::territories).reversed()
        .thenComparing(Comparator.comparingInt(Tally::claims).reversed())
        .thenComparing(Comparator.comparingInt(Tally::legends).reversed())
        .thenComparing(tally -> tally.explorerId().value());

    private final List<MapStanding> standings;
    private final int disputedExcluded;

    private MapLeaderboard(List<MapStanding> standings, int disputedExcluded) {
        this.standings = List.copyOf(standings);
        this.disputedExcluded = disputedExcluded;
    }

    public static MapLeaderboard of(Collection<MapVisitFact> visits, Collection<ExplorerId> members) {
        List<MapVisitFact> counted = visits.stream().filter(visit -> !visit.disputed()).toList();
        // 지역마다 이의 아닌 방문 중 선점 순서가 가장 앞선 것이 선점(선점 방문이 이의 표시되면 다음 방문으로 넘어간다 — QA Q1)
        Set<MapVisitFact> claims = counted.stream()
            .collect(Collectors.groupingBy(MapVisitFact::regionCode,
                Collectors.minBy(Comparator.comparingInt(MapVisitFact::claimOrder))))
            .values().stream().flatMap(Optional::stream).collect(Collectors.toSet());
        List<Tally> tallies = members.stream().distinct().map(member -> Tally.of(member, counted, claims)).sorted(ORDER).toList();
        List<MapStanding> standings = new ArrayList<>();
        for (int i = 0; i < tallies.size(); i++) {
            Tally tally = tallies.get(i);
            boolean tiedWithPrevious = i > 0 && tally.sameScoreAs(tallies.get(i - 1));
            int rank = tiedWithPrevious ? standings.get(i - 1).rank() : i + 1;
            standings.add(new MapStanding(tally.explorerId(), tally.territories(), tally.claims(), tally.legends(), rank));
        }
        return new MapLeaderboard(standings, visits.size() - counted.size());
    }

    /** 순위 순. */
    public List<MapStanding> standings() {
        return standings;
    }

    /** 이의 표시로 집계에서 뺀 방문 수(화면 안내용). */
    public int disputedExcluded() {
        return disputedExcluded;
    }

    private record Tally(ExplorerId explorerId, int territories, int claims, int legends) {
        static Tally of(ExplorerId member, List<MapVisitFact> counted, Set<MapVisitFact> claims) {
            List<MapVisitFact> mine = counted.stream().filter(visit -> visit.explorerId().equals(member)).toList();
            return new Tally(member, (int) mine.stream().map(MapVisitFact::regionCode).distinct().count(),
                (int) mine.stream().filter(claims::contains).count(),
                (int) mine.stream().filter(visit -> visit.rarity() == Rarity.LEGEND).count());
        }

        boolean sameScoreAs(Tally other) {
            return territories == other.territories && claims == other.claims && legends == other.legends;
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MapLeaderboard board && standings.equals(board.standings) && disputedExcluded == board.disputedExcluded;
    }

    @Override
    public int hashCode() {
        return Objects.hash(standings, disputedExcluded);
    }
}
