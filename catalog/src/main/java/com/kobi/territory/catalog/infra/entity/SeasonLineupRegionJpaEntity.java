package com.kobi.territory.catalog.infra.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kobi.territory.catalog.domain.lineup.LineupEvidence;
import com.kobi.territory.catalog.domain.lineup.LineupProvenance;
import com.kobi.territory.catalog.domain.lineup.LineupRegion;
import com.kobi.territory.catalog.domain.lineup.LineupSnapshot;
import com.kobi.territory.common.model.RegionCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * season_lineup_region — 회차 지역 목록의 한 줄(13s단계 V11). stage = CANDIDATE(후보) | CONFIRMED(확정본), position = 순위(0부터).
 * evidence = 근거 JSON 배열 [{kind, contentId, title, startDate, endDate, fetchedAt}](kind FESTIVAL | ATTRACTION — 관광지는 기간 없음, kind 가
 * 없는 기록은 축제. TourAPI 지역만, AI 추정은 "[]").
 */
@Entity
@Table(name = "season_lineup_region")
@IdClass(SeasonLineupRegionJpaEntity.Key.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeasonLineupRegionJpaEntity {

    public static final String CANDIDATE = "CANDIDATE";
    public static final String CONFIRMED = "CONFIRMED";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<Map<String, String>>> EVIDENCE = new TypeReference<>() {};

    @Id
    @Column(name = "round_id", length = 20)
    private String roundId;

    @Id
    @Column(name = "stage", length = 10)
    private String stage;

    @Id
    @Column(name = "position_no")
    private int position;

    @Column(name = "region_code", nullable = false, length = 10)
    private String regionCode;

    @Column(name = "provenance", nullable = false, length = 16)
    private String provenance;

    @Column(name = "evidence", nullable = false, length = 4000)
    private String evidence;

    /** 한 묶음(후보 또는 확정본)의 지역 행들. */
    public static List<SeasonLineupRegionJpaEntity> rowsOf(String roundId, String stage, LineupSnapshot snapshot) {
        if (snapshot == null) return List.of();
        List<LineupRegion> regions = snapshot.regions().stream().toList();
        List<SeasonLineupRegionJpaEntity> rows = new ArrayList<>();
        for (int i = 0; i < regions.size(); i++) {
            LineupRegion region = regions.get(i);
            SeasonLineupRegionJpaEntity row = new SeasonLineupRegionJpaEntity();
            row.roundId = roundId;
            row.stage = stage;
            row.position = i;
            row.regionCode = region.code().value();
            row.provenance = region.provenance().code();
            row.evidence = write(region.evidence());
            rows.add(row);
        }
        return rows;
    }

    public String roundId() {
        return roundId;
    }

    public String stage() {
        return stage;
    }

    public int position() {
        return position;
    }

    public LineupRegion toDomain() {
        return new LineupRegion(RegionCode.of(regionCode), LineupProvenance.ofCode(provenance), read(evidence));
    }

    private static String write(List<LineupEvidence> evidence) {
        List<Map<String, String>> items = evidence.stream().map(item -> {
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("kind", item.kind().name());
            fields.put("contentId", item.contentId());
            fields.put("title", item.title());
            if (item.startDate() != null) fields.put("startDate", item.startDate().toString());
            if (item.endDate() != null) fields.put("endDate", item.endDate().toString());
            fields.put("fetchedAt", item.fetchedAt().toString());
            return fields;
        }).toList();
        try {
            return JSON.writeValueAsString(items);
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException("근거 축제를 저장할 수 없다", impossible);
        }
    }

    private static List<LineupEvidence> read(String json) {
        try {
            return JSON.readValue(json, EVIDENCE).stream().map(fields -> new LineupEvidence(
                LineupEvidence.EvidenceKind.valueOf(fields.getOrDefault("kind", "FESTIVAL")), fields.get("contentId"), fields.get("title"),
                date(fields.get("startDate")), date(fields.get("endDate")), Instant.parse(fields.get("fetchedAt")))).toList();
        } catch (JsonProcessingException broken) {
            throw new IllegalStateException("근거 축제 기록을 읽을 수 없다", broken);
        }
    }

    private static LocalDate date(String text) {
        return text == null ? null : LocalDate.parse(text);
    }

    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private String roundId;
        private String stage;
        private int position;
    }
}
