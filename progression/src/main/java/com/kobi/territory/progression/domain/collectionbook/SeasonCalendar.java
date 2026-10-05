package com.kobi.territory.progression.domain.collectionbook;

import com.kobi.territory.common.model.RegionCode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 일급 컬렉션: 계절 한정 테마 달력(정책 VO) — 계절 정의와 서비스 시간대로 어느 시각에 열린 회차를 낸다. 회차는 해마다 새로 열리므로 목록을
 * 미리 만들지 않고 시각에서 계산한다.
 * <p>
 * 재계산용 사본({@link #excludingEndedBy})은 그 시각에 이미 닫힌 회차를 내지 않는다 — 닫힌 회차의 기록(진행·완성)은 확정이라 재계산이
 * 다시 만들지 않는다.
 */
public final class SeasonCalendar {

    private final List<Season> seasons;
    private final ZoneId zone;
    private final Instant frozenBefore;
    private final RoundLineups lineups;

    private SeasonCalendar(List<Season> seasons, ZoneId zone, Instant frozenBefore, RoundLineups lineups) {
        this.seasons = List.copyOf(seasons);
        this.zone = Objects.requireNonNull(zone, "zone");
        this.frozenBefore = frozenBefore;
        this.lineups = Objects.requireNonNull(lineups, "lineups");
    }

    /** 회차 지역은 모두 계절 정의의 기본 목록. */
    public static SeasonCalendar of(List<Season> seasons, ZoneId zone) {
        return of(seasons, zone, RoundLineups.defaults());
    }

    /** 회차 지역은 회차별 확정 목록(13s단계), 없으면 기본 목록. */
    public static SeasonCalendar of(List<Season> seasons, ZoneId zone, RoundLineups lineups) {
        return new SeasonCalendar(seasons, zone, null, lineups);
    }

    /** 계절이 없는 달력(9단계 이전 범위). */
    public static SeasonCalendar none(ZoneId zone) {
        return of(List.of(), zone);
    }

    /** 재계산용: at 에 이미 닫힌 회차는 내지 않는 사본. */
    public SeasonCalendar excludingEndedBy(Instant at) {
        return new SeasonCalendar(seasons, zone, at, lineups);
    }

    /** 이 시각에 열려 있는 회차들. */
    public List<SeasonRound> roundsOpenAt(Instant at) {
        int year = at.atZone(zone).getYear();
        return seasons.stream().map(season -> season.roundOf(year, zone, lineups))
            .filter(round -> round.openAt(at) && (frozenBefore == null || !round.endedBy(frozenBefore))).toList();
    }

    /** 이 시각에 열려 있고 이 지역을 포함하는 회차들. */
    public List<SeasonRound> roundsCovering(RegionCode region, Instant at) {
        return roundsOpenAt(at).stream().filter(round -> round.includes(region)).toList();
    }

    /** 회차 id({계절}-{연도})의 회차. 모르는 계절이나 형식이면 빈 값. */
    public Optional<SeasonRound> round(String roundId) {
        int dash = roundId.lastIndexOf('-');
        if (dash <= 0) return Optional.empty();
        String seasonId = roundId.substring(0, dash);
        try {
            int year = Integer.parseInt(roundId.substring(dash + 1));
            return seasons.stream().filter(season -> season.id().equals(seasonId)).findFirst()
                .map(season -> season.roundOf(year, zone, lineups));
        } catch (NumberFormatException notRound) {
            return Optional.empty();
        }
    }

    /** 이 시각 뒤에 가장 먼저 열리는 회차(지금 열린 회차는 제외). 계절이 없으면 빈 값. */
    public Optional<SeasonRound> nextRoundAfter(Instant at) {
        int year = at.atZone(zone).getYear();
        return seasons.stream().flatMap(season -> List.of(season.roundOf(year, zone, lineups), season.roundOf(year + 1, zone, lineups)).stream())
            .filter(round -> round.startsAt().isAfter(at)).min(Comparator.comparing(SeasonRound::startsAt));
    }

    public ZoneId zone() {
        return zone;
    }
}
