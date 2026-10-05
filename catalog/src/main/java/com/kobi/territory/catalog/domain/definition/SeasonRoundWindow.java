package com.kobi.territory.catalog.domain.definition;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 계절 회차 하나의 기간(13s단계 — 회차별 지역 목록을 모으고 확정하는 기준). 기간은 [startsAt, endsAt) — firstDay 0시부터 lastDay 다음 날 0시
 * 전까지(서비스 시간대). 고정·수집 시점·찾는 범위를 계산하는 행동이 있는 값이라 class(equals/hashCode 직접 구현 — class vs record 기준).
 */
public final class SeasonRoundWindow {

    private final String roundId;
    private final String seasonId;
    private final int year;
    private final LocalDate firstDay;
    private final LocalDate lastDay;
    private final Instant startsAt;
    private final Instant endsAt;

    /**
     * @param roundId  {계절 id}-{연도}
     * @param firstDay 첫날(포함)
     * @param lastDay  마지막 날(포함)
     */
    public SeasonRoundWindow(String roundId, String seasonId, int year, LocalDate firstDay, LocalDate lastDay, Instant startsAt,
                             Instant endsAt) {
        this.roundId = Objects.requireNonNull(roundId, "roundId");
        this.seasonId = Objects.requireNonNull(seasonId, "seasonId");
        this.year = year;
        this.firstDay = Objects.requireNonNull(firstDay, "firstDay");
        this.lastDay = Objects.requireNonNull(lastDay, "lastDay");
        this.startsAt = Objects.requireNonNull(startsAt, "startsAt");
        this.endsAt = Objects.requireNonNull(endsAt, "endsAt");
        if (lastDay.isBefore(firstDay)) throw new IllegalArgumentException("회차 마지막 날이 첫날보다 앞: " + roundId);
    }

    public String roundId() {
        return roundId;
    }

    public String seasonId() {
        return seasonId;
    }

    public int year() {
        return year;
    }

    public LocalDate firstDay() {
        return firstDay;
    }

    public LocalDate lastDay() {
        return lastDay;
    }

    public Instant startsAt() {
        return startsAt;
    }

    public Instant endsAt() {
        return endsAt;
    }

    /** 이 시각에 회차가 이미 열렸는지(열린 뒤에는 지역 목록이 고정된다 — 진행 중 바뀌면 진행도가 깨진다). */
    public boolean startedBy(Instant at) {
        return !at.isBefore(startsAt);
    }

    /** 이 시각에 열려 있는지. */
    public boolean openAt(Instant at) {
        return startedBy(at) && at.isBefore(endsAt);
    }

    /** 자동 수집을 시작하는 시각(시작 leadDays 일 전). */
    public Instant collectionOpensAt(int leadDays) {
        return startsAt.minus(Duration.ofDays(leadDays));
    }

    /** 축제를 찾는 날짜 범위의 첫날 — 회차 첫날보다 여유 일수만큼 앞. */
    public LocalDate searchFrom(int marginDays) {
        return firstDay.minusDays(marginDays);
    }

    /** 축제를 찾는 날짜 범위의 마지막 날 — 회차 마지막 날보다 여유 일수만큼 뒤. */
    public LocalDate searchUntil(int marginDays) {
        return lastDay.plusDays(marginDays);
    }

    /** 기간 [from, until](양 끝 포함)이 여유를 둔 찾는 범위와 겹치는지. */
    public boolean overlapsSearchRange(LocalDate from, LocalDate until, int marginDays) {
        return !until.isBefore(searchFrom(marginDays)) && !from.isAfter(searchUntil(marginDays));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SeasonRoundWindow that && year == that.year && roundId.equals(that.roundId) && seasonId.equals(that.seasonId)
            && firstDay.equals(that.firstDay) && lastDay.equals(that.lastDay) && startsAt.equals(that.startsAt) && endsAt.equals(that.endsAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roundId, seasonId, year, firstDay, lastDay, startsAt, endsAt);
    }

    @Override
    public String toString() {
        return "SeasonRoundWindow[" + roundId + " " + startsAt + " ~ " + endsAt + "]";
    }
}
