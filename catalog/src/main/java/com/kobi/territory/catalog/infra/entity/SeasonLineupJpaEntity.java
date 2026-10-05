package com.kobi.territory.catalog.infra.entity;

import com.kobi.territory.catalog.domain.definition.SeasonRoundWindow;
import com.kobi.territory.catalog.domain.lineup.CollectionAttempt;
import com.kobi.territory.catalog.domain.lineup.ConfirmedBy;
import com.kobi.territory.catalog.domain.lineup.LineupRegion;
import com.kobi.territory.catalog.domain.lineup.LineupRegions;
import com.kobi.territory.catalog.domain.lineup.LineupSnapshot;
import com.kobi.territory.catalog.domain.lineup.SeasonLineup;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * season_lineup — 계절 회차 하나의 지역 목록 기록(13s단계 V11, SeasonLineup 루트). 지역 행은 season_lineup_region(stage = CANDIDATE |
 * CONFIRMED). 경고 문구는 줄바꿈으로 이어 저장한다. 회차가 지나도 지우지 않는다(그 회차가 어떤 근거로 정해졌는지의 기록).
 */
@Entity
@Table(name = "season_lineup")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeasonLineupJpaEntity {

    private static final String LINE = "\n";

    @Id
    @Column(name = "round_id", length = 20)
    private String roundId;

    @Column(name = "season_id", nullable = false, length = 12)
    private String seasonId;

    @Column(name = "round_year", nullable = false)
    private int roundYear;

    @Column(name = "first_day", nullable = false)
    private LocalDate firstDay;

    @Column(name = "last_day", nullable = false)
    private LocalDate lastDay;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "candidate_collected_at")
    private Instant candidateCollectedAt;

    @Column(name = "candidate_warnings", length = 2000)
    private String candidateWarnings;

    @Column(name = "confirmed_collected_at")
    private Instant confirmedCollectedAt;

    @Column(name = "confirmed_warnings", length = 2000)
    private String confirmedWarnings;

    @Column(name = "confirmed_by", length = 10)
    private String confirmedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "attempt_at")
    private Instant attemptAt;

    @Column(name = "attempt_outcome", length = 20)
    private String attemptOutcome;

    @Column(name = "attempt_warnings", length = 2000)
    private String attemptWarnings;

    /** 낙관적 잠금 — 관리자 갱신·확정과 자동 수집이 겹치면 늦은 쪽이 충돌로 드러난다. */
    @Version
    private Long version;

    public static SeasonLineupJpaEntity from(SeasonLineup lineup) {
        SeasonLineupJpaEntity entity = new SeasonLineupJpaEntity();
        SeasonRoundWindow window = lineup.window();
        entity.roundId = window.roundId();
        entity.seasonId = window.seasonId();
        entity.roundYear = window.year();
        entity.firstDay = window.firstDay();
        entity.lastDay = window.lastDay();
        entity.startsAt = window.startsAt();
        entity.endsAt = window.endsAt();
        entity.apply(lineup);
        return entity;
    }

    public void apply(SeasonLineup lineup) {
        LineupSnapshot candidate = lineup.candidate();
        candidateCollectedAt = candidate == null ? null : candidate.collectedAt();
        candidateWarnings = candidate == null ? null : lines(candidate.warnings());
        LineupSnapshot confirmed = lineup.confirmed();
        confirmedCollectedAt = confirmed == null ? null : confirmed.collectedAt();
        confirmedWarnings = confirmed == null ? null : lines(confirmed.warnings());
        confirmedBy = lineup.confirmedBy() == null ? null : lineup.confirmedBy().name();
        confirmedAt = lineup.confirmedAt();
        CollectionAttempt attempt = lineup.lastAttempt();
        attemptAt = attempt == null ? null : attempt.at();
        attemptOutcome = attempt == null ? null : attempt.outcome().name();
        attemptWarnings = attempt == null ? null : lines(attempt.warnings());
    }

    public String roundId() {
        return roundId;
    }

    public long version() {
        return version == null ? 0 : version;
    }

    /** @param regionRows 이 회차의 지역 행(후보·확정 모두) */
    public SeasonLineup toDomain(List<SeasonLineupRegionJpaEntity> regionRows) {
        SeasonRoundWindow window = new SeasonRoundWindow(roundId, seasonId, roundYear, firstDay, lastDay, startsAt, endsAt);
        LineupSnapshot candidate = candidateCollectedAt == null ? null
            : new LineupSnapshot(regions(regionRows, SeasonLineupRegionJpaEntity.CANDIDATE), candidateCollectedAt, split(candidateWarnings));
        LineupSnapshot confirmed = confirmedCollectedAt == null ? null
            : new LineupSnapshot(regions(regionRows, SeasonLineupRegionJpaEntity.CONFIRMED), confirmedCollectedAt, split(confirmedWarnings));
        CollectionAttempt attempt = attemptAt == null ? null
            : new CollectionAttempt(attemptAt, CollectionAttempt.Outcome.valueOf(attemptOutcome), split(attemptWarnings));
        return SeasonLineup.restore(window, candidate, confirmed, confirmedBy == null ? null : ConfirmedBy.valueOf(confirmedBy),
            confirmedAt, attempt, version());
    }

    private static LineupRegions regions(List<SeasonLineupRegionJpaEntity> rows, String stage) {
        List<LineupRegion> regions = rows.stream().filter(row -> row.stage().equals(stage))
            .sorted(Comparator.comparingInt(SeasonLineupRegionJpaEntity::position)).map(SeasonLineupRegionJpaEntity::toDomain).toList();
        return LineupRegions.of(regions);
    }

    private static String lines(List<String> warnings) {
        return warnings.isEmpty() ? null : String.join(LINE, warnings);
    }

    private static List<String> split(String text) {
        return text == null || text.isEmpty() ? List.of() : Arrays.asList(text.split(LINE));
    }
}
