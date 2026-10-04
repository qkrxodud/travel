package com.kobi.territory.notification.domain.campaign;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** 일급 컬렉션: 계절 한정 테마들의 시작일. */
public final class SeasonStarts {

    private final List<SeasonStart> seasons;

    private SeasonStarts(List<SeasonStart> seasons) {
        this.seasons = List.copyOf(seasons);
    }

    public static SeasonStarts of(List<SeasonStart> seasons) {
        return new SeasonStarts(seasons);
    }

    /** 그 날짜에 시작하는 계절들. */
    public List<SeasonStart> startingOn(LocalDate day) {
        return seasons.stream().filter(season -> season.startsOn(day)).toList();
    }

    /** (local 즉시 발송) 그 날짜에 열려 있는 계절 — 없으면 그 뒤 가장 먼저 시작하는 계절과 그 시작 연도. */
    public Optional<SeasonRoundStart> openOrNext(LocalDate day) {
        Optional<SeasonRoundStart> open = seasons.stream().filter(season -> season.openYear(day) > 0)
            .map(season -> new SeasonRoundStart(season, season.openYear(day))).findFirst();
        if (open.isPresent()) return open;
        return seasons.stream().map(season -> {
            LocalDate startThisYear = season.start().atYear(day.getYear());
            int year = startThisYear.isAfter(day) ? day.getYear() : day.getYear() + 1;
            return new SeasonRoundStart(season, year);
        }).min(Comparator.comparing(round -> round.season().start().atYear(round.year())));
    }

    /** 계절 하나의 한 회차. */
    public record SeasonRoundStart(SeasonStart season, int year) {
        public String roundId() {
            return season.roundId(year);
        }
    }
}
