package com.kobi.territory.progression.infra.entity;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.progression.domain.collectionbook.CollectionBook;
import com.kobi.territory.progression.domain.collectionbook.ThemeProgress;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * set_progress — 도감(CollectionBook, 지도 단위)의 테마 하나(ThemeProgress). 테이블·컬럼 이름(set_id)은 외부 계약이라 유지.
 * collected_codes·completed_member_ids(완성 시점 멤버 = 보상 수령자, V3 — 결정 1)는 쉼표 구분.
 */
@Entity
@Table(name = "set_progress")
@IdClass(ThemeProgressJpaEntity.Key.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ThemeProgressJpaEntity {

    @Id
    @Column(name = "map_id", length = 36)
    private String mapId;

    @Id
    @Column(name = "set_id", length = 20)
    private String themeId;

    @Column(name = "collected_codes", nullable = false, length = 1000)
    private String collectedCodes;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completed_member_ids", length = 400)
    private String completedMemberIds;

    /** 낙관적 락 — 재계산과 도감 이벤트 처리의 동시 갱신을 충돌로 드러낸다(구조 QA S2-1). */
    @Version
    private Long version;

    public static ThemeProgressJpaEntity from(String mapId, ThemeProgress themeProgress) {
        ThemeProgressJpaEntity entity = new ThemeProgressJpaEntity();
        entity.mapId = mapId;
        entity.themeId = themeProgress.themeId();
        entity.apply(themeProgress);
        return entity;
    }

    public void apply(ThemeProgress themeProgress) {
        this.collectedCodes = CsvColumn.write(themeProgress.collected().stream().map(RegionCode::value).toList());
        this.completedAt = themeProgress.completedAt();
        this.completedMemberIds = CsvColumn.write(themeProgress.completedMembers().stream().map(ExplorerId::value).toList());
    }

    public String themeId() {
        return themeId;
    }

    public ThemeProgress toDomain() {
        return ThemeProgress.restore(themeId,
            CsvColumn.read(collectedCodes).stream().map(RegionCode::of).collect(Collectors.toSet()), completedAt,
            CsvColumn.read(completedMemberIds).stream().map(ExplorerId::of).collect(Collectors.toSet()));
    }

    /** 한 지도의 테마 행들(+ 9단계 계절 회차 행들)로 도감 애그리거트를 복원한다. */
    public static CollectionBook toCollectionBook(String mapId, List<ThemeProgressJpaEntity> rows,
                                                  List<SeasonProgressJpaEntity> seasonRows) {
        return CollectionBook.restore(mapId, rows.stream().map(ThemeProgressJpaEntity::toDomain).toList(),
            seasonRows.stream().map(SeasonProgressJpaEntity::toDomain).toList());
    }

    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private String mapId;
        private String themeId;
    }
}
