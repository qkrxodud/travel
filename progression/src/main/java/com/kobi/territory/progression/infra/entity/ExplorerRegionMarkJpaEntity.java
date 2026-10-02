package com.kobi.territory.progression.infra.entity;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.progression.domain.progress.ExploredRegion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * explorer_region_mark — 탐험가 단위 지역(ExploredRegion)의 지도별 마지막 방문 회차(결정 6). 양수 g = g회차 체크인,
 * 음수 −g = g회차 취소. 지도 수만큼 행이 늘 뿐 한 행의 크기는 고정이다(QA P3-7 — 문자열 컬럼 넘침 방지).
 */
@Entity
@Table(name = "explorer_region_mark")
@IdClass(ExplorerRegionMarkJpaEntity.Key.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExplorerRegionMarkJpaEntity {

    @Id
    @Column(name = "explorer_id", length = 36)
    private String explorerId;

    @Id
    @Column(name = "region_code", length = 10)
    private String regionCode;

    @Id
    @Column(name = "map_id", length = 36)
    private String mapId;

    @Column(name = "mark", nullable = false)
    private int mark;

    public static List<ExplorerRegionMarkJpaEntity> from(ExplorerId explorer, ExploredRegion region) {
        return region.marks().entrySet().stream().map(entry -> {
            ExplorerRegionMarkJpaEntity entity = new ExplorerRegionMarkJpaEntity();
            entity.explorerId = explorer.value();
            entity.regionCode = region.code().value();
            entity.mapId = entry.getKey();
            entity.mark = entry.getValue();
            return entity;
        }).toList();
    }

    public void apply(int next) {
        this.mark = next;
    }

    public String regionCode() {
        return regionCode;
    }

    public Key key() {
        return new Key(explorerId, regionCode, mapId);
    }

    public int mark() {
        return mark;
    }

    /** 지역 코드 → (지도 → 회차). */
    static Map<String, Map<String, Integer>> byRegion(List<ExplorerRegionMarkJpaEntity> rows) {
        return rows.stream().collect(Collectors.groupingBy(row -> row.regionCode,
            Collectors.toMap(row -> row.mapId, row -> row.mark)));
    }

    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private String explorerId;
        private String regionCode;
        private String mapId;
    }
}
